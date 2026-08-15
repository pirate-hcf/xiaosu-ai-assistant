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
import com.xiaosu.persistence.PersistenceTransaction;
import com.xiaosu.persistence.model.DocumentChunkRecord;
import com.xiaosu.persistence.model.DocumentVersionRecord;
import com.xiaosu.persistence.model.DocumentVersionStatus;
import com.xiaosu.persistence.repository.DocumentChunkRepository;
import com.xiaosu.persistence.repository.DocumentRepository;
import com.xiaosu.persistence.repository.DocumentVersionRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final PersistenceTransaction transaction;
    private final FileStore fileStore;
    private final List<DocumentParser> parsers;
    private final Chunker chunker;
    private final ObjectProvider<EmbeddingGateway> embeddingGateways;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public DocumentIndexService(
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository,
            DocumentRepository documentRepository,
            PersistenceTransaction transaction,
            FileStore fileStore,
            List<DocumentParser> parsers,
            Chunker chunker,
            ObjectProvider<EmbeddingGateway> embeddingGateways,
            ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.transaction = transaction;
        this.fileStore = fileStore;
        this.parsers = List.copyOf(parsers);
        this.chunker = chunker;
        this.embeddingGateways = embeddingGateways;
        this.objectMapper = objectMapper;
    }

    @Async("documentIndexExecutor")
    public void index(UUID versionId) {
        try {
            DocumentVersionRecord version = versionRepository.findById(versionId).orElse(null);
            if (version == null || version.status() != DocumentVersionStatus.PENDING) {
                return;
            }
            List<PreparedChunk> chunks = prepareChunks(version);
            activate(version, chunks);
            LOGGER.info("Document version indexed: versionId={}, chunks={}", versionId, chunks.size());
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

    private void activate(DocumentVersionRecord version, List<PreparedChunk> chunks) {
        transaction.required(() -> {
            DocumentVersionRecord locked = versionRepository.findByIdForUpdate(version.id()).orElse(null);
            if (locked == null || locked.status() != DocumentVersionStatus.PENDING) {
                return;
            }
            chunkRepository.deleteByVersionId(version.id());
            for (PreparedChunk chunk : chunks) {
                chunkRepository.insert(new DocumentChunkRecord(
                        UUID.randomUUID(),
                        version.id(),
                        chunk.chunkNo(),
                        chunk.content(),
                        chunk.locatorJson(),
                        chunk.embedding()));
            }
            if (!versionRepository.updateStatus(
                    version.id(), DocumentVersionStatus.PENDING, DocumentVersionStatus.INDEXED, null)) {
                throw new IllegalStateException("Document version status changed while indexing");
            }
            if (!documentRepository.setActiveVersion(version.documentId(), version.id(), clock.instant())) {
                throw new IllegalStateException("Document disappeared while indexing");
            }
        });
    }

    private void markFailed(UUID versionId, String summary) {
        try {
            transaction.required(() -> {
                DocumentVersionRecord locked = versionRepository.findByIdForUpdate(versionId).orElse(null);
                if (locked == null || locked.status() != DocumentVersionStatus.PENDING) {
                    return;
                }
                chunkRepository.deleteByVersionId(versionId);
                versionRepository.updateStatus(
                        versionId, DocumentVersionStatus.PENDING, DocumentVersionStatus.FAILED, summary);
            });
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
