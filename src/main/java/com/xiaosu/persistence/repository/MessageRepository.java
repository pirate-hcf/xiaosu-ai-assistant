package com.xiaosu.persistence.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.xiaosu.persistence.model.MessageRecord;

@Repository
public class MessageRepository {

    private final JdbcClient jdbcClient;

    public MessageRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean insertIfAbsent(MessageRecord message) {
        try {
            jdbcClient.sql("""
                    INSERT INTO messages (
                        id, conversation_id, platform_message_id, user_text, assistant_text,
                        tool_calls_json, citations_json, prompt_tokens, completion_tokens,
                        status, trace_id, created_at
                    ) VALUES (
                        :id, :conversationId, :platformMessageId, :userText, :assistantText,
                        :toolCallsJson, :citationsJson, :promptTokens, :completionTokens,
                        :status, :traceId, :createdAt
                    )
                    """)
                    .param("id", message.id().toString())
                    .param("conversationId", message.conversationId().toString())
                    .param("platformMessageId", message.platformMessageId())
                    .param("userText", message.userText())
                    .param("assistantText", message.assistantText(), Types.LONGVARCHAR)
                    .param("toolCallsJson", message.toolCallsJson(), Types.VARCHAR)
                    .param("citationsJson", message.citationsJson(), Types.VARCHAR)
                    .param("promptTokens", message.promptTokens(), Types.INTEGER)
                    .param("completionTokens", message.completionTokens(), Types.INTEGER)
                    .param("status", message.status().databaseValue())
                    .param("traceId", message.traceId())
                    .param("createdAt", Timestamp.from(message.createdAt()))
                    .update();
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<MessageRecord> findByPlatformMessageId(String platformMessageId) {
        return jdbcClient.sql("""
                SELECT * FROM messages
                WHERE platform_message_id = :platformMessageId
                """)
                .param("platformMessageId", platformMessageId)
                .query(PersistenceRowMappers.MESSAGE)
                .optional();
    }

    public long countByPlatformMessageId(String platformMessageId) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM messages
                WHERE platform_message_id = :platformMessageId
                """)
                .param("platformMessageId", platformMessageId)
                .query(Long.class)
                .single();
    }
}
