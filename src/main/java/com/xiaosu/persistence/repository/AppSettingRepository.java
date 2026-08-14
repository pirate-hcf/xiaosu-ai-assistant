package com.xiaosu.persistence.repository;

import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.xiaosu.persistence.model.AppSettingRecord;

@Repository
public class AppSettingRepository {

    private final JdbcClient jdbcClient;

    public AppSettingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(AppSettingRecord setting) {
        jdbcClient.sql("""
                INSERT INTO app_settings (setting_key, setting_value, updated_at)
                VALUES (:key, :value, :updatedAt)
                ON DUPLICATE KEY UPDATE
                    setting_value = :value,
                    updated_at = :updatedAt
                """)
                .param("key", setting.key())
                .param("value", setting.value())
                .param("updatedAt", Timestamp.from(setting.updatedAt()))
                .update();
    }

    public Optional<AppSettingRecord> findByKey(String key) {
        return jdbcClient.sql("SELECT * FROM app_settings WHERE setting_key = :key")
                .param("key", key)
                .query(PersistenceRowMappers.APP_SETTING)
                .optional();
    }
}
