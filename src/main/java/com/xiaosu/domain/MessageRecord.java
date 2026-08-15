package com.xiaosu.domain;

import java.time.Instant;
import java.util.UUID;

public record MessageRecord(
        UUID id,
        UUID conversationId,
        String platformMessageId,
        String userText,
        String assistantText,
        String toolCallsJson,
        String citationsJson,
        Integer promptTokens,
        Integer completionTokens,
        MessageStatus status,
        String traceId,
        Instant createdAt) {
}
