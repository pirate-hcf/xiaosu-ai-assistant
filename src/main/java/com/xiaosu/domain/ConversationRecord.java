package com.xiaosu.domain;

import java.time.Instant;
import java.util.UUID;

public record ConversationRecord(
        UUID id,
        String sessionKey,
        Instant createdAt,
        Instant lastActiveAt) {
}
