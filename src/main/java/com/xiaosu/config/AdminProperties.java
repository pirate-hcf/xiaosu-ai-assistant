package com.xiaosu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("xiaosu.admin")
public record AdminProperties(String username, String password) {
}
