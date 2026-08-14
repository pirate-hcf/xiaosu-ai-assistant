package com.xiaosu.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("xiaosu.storage")
public record StorageProperties(Path uploadDirectory, DataSize maxFileSize) {
}
