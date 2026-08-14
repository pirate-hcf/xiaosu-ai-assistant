package com.xiaosu.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.xiaosu.persistence.exception.DuplicateRecordException;
import com.xiaosu.persistence.model.AppSettingRecord;
import com.xiaosu.persistence.model.ConversationRecord;
import com.xiaosu.persistence.model.DocumentChunkRecord;
import com.xiaosu.persistence.model.DocumentRecord;
import com.xiaosu.persistence.model.DocumentVersionRecord;
import com.xiaosu.persistence.model.DocumentVersionStatus;
import com.xiaosu.persistence.model.MessageRecord;
import com.xiaosu.persistence.model.MessageStatus;
import com.xiaosu.persistence.repository.AppSettingRepository;
import com.xiaosu.persistence.repository.ConversationRepository;
import com.xiaosu.persistence.repository.DocumentChunkRepository;
import com.xiaosu.persistence.repository.DocumentRepository;
import com.xiaosu.persistence.repository.DocumentVersionRepository;
import com.xiaosu.persistence.repository.MessageRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:mysql:8.4:///xiaosu_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.datasource.hikari.connection-timeout=60000"
})
class RepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00.123456Z");

    private final JdbcClient jdbcClient;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AppSettingRepository settingRepository;
    private final PersistenceTransaction transaction;

    @Autowired
    RepositoryIntegrationTest(
            JdbcClient jdbcClient,
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            AppSettingRepository settingRepository,
            PersistenceTransaction transaction) {
        this.jdbcClient = jdbcClient;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.settingRepository = settingRepository;
        this.transaction = transaction;
    }

    @BeforeEach
    void clearBusinessTables() {
        jdbcClient.sql("DELETE FROM messages").update();
        jdbcClient.sql("DELETE FROM conversations").update();
        jdbcClient.sql("DELETE FROM document_chunks").update();
        jdbcClient.sql("UPDATE documents SET active_version_id = NULL").update();
        jdbcClient.sql("DELETE FROM document_versions").update();
        jdbcClient.sql("DELETE FROM documents").update();
        jdbcClient.sql("DELETE FROM app_settings").update();
    }

    @Test
    void insertsAndReadsDocumentVersionAndChunk() {
        DocumentRecord document = document("employee-handbook.md");
        DocumentVersionRecord version = version(document.id(), 1);
        DocumentChunkRecord chunk = new DocumentChunkRecord(
                UUID.randomUUID(),
                version.id(),
                0,
                "Employees receive annual leave.",
                "{\"type\":\"markdown\",\"startLine\":1,\"endLine\":2}",
                new byte[] {1, 2, 3, 4});

        documentRepository.insert(document);
        versionRepository.insert(version);
        assertThat(documentRepository.setActiveVersion(document.id(), version.id(), NOW)).isTrue();
        chunkRepository.insert(chunk);

        DocumentRecord storedDocument = documentRepository.findById(document.id()).orElseThrow();
        DocumentVersionRecord storedVersion = versionRepository.findById(version.id()).orElseThrow();
        DocumentChunkRecord storedChunk = chunkRepository.findById(chunk.id()).orElseThrow();

        assertThat(storedDocument.canonicalName()).isEqualTo(document.canonicalName());
        assertThat(storedDocument.activeVersionId()).isEqualTo(version.id());
        assertThat(storedVersion).isEqualTo(version);
        assertThat(versionRepository.findByDocumentId(document.id())).containsExactly(version);
        assertThat(storedChunk.content()).isEqualTo(chunk.content());
        assertThat(storedChunk.locatorJson())
                .contains("\"type\": \"markdown\"")
                .contains("\"startLine\": 1")
                .contains("\"endLine\": 2");
        assertThat(storedChunk.embedding()).containsExactly(chunk.embedding());
        assertThat(chunkRepository.findByVersionId(version.id())).hasSize(1);
    }

    @Test
    void rejectsDuplicateCanonicalName() {
        DocumentRecord first = document("duplicate.md");
        documentRepository.insert(first);

        assertThatThrownBy(() -> documentRepository.insert(document("duplicate.md")))
                .isInstanceOf(DuplicateRecordException.class)
                .hasMessageContaining("duplicate.md");

        assertThat(documentRepository.findByCanonicalName("duplicate.md"))
                .contains(first);
    }

    @Test
    void duplicateSessionAndPlatformMessageRemainSingleRecords() {
        ConversationRecord conversation = new ConversationRecord(
                UUID.randomUUID(), "dingtalk:corp:group:group-1:user-1", NOW, NOW);
        ConversationRecord duplicateConversation = new ConversationRecord(
                UUID.randomUUID(), conversation.sessionKey(), NOW, NOW);

        assertThat(conversationRepository.insertIfAbsent(conversation)).isTrue();
        assertThat(conversationRepository.insertIfAbsent(duplicateConversation)).isFalse();
        assertThat(conversationRepository.countBySessionKey(conversation.sessionKey())).isEqualTo(1);
        assertThat(conversationRepository.findBySessionKey(conversation.sessionKey()))
                .contains(conversation);

        MessageRecord message = message(conversation.id(), "platform-message-1");
        MessageRecord duplicateMessage = message(conversation.id(), message.platformMessageId());

        assertThat(messageRepository.insertIfAbsent(message)).isTrue();
        assertThat(messageRepository.insertIfAbsent(duplicateMessage)).isFalse();
        assertThat(messageRepository.countByPlatformMessageId(message.platformMessageId())).isEqualTo(1);
        assertThat(messageRepository.findByPlatformMessageId(message.platformMessageId()))
                .contains(message);
    }

    @Test
    void failedDeleteTransactionRollsBackAllChanges() {
        DocumentRecord document = document("rollback.md");
        documentRepository.insert(document);

        assertThatThrownBy(() -> transaction.required(() -> {
            assertThat(documentRepository.markDeleted(document.id(), NOW.plusSeconds(10))).isTrue();
            documentRepository.insert(document("rollback.md"));
        })).isInstanceOf(DuplicateRecordException.class);

        DocumentRecord restored = documentRepository.findById(document.id()).orElseThrow();
        assertThat(restored.deletedAt()).isNull();
        assertThat(restored.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void settingSaveUpsertsTypedRecord() {
        settingRepository.save(new AppSettingRecord("chat_model", "model-a", NOW));
        settingRepository.save(new AppSettingRecord("chat_model", "model-b", NOW.plusSeconds(1)));

        AppSettingRecord stored = settingRepository.findByKey("chat_model").orElseThrow();
        assertThat(stored.value()).isEqualTo("model-b");
        assertThat(stored.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    private static DocumentRecord document(String canonicalName) {
        return new DocumentRecord(UUID.randomUUID(), canonicalName, null, null, NOW, NOW);
    }

    private static DocumentVersionRecord version(UUID documentId, int versionNo) {
        return new DocumentVersionRecord(
                UUID.randomUUID(),
                documentId,
                versionNo,
                "a".repeat(64),
                "text/markdown",
                "uploads/employee-handbook.md",
                DocumentVersionStatus.INDEXED,
                null,
                NOW);
    }

    private static MessageRecord message(UUID conversationId, String platformMessageId) {
        return new MessageRecord(
                UUID.randomUUID(),
                conversationId,
                platformMessageId,
                "hello",
                "world",
                "[]",
                "[]",
                10,
                5,
                MessageStatus.COMPLETED,
                UUID.randomUUID().toString(),
                NOW);
    }
}
