package com.xiaosu.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionRecord(
        UUID id,
        UUID documentId,
        int versionNo,
        String sha256,
        String mimeType,
        String storagePath,
        DocumentVersionStatus status,
        String errorMessage,
        Instant createdAt) {
}
