package com.xiaosu.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplicationHealthService {

    private final LlmProperties llmProperties;
    private final DingTalkProperties dingTalkProperties;

    public ApplicationHealthService(
            LlmProperties llmProperties,
            DingTalkProperties dingTalkProperties) {
        this.llmProperties = llmProperties;
        this.dingTalkProperties = dingTalkProperties;
    }

    public ReadinessStatus readiness() {
        Map<String, DependencyStatus> components = new LinkedHashMap<>();
        components.put("llm", dependencyStatus(isLlmConfigured()));
        components.put("dingtalk", dependencyStatus(isDingTalkConfigured()));
        return new ReadinessStatus("UP", components);
    }

    private boolean isLlmConfigured() {
        return StringUtils.hasText(llmProperties.baseUrl())
                && StringUtils.hasText(llmProperties.apiKey())
                && StringUtils.hasText(llmProperties.chatModel())
                && StringUtils.hasText(llmProperties.embeddingModel());
    }

    private boolean isDingTalkConfigured() {
        return StringUtils.hasText(dingTalkProperties.clientId())
                && StringUtils.hasText(dingTalkProperties.clientSecret());
    }

    private static DependencyStatus dependencyStatus(boolean configured) {
        return new DependencyStatus(configured ? "configured" : "not_configured");
    }

    public record ReadinessStatus(String status, Map<String, DependencyStatus> components) {
    }

    public record DependencyStatus(String status) {
    }
}
