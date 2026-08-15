package com.xiaosu.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.xiaosu.knowledge.chunk.ChunkDraft;
import com.xiaosu.knowledge.chunk.Chunker;
import com.xiaosu.knowledge.embedding.EmbeddingGateway;
import com.xiaosu.knowledge.embedding.EmbeddingGatewayException;
import com.xiaosu.knowledge.embedding.FloatVectorCodec;
import com.xiaosu.knowledge.parser.DocumentParseException;
import com.xiaosu.knowledge.parser.DocumentParser;
import com.xiaosu.domain.DocumentChunkRecord;
import com.xiaosu.domain.DocumentVersionRecord;
import com.xiaosu.domain.DocumentVersionStatus;
import com.xiaosu.service.DocumentVersionService;
import com.xiaosu.service.DocumentWorkflowService;

import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final DocumentVersionService versionService;
    private final DocumentWorkflowService workflowService;
    private final FileStore fileStore;
    private final List<DocumentParser> parsers;
    private final Chunker chunker;
    private final ObjectProvider<EmbeddingGateway> embeddingGateways;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public DocumentIndexService(
            DocumentVersionService versionService,
            DocumentWorkflowService workflowService,
            FileStore fileStore,
            List<DocumentParser> parsers,
            Chunker chunker,
            ObjectProvider<EmbeddingGateway> embeddingGateways,
            ObjectMapper objectMapper) {
        this.versionService = versionService;
        this.workflowService = workflowService;
        this.fileStore = fileStore;
        this.parsers = List.copyOf(parsers);
        this.chunker = chunker;
        this.embeddingGateways = embeddingGateways;
        this.objectMapper = objectMapper;
    }

    @Async("documentIndexExecutor")
    public void index(UUID versionId) {
        try {
            DocumentVersionRecord version = versionService.findById(versionId).orElse(null);
            if (version == null || version.status() != DocumentVersionStatus.PENDING) {
                return;
            }
            List<PreparedChunk> chunks = prepareChunks(version);
            if (activate(version, chunks)) {
                LOGGER.info("Document version indexed: versionId={}, chunks={}", versionId, chunks.size());
            }
        } catch (Exception exception) {
            String summary = safeSummary(exception);
            markFailed(versionId, summary);
            LOGGER.warn("Document version indexing failed: versionId={}, reason={}", versionId, summary);
        }
    }

    private List<PreparedChunk> prepareChunks(DocumentVersionRecord version) throws IOException {
        DocumentParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(version.mimeType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No parser configured"));
        EmbeddingGateway embeddingGateway = embeddingGateways.getIfAvailable(
                () -> {
                    throw new EmbeddingGatewayException("Embedding service is not configured");
                });

        List<ChunkDraft> drafts;
        try (InputStream inputStream = fileStore.open(version.storagePath())) {
            drafts = chunker.chunk(parser.parse(inputStream));
        }
        if (drafts.isEmpty()) {
            throw new IllegalStateException("Parser produced no chunks");
        }
        return drafts.stream()
                .map(draft -> new PreparedChunk(
                        draft.chunkNo(),
                        draft.content(),
                        locatorJson(draft),
                        FloatVectorCodec.encode(embeddingGateway.embed(draft.content()))))
                .toList();
    }

    private String locatorJson(ChunkDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft.locator());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize locator", exception);
        }
    }

    private boolean activate(DocumentVersionRecord version, List<PreparedChunk> chunks) {
        List<DocumentChunkRecord> records = chunks.stream()
                .map(chunk -> new DocumentChunkRecord(
                        UUID.randomUUID(),
                        version.id(),
                        chunk.chunkNo(),
                        chunk.content(),
                        chunk.locatorJson(),
                        chunk.embedding()))
                .toList();
        return workflowService.activateVersion(version.id(), records, clock.instant());
    }

    private void markFailed(UUID versionId, String summary) {
        try {
            workflowService.markVersionFailed(versionId, summary);
        } catch (RuntimeException persistenceFailure) {
            LOGGER.error("Could not persist indexing failure: versionId={}", versionId);
        }
    }

    private static String safeSummary(Exception exception) {
        String message;
        if (exception instanceof DocumentParseException parseException) {
            message = parseException.getMessage();
        } else if (exception instanceof EmbeddingGatewayException) {
            message = "Embedding 生成失败，请稍后重试";
        } else if (exception instanceof IOException) {
            message = "原始文件读取失败，请重新上传";
        } else {
            message = "文档索引失败，请稍后重试";
        }
        if (message == null || message.isBlank()) {
            message = "文档索引失败，请稍后重试";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private record PreparedChunk(int chunkNo, String content, String locatorJson, byte[] embedding) {
    }
}
