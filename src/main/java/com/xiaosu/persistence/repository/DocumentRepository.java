package com.xiaosu.persistence.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.xiaosu.persistence.exception.DuplicateRecordException;
import com.xiaosu.persistence.model.DocumentRecord;

@Repository
public class DocumentRepository {

    private final JdbcClient jdbcClient;

    public DocumentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(DocumentRecord document) {
        try {
            jdbcClient.sql("""
                    INSERT INTO documents (
                        id, canonical_name, active_version_id, deleted_at, created_at, updated_at
                    ) VALUES (
                        :id, :canonicalName, :activeVersionId, :deletedAt, :createdAt, :updatedAt
                    )
                    """)
                    .param("id", document.id().toString())
                    .param("canonicalName", document.canonicalName())
                    .param("activeVersionId", uuidValue(document.activeVersionId()), Types.CHAR)
                    .param("deletedAt", timestampValue(document.deletedAt()), Types.TIMESTAMP)
                    .param("createdAt", Timestamp.from(document.createdAt()))
                    .param("updatedAt", Timestamp.from(document.updatedAt()))
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new DuplicateRecordException("document", document.canonicalName(), exception);
        }
    }

    public Optional<DocumentRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM documents WHERE id = :id")
                .param("id", id.toString())
                .query(PersistenceRowMappers.DOCUMENT)
                .optional();
    }

    public Optional<DocumentRecord> findByCanonicalName(String canonicalName) {
        return jdbcClient.sql("SELECT * FROM documents WHERE canonical_name = :canonicalName")
                .param("canonicalName", canonicalName)
                .query(PersistenceRowMappers.DOCUMENT)
                .optional();
    }

    public List<DocumentRecord> findAllNotDeleted() {
        return jdbcClient.sql("""
                SELECT * FROM documents
                WHERE deleted_at IS NULL
                ORDER BY created_at DESC, canonical_name
                """)
                .query(PersistenceRowMappers.DOCUMENT)
                .list();
    }

    public boolean setActiveVersion(UUID documentId, UUID versionId, Instant updatedAt) {
        int updated = jdbcClient.sql("""
                UPDATE documents
                SET active_version_id = :versionId, updated_at = :updatedAt
                WHERE id = :documentId
                """)
                .param("versionId", versionId.toString())
                .param("updatedAt", Timestamp.from(updatedAt))
                .param("documentId", documentId.toString())
                .update();
        return updated == 1;
    }

    public boolean markDeleted(UUID documentId, Instant deletedAt) {
        int updated = jdbcClient.sql("""
                UPDATE documents
                SET deleted_at = :deletedAt, active_version_id = NULL, updated_at = :deletedAt
                WHERE id = :documentId
                """)
                .param("deletedAt", Timestamp.from(deletedAt))
                .param("documentId", documentId.toString())
                .update();
        return updated == 1;
    }

    private static String uuidValue(UUID value) {
        return value == null ? null : value.toString();
    }

    private static Timestamp timestampValue(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
