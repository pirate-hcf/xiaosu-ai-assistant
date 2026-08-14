package com.xiaosu.persistence.model;

import java.time.Instant;
import java.util.UUID;

public record DocumentRecord(
        UUID id,
        String canonicalName,
        UUID activeVersionId,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {
}
