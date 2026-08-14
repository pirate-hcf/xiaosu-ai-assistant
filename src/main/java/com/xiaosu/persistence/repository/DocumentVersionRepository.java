package com.xiaosu.persistence.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.xiaosu.persistence.exception.DuplicateRecordException;
import com.xiaosu.persistence.model.DocumentVersionRecord;

@Repository
public class DocumentVersionRepository {

    private final JdbcClient jdbcClient;

    public DocumentVersionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(DocumentVersionRecord version) {
        try {
            jdbcClient.sql("""
                    INSERT INTO document_versions (
                        id, document_id, version_no, sha256, mime_type, storage_path,
                        status, error_message, created_at
                    ) VALUES (
                        :id, :documentId, :versionNo, :sha256, :mimeType, :storagePath,
                        :status, :errorMessage, :createdAt
                    )
                    """)
                    .param("id", version.id().toString())
                    .param("documentId", version.documentId().toString())
                    .param("versionNo", version.versionNo())
                    .param("sha256", version.sha256())
                    .param("mimeType", version.mimeType())
                    .param("storagePath", version.storagePath())
                    .param("status", version.status().databaseValue())
                    .param("errorMessage", version.errorMessage(), Types.VARCHAR)
                    .param("createdAt", Timestamp.from(version.createdAt()))
                    .update();
        } catch (DuplicateKeyException exception) {
            String key = version.documentId() + ":" + version.versionNo();
            throw new DuplicateRecordException("document version", key, exception);
        }
    }

    public Optional<DocumentVersionRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM document_versions WHERE id = :id")
                .param("id", id.toString())
                .query(PersistenceRowMappers.DOCUMENT_VERSION)
                .optional();
    }

    public List<DocumentVersionRecord> findByDocumentId(UUID documentId) {
        return jdbcClient.sql("""
                SELECT * FROM document_versions
                WHERE document_id = :documentId
                ORDER BY version_no
                """)
                .param("documentId", documentId.toString())
                .query(PersistenceRowMappers.DOCUMENT_VERSION)
                .list();
    }

    public Optional<DocumentVersionRecord> findLatestByDocumentId(UUID documentId) {
        return jdbcClient.sql("""
                SELECT * FROM document_versions
                WHERE document_id = :documentId
                ORDER BY version_no DESC
                LIMIT 1
                """)
                .param("documentId", documentId.toString())
                .query(PersistenceRowMappers.DOCUMENT_VERSION)
                .optional();
    }
}
