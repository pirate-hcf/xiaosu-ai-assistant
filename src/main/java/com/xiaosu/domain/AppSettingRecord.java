package com.xiaosu.domain;

import java.time.Instant;

public record AppSettingRecord(String key, String value, Instant updatedAt) {
}
