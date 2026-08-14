package com.xiaosu.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("xiaosu.storage")
public record StorageProperties(Path uploadDirectory) {
}
