package com.xiaosu.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.xiaosu.persistence.model.DocumentVersionRecord;
import com.xiaosu.persistence.model.DocumentVersionStatus;
import com.xiaosu.persistence.repository.DocumentRepository;
import com.xiaosu.persistence.repository.DocumentVersionRepository;

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
    private JdbcClient jdbcClient;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private FileStore fileStore;

    @BeforeEach
    void resetState() throws IOException {
        jdbcClient.sql("DELETE FROM document_chunks").update();
        jdbcClient.sql("UPDATE documents SET active_version_id = NULL").update();
        jdbcClient.sql("DELETE FROM document_versions").update();
        jdbcClient.sql("DELETE FROM documents").update();
        Files.createDirectories(TEST_UPLOAD_DIRECTORY);
        try (Stream<Path> paths = Files.list(TEST_UPLOAD_DIRECTORY)) {
            for (Path path : paths.toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void markdownUploadCreatesPendingVersionAndRandomPhysicalFile() throws Exception {
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

        var document = documentRepository.findById(result.documentId()).orElseThrow();
        DocumentVersionRecord version = versionRepository.findById(result.versionId()).orElseThrow();
        assertEquals("员工手册.md", document.canonicalName());
        assertEquals(DocumentVersionStatus.PENDING, version.status());
        assertEquals("text/markdown", version.mimeType());
        assertEquals(result.sha256(), version.sha256());
        assertTrue(version.storagePath().matches("[0-9a-f-]{36}"));
        assertFalse(Path.of(version.storagePath()).isAbsolute());
        assertFalse(version.storagePath().contains("员工手册"));
        assertTrue(fileStore.exists(version.storagePath()));
        assertFalse(response.body().contains(TEST_UPLOAD_DIRECTORY.toAbsolutePath().toString()));
        assertFalse(response.body().contains(version.storagePath()));
    }

    @Test
    void listReturnsPendingMetadataWithoutStoragePath() throws Exception {
        HttpResponse<String> uploadResponse = upload(
                "policy.txt", "text/plain", "差旅报销制度".getBytes(StandardCharsets.UTF_8));
        assertEquals(202, uploadResponse.statusCode());

        HttpResponse<String> response = get("/api/documents");

        assertEquals(200, response.statusCode());
        List<DocumentUploadService.DocumentSummary> documents = objectMapper.readValue(response.body(), DOCUMENT_LIST);
        assertEquals(1, documents.size());
        assertEquals("policy.txt", documents.getFirst().fileName());
        assertEquals(DocumentVersionStatus.PENDING, documents.getFirst().status());
        assertNotNull(documents.getFirst().latestVersionId());
        assertFalse(response.body().contains("storagePath"));
        assertFalse(response.body().contains(TEST_UPLOAD_DIRECTORY.toAbsolutePath().toString()));
    }

    @Test
    void markdownTextAndPdfAreAccepted() throws Exception {
        assertEquals(202, upload("guide.md", "application/octet-stream", "# Guide".getBytes(StandardCharsets.UTF_8))
                .statusCode());
        assertEquals(202, upload("faq.txt", "text/plain", "FAQ".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(202, upload("rules.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII))
                .statusCode());
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
        assertEquals(202, upload("same.md", "text/markdown", "first".getBytes(StandardCharsets.UTF_8)).statusCode());

        HttpResponse<String> response = upload("same.md", "text/markdown", "second".getBytes(StandardCharsets.UTF_8));

        assertEquals(409, response.statusCode());
        DocumentApiExceptionHandler.ApiError error = objectMapper.readValue(
                response.body(), DocumentApiExceptionHandler.ApiError.class);
        assertEquals("DOCUMENT_ALREADY_EXISTS", error.code());
        assertEquals(1L, count("documents"));
        assertEquals(1L, count("document_versions"));
        assertEquals(1L, storedFileCount());
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

    private long count(String table) {
        if (!table.equals("documents") && !table.equals("document_versions")) {
            throw new IllegalArgumentException("Unexpected table");
        }
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private long storedFileCount() throws IOException {
        try (Stream<Path> paths = Files.list(TEST_UPLOAD_DIRECTORY)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
