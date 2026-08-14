package com.xiaosu.persistence.repository;

import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.xiaosu.persistence.model.ConversationRecord;

@Repository
public class ConversationRepository {

    private final JdbcClient jdbcClient;

    public ConversationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean insertIfAbsent(ConversationRecord conversation) {
        try {
            jdbcClient.sql("""
                    INSERT INTO conversations (id, session_key, created_at, last_active_at)
                    VALUES (:id, :sessionKey, :createdAt, :lastActiveAt)
                    """)
                    .param("id", conversation.id().toString())
                    .param("sessionKey", conversation.sessionKey())
                    .param("createdAt", Timestamp.from(conversation.createdAt()))
                    .param("lastActiveAt", Timestamp.from(conversation.lastActiveAt()))
                    .update();
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<ConversationRecord> findBySessionKey(String sessionKey) {
        return jdbcClient.sql("SELECT * FROM conversations WHERE session_key = :sessionKey")
                .param("sessionKey", sessionKey)
                .query(PersistenceRowMappers.CONVERSATION)
                .optional();
    }

    public long countBySessionKey(String sessionKey) {
        return jdbcClient.sql("SELECT COUNT(*) FROM conversations WHERE session_key = :sessionKey")
                .param("sessionKey", sessionKey)
                .query(Long.class)
                .single();
    }
}
