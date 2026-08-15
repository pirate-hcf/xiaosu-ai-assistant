package com.xiaosu.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xiaosu.domain.DocumentChunkRecord;
import com.xiaosu.domain.DocumentRecord;
import com.xiaosu.domain.DocumentVersionRecord;
import com.xiaosu.domain.DocumentVersionStatus;

@Service
public class DocumentWorkflowService {

    private final DocumentService documentService;
    private final DocumentVersionService versionService;
    private final DocumentChunkService chunkService;

    public DocumentWorkflowService(
            DocumentService documentService,
            DocumentVersionService versionService,
            DocumentChunkService chunkService) {
        this.documentService = documentService;
        this.versionService = versionService;
        this.chunkService = chunkService;
    }

    @Transactional
    public void createDocumentWithVersion(DocumentRecord document, DocumentVersionRecord version) {
        documentService.insert(document);
        versionService.insert(version);
    }

    @Transactional
    public boolean activateVersion(UUID versionId, List<DocumentChunkRecord> chunks, Instant updatedAt) {
        DocumentVersionRecord locked = versionService.findByIdForUpdate(versionId).orElse(null);
        if (locked == null || locked.status() != DocumentVersionStatus.PENDING) {
            return false;
        }
        chunkService.deleteByVersionId(versionId);
        chunks.forEach(chunkService::insert);
        if (!versionService.updateStatus(
                versionId, DocumentVersionStatus.PENDING, DocumentVersionStatus.INDEXED, null)) {
            throw new IllegalStateException("Document version status changed while indexing");
        }
        if (!documentService.setActiveVersion(locked.documentId(), versionId, updatedAt)) {
            throw new IllegalStateException("Document disappeared while indexing");
        }
        return true;
    }

    @Transactional
    public void markVersionFailed(UUID versionId, String summary) {
        DocumentVersionRecord locked = versionService.findByIdForUpdate(versionId).orElse(null);
        if (locked == null || locked.status() != DocumentVersionStatus.PENDING) {
            return;
        }
        chunkService.deleteByVersionId(versionId);
        versionService.updateStatus(
                versionId, DocumentVersionStatus.PENDING, DocumentVersionStatus.FAILED, summary);
    }

    @Transactional
    public RetryTransition retryLatestFailedVersion(UUID documentId) {
        if (documentService.findById(documentId).isEmpty()) {
            return RetryTransition.notFound();
        }
        DocumentVersionRecord version = versionService.findLatestByDocumentId(documentId).orElse(null);
        if (version == null || !versionService.updateStatus(
                version.id(), DocumentVersionStatus.FAILED, DocumentVersionStatus.PENDING, null)) {
            return RetryTransition.notRetryable();
        }
        return RetryTransition.retrying(version.id());
    }

    public record RetryTransition(RetryDecision decision, UUID versionId) {

        public static RetryTransition retrying(UUID versionId) {
            return new RetryTransition(RetryDecision.RETRYING, versionId);
        }

        public static RetryTransition notFound() {
            return new RetryTransition(RetryDecision.NOT_FOUND, null);
        }

        public static RetryTransition notRetryable() {
            return new RetryTransition(RetryDecision.NOT_RETRYABLE, null);
        }
    }

    public enum RetryDecision {
        RETRYING,
        NOT_FOUND,
        NOT_RETRYABLE
    }
}
