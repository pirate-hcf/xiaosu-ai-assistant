package com.xiaosu.persistence.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;

import com.xiaosu.persistence.model.AppSettingRecord;
import com.xiaosu.persistence.model.ConversationRecord;
import com.xiaosu.persistence.model.DocumentChunkRecord;
import com.xiaosu.persistence.model.DocumentRecord;
import com.xiaosu.persistence.model.DocumentVersionRecord;
import com.xiaosu.persistence.model.DocumentVersionStatus;
import com.xiaosu.persistence.model.MessageRecord;
import com.xiaosu.persistence.model.MessageStatus;

final class PersistenceRowMappers {

    static final RowMapper<DocumentRecord> DOCUMENT = (resultSet, rowNumber) -> new DocumentRecord(
            uuid(resultSet, "id"),
            resultSet.getString("canonical_name"),
            nullableUuid(resultSet, "active_version_id"),
            nullableInstant(resultSet, "deleted_at"),
            instant(resultSet, "created_at"),
            instant(resultSet, "updated_at"));

    static final RowMapper<DocumentVersionRecord> DOCUMENT_VERSION = (resultSet, rowNumber) ->
            new DocumentVersionRecord(
                    uuid(resultSet, "id"),
                    uuid(resultSet, "document_id"),
                    resultSet.getInt("version_no"),
                    resultSet.getString("sha256"),
                    resultSet.getString("mime_type"),
                    resultSet.getString("storage_path"),
                    DocumentVersionStatus.fromDatabase(resultSet.getString("status")),
                    resultSet.getString("error_message"),
                    instant(resultSet, "created_at"));

    static final RowMapper<DocumentChunkRecord> DOCUMENT_CHUNK = (resultSet, rowNumber) ->
            new DocumentChunkRecord(
                    uuid(resultSet, "id"),
                    uuid(resultSet, "version_id"),
                    resultSet.getInt("chunk_no"),
                    resultSet.getString("content"),
                    resultSet.getString("locator_json"),
                    resultSet.getBytes("embedding"));

    static final RowMapper<ConversationRecord> CONVERSATION = (resultSet, rowNumber) ->
            new ConversationRecord(
                    uuid(resultSet, "id"),
                    resultSet.getString("session_key"),
                    instant(resultSet, "created_at"),
                    instant(resultSet, "last_active_at"));

    static final RowMapper<MessageRecord> MESSAGE = (resultSet, rowNumber) -> new MessageRecord(
            uuid(resultSet, "id"),
            uuid(resultSet, "conversation_id"),
            resultSet.getString("platform_message_id"),
            resultSet.getString("user_text"),
            resultSet.getString("assistant_text"),
            resultSet.getString("tool_calls_json"),
            resultSet.getString("citations_json"),
            nullableInteger(resultSet, "prompt_tokens"),
            nullableInteger(resultSet, "completion_tokens"),
            MessageStatus.fromDatabase(resultSet.getString("status")),
            resultSet.getString("trace_id"),
            instant(resultSet, "created_at"));

    static final RowMapper<AppSettingRecord> APP_SETTING = (resultSet, rowNumber) ->
            new AppSettingRecord(
                    resultSet.getString("setting_key"),
                    resultSet.getString("setting_value"),
                    instant(resultSet, "updated_at"));

    private PersistenceRowMappers() {
    }

    private static UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return UUID.fromString(resultSet.getString(column));
    }

    private static UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
