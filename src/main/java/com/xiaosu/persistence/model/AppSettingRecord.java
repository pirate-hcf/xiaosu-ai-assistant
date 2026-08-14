package com.xiaosu.persistence.model;

import java.time.Instant;

public record AppSettingRecord(String key, String value, Instant updatedAt) {
}
