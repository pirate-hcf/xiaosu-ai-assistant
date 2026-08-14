package com.xiaosu.knowledge.chunk;

import java.util.Objects;

import com.xiaosu.knowledge.parser.BlockLocator;

public record ChunkDraft(int chunkNo, String content, BlockLocator locator, int estimatedTokens) {

    public ChunkDraft {
        if (chunkNo < 0) {
            throw new IllegalArgumentException("chunkNo must not be negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        content = content.strip();
        Objects.requireNonNull(locator, "locator");
        if (estimatedTokens < 1) {
            throw new IllegalArgumentException("estimatedTokens must be positive");
        }
    }
}
