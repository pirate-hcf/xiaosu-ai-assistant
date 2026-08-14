package com.xiaosu.mockapi;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("xiaosu.mock-data")
public record MockDataProperties(Path directory) {
}
