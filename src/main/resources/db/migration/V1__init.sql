CREATE TABLE documents (
    id CHAR(36) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    active_version_id CHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT uk_documents_canonical_name UNIQUE (canonical_name),
    INDEX idx_documents_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE document_versions (
    id CHAR(36) NOT NULL,
    document_id CHAR(36) NOT NULL,
    version_no INT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_document_versions PRIMARY KEY (id),
    CONSTRAINT uk_document_versions_document_version UNIQUE (document_id, version_no),
    CONSTRAINT fk_document_versions_document
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    INDEX idx_document_versions_status (status),
    INDEX idx_document_versions_sha256 (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_active_version
        FOREIGN KEY (active_version_id) REFERENCES document_versions (id) ON DELETE SET NULL;

CREATE TABLE document_chunks (
    id CHAR(36) NOT NULL,
    version_id CHAR(36) NOT NULL,
    chunk_no INT NOT NULL,
    content TEXT NOT NULL,
    locator_json JSON NOT NULL,
    embedding LONGBLOB NOT NULL,
    CONSTRAINT pk_document_chunks PRIMARY KEY (id),
    CONSTRAINT uk_document_chunks_version_chunk UNIQUE (version_id, chunk_no),
    CONSTRAINT fk_document_chunks_version
        FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE conversations (
    id CHAR(36) NOT NULL,
    session_key VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_active_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT uk_conversations_session_key UNIQUE (session_key),
    INDEX idx_conversations_last_active_at (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE messages (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    platform_message_id VARCHAR(255) NOT NULL,
    user_text TEXT NOT NULL,
    assistant_text TEXT NULL,
    tool_calls_json JSON NULL,
    citations_json JSON NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    status VARCHAR(20) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT uk_messages_platform_message_id UNIQUE (platform_message_id),
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    INDEX idx_messages_conversation_created_at (conversation_id, created_at),
    INDEX idx_messages_status_created_at (status, created_at),
    INDEX idx_messages_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_settings (
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_app_settings PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
