package com.xiaosu.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;

import com.xiaosu.domain.DocumentRecord;
import com.xiaosu.domain.DocumentVersionRecord;
import com.xiaosu.domain.DocumentVersionStatus;
import com.xiaosu.knowledge.parser.DocumentParser;
import com.xiaosu.knowledge.parser.MarkdownDocumentParser;
import com.xiaosu.knowledge.parser.ParsedBlock;
import com.xiaosu.mapper.DatabaseCleanupMapper;
import com.xiaosu.service.DocumentChunkService;
import com.xiaosu.service.DocumentService;
import com.xiaosu.service.DocumentVersionService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:tc:mysql:8.4:///xiaosu_upload_test",
                "spring.datasource.username=test",
                "spring.datasource.password=test",
                "spring.datasource.hikari.connection-timeout=60000",
                "xiaosu.storage.upload-directory=target/test-uploads",
                "xiaosu.storage.max-file-size=10MB",
                "spring.servlet.multipart.max-file-size=10MB",
                "spring.servlet.multipart.max-request-size=11MB"
        })
@ActiveProfiles("fake-embedding")
@Import(DocumentApiIntegrationTest.ParserFailureConfiguration.class)
class DocumentApiIntegrationTest {

    private static final Path TEST_UPLOAD_DIRECTORY = Path.of("target/test-uploads");
    private static final TypeReference<List<DocumentUploadService.DocumentSummary>> DOCUMENT_LIST =
            new TypeReference<>() {
            };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanupMapper databaseCleanupMapper;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentVersionService versionService;

    @Autowired
    private DocumentChunkService chunkService;

    @Autowired
    private FileStore fileStore;

    @Autowired
    private PendingDocumentRecovery pendingDocumentRecovery;

    @Autowired
    private FailOnceMarkdownParser failOnceMarkdownParser;

    @BeforeEach
    void resetState() throws IOException {
        databaseCleanupMapper.deleteChunks();
        databaseCleanupMapper.clearActiveVersions();
        databaseCleanupMapper.deleteVersions();
        databaseCleanupMapper.deleteDocuments();
        Files.createDirectories(TEST_UPLOAD_DIRECTORY);
        try (Stream<Path> paths = Files.list(TEST_UPLOAD_DIRECTORY)) {
            for (Path path : paths.toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void markdownUploadMovesFromPendingToIndexedAndUsesRandomPhysicalFile() throws Exception {
        byte[] content = "# 员工手册\n\n年假应提前申请。\n".getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = upload("C:\\fakepath\\员工手册.md", "text/markdown", content);

        assertEquals(202, response.statusCode());
        DocumentUploadService.UploadResult result = objectMapper.readValue(
                response.body(), DocumentUploadService.UploadResult.class);
        assertEquals("员工手册.md", result.fileName());
        assertEquals(1, result.versionNo());
        assertEquals(DocumentVersionStatus.PENDING, result.status());
        assertEquals(sha256(content), result.sha256());
        assertEquals(content.length, result.size());

        var document = documentService.findById(result.documentId()).orElseThrow();
        DocumentVersionRecord version = awaitStatus(result.versionId(), DocumentVersionStatus.INDEXED);
        assertEquals("员工手册.md", document.canonicalName());
        assertEquals(DocumentVersionStatus.INDEXED, version.status());
        assertEquals("text/markdown", version.mimeType());
        assertEquals(result.sha256(), version.sha256());
        assertTrue(version.storagePath().matches("[0-9a-f-]{36}"));
        assertFalse(Path.of(version.storagePath()).isAbsolute());
        assertFalse(version.storagePath().contains("员工手册"));
        assertTrue(fileStore.exists(version.storagePath()));
        assertFalse(response.body().contains(TEST_UPLOAD_DIRECTORY.toAbsolutePath().toString()));
        assertFalse(response.body().contains(version.storagePath()));
        assertFalse(chunkService.findByVersionId(version.id()).isEmpty());
        assertEquals(version.id(), documentService.findById(result.documentId()).orElseThrow().activeVersionId());
    }

    @Test
    void listReturnsIndexedMetadataWithoutStoragePath() throws Exception {
        HttpResponse<String> uploadResponse = upload(
                "policy.txt", "text/plain", "差旅报销制度".getBytes(StandardCharsets.UTF_8));
        assertEquals(202, uploadResponse.statusCode());
        DocumentUploadService.UploadResult uploadResult = objectMapper.readValue(
                uploadResponse.body(), DocumentUploadService.UploadResult.class);
        awaitStatus(uploadResult.versionId(), DocumentVersionStatus.INDEXED);

        HttpResponse<String> response = get("/api/documents");

        assertEquals(200, response.statusCode());
        List<DocumentUploadService.DocumentSummary> documents = objectMapper.readValue(response.body(), DOCUMENT_LIST);
        assertEquals(1, documents.size());
        assertEquals("policy.txt", documents.getFirst().fileName());
        assertEquals(DocumentVersionStatus.INDEXED, documents.getFirst().status());
        assertNotNull(documents.getFirst().latestVersionId());
        assertFalse(response.body().contains("storagePath"));
        assertFalse(response.body().contains(TEST_UPLOAD_DIRECTORY.toAbsolutePath().toString()));
    }

    @Test
    void markdownTextAndPdfAreAccepted() throws Exception {
        HttpResponse<String> markdown = upload(
                "guide.md", "application/octet-stream", "# Guide".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> text = upload("faq.txt", "text/plain", "FAQ".getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> pdf = upload(
                "rules.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        assertEquals(202, markdown.statusCode());
        assertEquals(202, text.statusCode());
        assertEquals(202, pdf.statusCode());
        awaitTerminal(objectMapper.readValue(markdown.body(), DocumentUploadService.UploadResult.class).versionId());
        awaitTerminal(objectMapper.readValue(text.body(), DocumentUploadService.UploadResult.class).versionId());
        awaitTerminal(objectMapper.readValue(pdf.body(), DocumentUploadService.UploadResult.class).versionId());
        assertEquals(3L, count("documents"));
        assertEquals(3L, count("document_versions"));
    }

    @Test
    void unsupportedPomReturns415WithoutCreatingDataOrFile() throws Exception {
        HttpResponse<String> response = upload("pom.xml", "application/xml", "<project/>".getBytes(StandardCharsets.UTF_8));

        assertEquals(415, response.statusCode());
        DocumentApiExceptionHandler.ApiError error = objectMapper.readValue(
                response.body(), DocumentApiExceptionHandler.ApiError.class);
        assertEquals("UNSUPPORTED_FILE_TYPE", error.code());
        assertEquals(0L, count("documents"));
        assertEquals(0L, storedFileCount());
    }

    @Test
    void fileOverTenMebibytesReturns413WithoutCreatingDataOrFile() throws Exception {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = '#';

        HttpResponse<String> response = upload("oversized.md", "text/markdown", oversized);

        assertEquals(413, response.statusCode());
        DocumentApiExceptionHandler.ApiError error = objectMapper.readValue(
                response.body(), DocumentApiExceptionHandler.ApiError.class);
        assertEquals("FILE_TOO_LARGE", error.code());
        assertEquals(0L, count("documents"));
        assertEquals(0L, storedFileCount());
    }

    @Test
    void duplicateCanonicalNameReturns409AndDoesNotLeaveSecondFile() throws Exception {
        HttpResponse<String> first = upload("same.md", "text/markdown", "first".getBytes(StandardCharsets.UTF_8));
        assertEquals(202, first.statusCode());
        awaitStatus(
                objectMapper.readValue(first.body(), DocumentUploadService.UploadResult.class).versionId(),
                DocumentVersionStatus.INDEXED);

        HttpResponse<String> response = upload("same.md", "text/markdown", "second".getBytes(StandardCharsets.UTF_8));

        assertEquals(409, response.statusCode());
        DocumentApiExceptionHandler.ApiError error = objectMapper.readValue(
                response.body(), DocumentApiExceptionHandler.ApiError.class);
        assertEquals("DOCUMENT_ALREADY_EXISTS", error.code());
        assertEquals(1L, count("documents"));
        assertEquals(1L, count("document_versions"));
        assertEquals(1L, storedFileCount());
    }

    @Test
    void parserFailureCreatesNoChunksAndRetryIndexesTheSameVersion() throws Exception {
        failOnceMarkdownParser.failNext();
        HttpResponse<String> uploadResponse = upload(
                "retry.md", "text/markdown", "# Retry\n\nThis succeeds on retry.".getBytes(StandardCharsets.UTF_8));
        DocumentUploadService.UploadResult uploadResult = objectMapper.readValue(
                uploadResponse.body(), DocumentUploadService.UploadResult.class);

        DocumentVersionRecord failed = awaitStatus(uploadResult.versionId(), DocumentVersionStatus.FAILED);
        assertEquals("文档索引失败，请稍后重试", failed.errorMessage());
        assertTrue(chunkService.findByVersionId(failed.id()).isEmpty());
        assertFalse(failed.errorMessage().contains("IllegalStateException"));

        HttpResponse<String> retryResponse = post("/api/documents/" + uploadResult.documentId() + "/retry");
        assertEquals(202, retryResponse.statusCode());
        DocumentUploadService.RetryResult retryResult = objectMapper.readValue(
                retryResponse.body(), DocumentUploadService.RetryResult.class);
        assertEquals(DocumentVersionStatus.PENDING, retryResult.status());

        DocumentVersionRecord indexed = awaitStatus(uploadResult.versionId(), DocumentVersionStatus.INDEXED);
        assertNull(indexed.errorMessage());
        assertFalse(chunkService.findByVersionId(indexed.id()).isEmpty());
    }

    @Test
    void recoveryResubmitsAnExistingPendingVersion() throws Exception {
        byte[] content = "# Recovered\n\nPending work survives restart.".getBytes(StandardCharsets.UTF_8);
        FileStore.StoredFile storedFile = fileStore.store(new java.io.ByteArrayInputStream(content));
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.now();
        documentService.insert(new DocumentRecord(documentId, "recovered.md", null, null, now, now));
        versionService.insert(new DocumentVersionRecord(
                versionId,
                documentId,
                1,
                storedFile.sha256(),
                "text/markdown",
                storedFile.storageKey(),
                DocumentVersionStatus.PENDING,
                null,
                now));

        pendingDocumentRecovery.recoverPendingVersions();

        awaitStatus(versionId, DocumentVersionStatus.INDEXED);
        assertEquals(versionId, documentService.findById(documentId).orElseThrow().activeVersionId());
        assertFalse(chunkService.findByVersionId(versionId).isEmpty());
    }

    private HttpResponse<String> upload(String fileName, String contentType, byte[] content) throws Exception {
        String boundary = "xiaosu-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/documents"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private DocumentVersionRecord awaitStatus(UUID versionId, DocumentVersionStatus expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            DocumentVersionRecord version = versionService.findById(versionId).orElseThrow();
            if (version.status() == expected) {
                return version;
            }
            Thread.sleep(50);
        }
        DocumentVersionRecord version = versionService.findById(versionId).orElseThrow();
        throw new AssertionError("Expected " + expected + " but was " + version.status());
    }

    private DocumentVersionRecord awaitTerminal(UUID versionId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            DocumentVersionRecord version = versionService.findById(versionId).orElseThrow();
            if (version.status() != DocumentVersionStatus.PENDING) {
                return version;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document version remained pending: " + versionId);
    }

    private long count(String table) {
        if (!table.equals("documents") && !table.equals("document_versions")) {
            throw new IllegalArgumentException("Unexpected table");
        }
        return table.equals("documents")
                ? databaseCleanupMapper.countDocuments()
                : databaseCleanupMapper.countVersions();
    }

    private long storedFileCount() throws IOException {
        try (Stream<Path> paths = Files.list(TEST_UPLOAD_DIRECTORY)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    @TestConfiguration
    static class ParserFailureConfiguration {

        @Bean
        FailOnceMarkdownParser failOnceMarkdownParser() {
            return new FailOnceMarkdownParser();
        }
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    static final class FailOnceMarkdownParser implements DocumentParser {

        private final MarkdownDocumentParser delegate = new MarkdownDocumentParser();
        private final AtomicBoolean failNext = new AtomicBoolean();

        void failNext() {
            failNext.set(true);
        }

        @Override
        public boolean supports(String mimeType) {
            return delegate.supports(mimeType);
        }

        @Override
        public List<ParsedBlock> parse(java.io.InputStream inputStream) {
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("sensitive parser diagnostics");
            }
            return delegate.parse(inputStream);
        }
    }
}
