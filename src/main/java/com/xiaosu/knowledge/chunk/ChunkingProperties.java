package com.xiaosu.knowledge.chunk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("xiaosu.knowledge.chunking")
public record ChunkingProperties(int maxTokens, int overlapTokens) {

    public ChunkingProperties {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (overlapTokens < 0 || overlapTokens >= maxTokens) {
            throw new IllegalArgumentException("overlapTokens must be between 0 and maxTokens - 1");
        }
    }
}
