package com.xiaosu.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("xiaosu.llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel,
        Duration timeout) {
}
