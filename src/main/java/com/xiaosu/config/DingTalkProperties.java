package com.xiaosu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("xiaosu.dingtalk")
public record DingTalkProperties(
        String clientId,
        String clientSecret,
        String cardTemplateId) {
}
