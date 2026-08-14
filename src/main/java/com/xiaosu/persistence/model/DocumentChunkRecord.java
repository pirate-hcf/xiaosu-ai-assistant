package com.xiaosu.persistence.model;

import java.util.UUID;

public record DocumentChunkRecord(
        UUID id,
        UUID versionId,
        int chunkNo,
        String content,
        String locatorJson,
        byte[] embedding) {
}
