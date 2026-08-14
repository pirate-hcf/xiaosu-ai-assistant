package com.xiaosu.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplicationHealthService {

    private final LlmProperties llmProperties;
    private final DingTalkProperties dingTalkProperties;
    private final DataSource dataSource;

    public ApplicationHealthService(
            LlmProperties llmProperties,
            DingTalkProperties dingTalkProperties,
            DataSource dataSource) {
        this.llmProperties = llmProperties;
        this.dingTalkProperties = dingTalkProperties;
        this.dataSource = dataSource;
    }

    public ReadinessStatus readiness() {
        boolean mysqlUp = isMySqlUp();
        Map<String, DependencyStatus> components = new LinkedHashMap<>();
        components.put("mysql", new DependencyStatus(mysqlUp ? "up" : "down"));
        components.put("llm", dependencyStatus(isLlmConfigured()));
        components.put("dingtalk", dependencyStatus(isDingTalkConfigured()));
        return new ReadinessStatus(mysqlUp ? "UP" : "DOWN", components);
    }

    private boolean isMySqlUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            return false;
        }
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
