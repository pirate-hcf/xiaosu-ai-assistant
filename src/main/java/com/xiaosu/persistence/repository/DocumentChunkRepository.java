package com.xiaosu.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.xiaosu.persistence.exception.DuplicateRecordException;
import com.xiaosu.persistence.model.DocumentChunkRecord;

@Repository
public class DocumentChunkRepository {

    private final JdbcClient jdbcClient;

    public DocumentChunkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(DocumentChunkRecord chunk) {
        try {
            jdbcClient.sql("""
                    INSERT INTO document_chunks (
                        id, version_id, chunk_no, content, locator_json, embedding
                    ) VALUES (
                        :id, :versionId, :chunkNo, :content, :locatorJson, :embedding
                    )
                    """)
                    .param("id", chunk.id().toString())
                    .param("versionId", chunk.versionId().toString())
                    .param("chunkNo", chunk.chunkNo())
                    .param("content", chunk.content())
                    .param("locatorJson", chunk.locatorJson())
                    .param("embedding", chunk.embedding())
                    .update();
        } catch (DuplicateKeyException exception) {
            String key = chunk.versionId() + ":" + chunk.chunkNo();
            throw new DuplicateRecordException("document chunk", key, exception);
        }
    }

    public Optional<DocumentChunkRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM document_chunks WHERE id = :id")
                .param("id", id.toString())
                .query(PersistenceRowMappers.DOCUMENT_CHUNK)
                .optional();
    }

    public List<DocumentChunkRecord> findByVersionId(UUID versionId) {
        return jdbcClient.sql("""
                SELECT * FROM document_chunks
                WHERE version_id = :versionId
                ORDER BY chunk_no
                """)
                .param("versionId", versionId.toString())
                .query(PersistenceRowMappers.DOCUMENT_CHUNK)
                .list();
    }
}
