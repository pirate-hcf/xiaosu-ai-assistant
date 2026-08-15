package com.xiaosu.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.xiaosu.domain.AppSettingRecord;
import com.xiaosu.domain.ConversationRecord;
import com.xiaosu.domain.DocumentChunkRecord;
import com.xiaosu.domain.DocumentRecord;
import com.xiaosu.domain.DocumentVersionRecord;
import com.xiaosu.domain.DocumentVersionStatus;
import com.xiaosu.domain.DuplicateRecordException;
import com.xiaosu.domain.MessageRecord;
import com.xiaosu.domain.MessageStatus;
import com.xiaosu.service.AppSettingService;
import com.xiaosu.service.ConversationService;
import com.xiaosu.service.DocumentChunkService;
import com.xiaosu.service.DocumentService;
import com.xiaosu.service.DocumentVersionService;
import com.xiaosu.service.MessageService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:mysql:8.4:///xiaosu_mybatis_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.datasource.hikari.connection-timeout=60000"
})
@Import(MyBatisIntegrationTest.RollbackProbeConfiguration.class)
class MyBatisIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00.123456Z");

    private final DatabaseCleanupMapper databaseCleanupMapper;
    private final DocumentService documentService;
    private final DocumentVersionService versionService;
    private final DocumentChunkService chunkService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final AppSettingService settingService;
    private final RollbackProbeService rollbackProbeService;

    @Autowired
    MyBatisIntegrationTest(
            DatabaseCleanupMapper databaseCleanupMapper,
            DocumentService documentService,
            DocumentVersionService versionService,
            DocumentChunkService chunkService,
            ConversationService conversationService,
            MessageService messageService,
            AppSettingService settingService,
            RollbackProbeService rollbackProbeService) {
        this.databaseCleanupMapper = databaseCleanupMapper;
        this.documentService = documentService;
        this.versionService = versionService;
        this.chunkService = chunkService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.settingService = settingService;
        this.rollbackProbeService = rollbackProbeService;
    }

    @BeforeEach
    void clearBusinessTables() {
        databaseCleanupMapper.deleteMessages();
        databaseCleanupMapper.deleteConversations();
        databaseCleanupMapper.deleteChunks();
        databaseCleanupMapper.clearActiveVersions();
        databaseCleanupMapper.deleteVersions();
        databaseCleanupMapper.deleteDocuments();
        databaseCleanupMapper.deleteSettings();
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

        documentService.insert(document);
        versionService.insert(version);
        assertThat(documentService.setActiveVersion(document.id(), version.id(), NOW)).isTrue();
        chunkService.insert(chunk);

        DocumentRecord storedDocument = documentService.findById(document.id()).orElseThrow();
        DocumentVersionRecord storedVersion = versionService.findById(version.id()).orElseThrow();
        DocumentChunkRecord storedChunk = chunkService.findById(chunk.id()).orElseThrow();

        assertThat(storedDocument.canonicalName()).isEqualTo(document.canonicalName());
        assertThat(storedDocument.activeVersionId()).isEqualTo(version.id());
        assertThat(storedVersion).isEqualTo(version);
        assertThat(versionService.findByDocumentId(document.id())).containsExactly(version);
        assertThat(storedChunk.content()).isEqualTo(chunk.content());
        assertThat(storedChunk.locatorJson())
                .contains("\"type\": \"markdown\"")
                .contains("\"startLine\": 1")
                .contains("\"endLine\": 2");
        assertThat(storedChunk.embedding()).containsExactly(chunk.embedding());
        assertThat(chunkService.findByVersionId(version.id())).hasSize(1);
    }

    @Test
    void rejectsDuplicateCanonicalName() {
        DocumentRecord first = document("duplicate.md");
        documentService.insert(first);

        assertThatThrownBy(() -> documentService.insert(document("duplicate.md")))
                .isInstanceOf(DuplicateRecordException.class)
                .hasMessageContaining("duplicate.md");

        assertThat(documentService.findByCanonicalName("duplicate.md")).contains(first);
    }

    @Test
    void duplicateSessionAndPlatformMessageRemainSingleRecords() {
        ConversationRecord conversation = new ConversationRecord(
                UUID.randomUUID(), "dingtalk:corp:group:group-1:user-1", NOW, NOW);
        ConversationRecord duplicateConversation = new ConversationRecord(
                UUID.randomUUID(), conversation.sessionKey(), NOW, NOW);

        assertThat(conversationService.insertIfAbsent(conversation)).isTrue();
        assertThat(conversationService.insertIfAbsent(duplicateConversation)).isFalse();
        assertThat(conversationService.countBySessionKey(conversation.sessionKey())).isEqualTo(1);
        assertThat(conversationService.findBySessionKey(conversation.sessionKey())).contains(conversation);

        MessageRecord message = message(conversation.id(), "platform-message-1");
        MessageRecord duplicateMessage = message(conversation.id(), message.platformMessageId());

        assertThat(messageService.insertIfAbsent(message)).isTrue();
        assertThat(messageService.insertIfAbsent(duplicateMessage)).isFalse();
        assertThat(messageService.countByPlatformMessageId(message.platformMessageId())).isEqualTo(1);
        assertThat(messageService.findByPlatformMessageId(message.platformMessageId())).contains(message);
    }

    @Test
    void failedServiceTransactionRollsBackAllChanges() {
        DocumentRecord document = document("rollback.md");
        documentService.insert(document);

        assertThatThrownBy(() -> rollbackProbeService.markDeletedThenInsertDuplicate(document.id()))
                .isInstanceOf(DuplicateRecordException.class);

        DocumentRecord restored = documentService.findById(document.id()).orElseThrow();
        assertThat(restored.deletedAt()).isNull();
        assertThat(restored.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void settingSaveUpsertsTypedRecord() {
        settingService.save(new AppSettingRecord("chat_model", "model-a", NOW));
        settingService.save(new AppSettingRecord("chat_model", "model-b", NOW.plusSeconds(1)));

        AppSettingRecord stored = settingService.findByKey("chat_model").orElseThrow();
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

    @TestConfiguration
    static class RollbackProbeConfiguration {

        @Bean
        RollbackProbeService rollbackProbeService(DocumentService documentService) {
            return new RollbackProbeService(documentService);
        }
    }

    static class RollbackProbeService {

        private final DocumentService documentService;

        RollbackProbeService(DocumentService documentService) {
            this.documentService = documentService;
        }

        @Transactional
        public void markDeletedThenInsertDuplicate(UUID documentId) {
            documentService.markDeleted(documentId, NOW.plusSeconds(10));
            documentService.insert(document("rollback.md"));
        }
    }
}
