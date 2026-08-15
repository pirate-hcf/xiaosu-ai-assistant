package com.xiaosu.knowledge;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xiaosu.domain.DocumentRecord;
import com.xiaosu.domain.DocumentVersionRecord;
import com.xiaosu.domain.DocumentVersionStatus;
import com.xiaosu.domain.DuplicateRecordException;
import com.xiaosu.service.DocumentService;
import com.xiaosu.service.DocumentVersionService;
import com.xiaosu.service.DocumentWorkflowService;

@Service
public class DocumentUploadService {

    private final DocumentService documentService;
    private final DocumentVersionService versionService;
    private final DocumentWorkflowService workflowService;
    private final FileStore fileStore;
    private final DocumentIndexDispatcher indexDispatcher;
    private final Clock clock;

    public DocumentUploadService(
            DocumentService documentService,
            DocumentVersionService versionService,
            DocumentWorkflowService workflowService,
            FileStore fileStore,
            DocumentIndexDispatcher indexDispatcher) {
        this.documentService = documentService;
        this.versionService = versionService;
        this.workflowService = workflowService;
        this.fileStore = fileStore;
        this.indexDispatcher = indexDispatcher;
        this.clock = Clock.systemUTC();
    }

    public UploadResult upload(MultipartFile file) {
        UploadFileName uploadFileName = UploadFileName.from(file.getOriginalFilename(), file.getContentType());
        if (documentService.findByCanonicalName(uploadFileName.canonicalName()).isPresent()) {
            throw DocumentUploadException.conflict(uploadFileName.canonicalName());
        }

        FileStore.StoredFile storedFile;
        try {
            storedFile = fileStore.store(file.getInputStream());
        } catch (IOException exception) {
            throw DocumentUploadException.storageFailure(exception);
        }

        try {
            uploadFileName.type().validateContent(storedFile.prefix());
            UploadResult result = createPendingVersion(uploadFileName, storedFile);
            indexDispatcher.submit(result.versionId());
            return result;
        } catch (DuplicateRecordException exception) {
            fileStore.deleteQuietly(storedFile.storageKey());
            throw DocumentUploadException.conflict(uploadFileName.canonicalName());
        } catch (RuntimeException exception) {
            fileStore.deleteQuietly(storedFile.storageKey());
            throw exception;
        }
    }

    public List<DocumentSummary> listDocuments() {
        return documentService.findAllNotDeleted().stream()
                .map(document -> versionService.findLatestByDocumentId(document.id())
                        .map(version -> toSummary(document, version))
                        .orElseThrow(() -> new IllegalStateException("文档缺少版本记录")))
                .toList();
    }

    public RetryResult retry(UUID documentId) {
        DocumentWorkflowService.RetryTransition transition = workflowService.retryLatestFailedVersion(documentId);
        if (transition.decision() == DocumentWorkflowService.RetryDecision.NOT_FOUND) {
            throw DocumentIndexException.notFound();
        }
        if (transition.decision() == DocumentWorkflowService.RetryDecision.NOT_RETRYABLE) {
            throw DocumentIndexException.notRetryable();
        }
        RetryResult result = new RetryResult(documentId, transition.versionId(), DocumentVersionStatus.PENDING);
        indexDispatcher.submit(result.versionId());
        return result;
    }

    private UploadResult createPendingVersion(UploadFileName uploadFileName, FileStore.StoredFile storedFile) {
        Instant now = clock.instant();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        DocumentRecord document = new DocumentRecord(
                documentId, uploadFileName.canonicalName(), null, null, now, now);
        DocumentVersionRecord version = new DocumentVersionRecord(
                versionId,
                documentId,
                1,
                storedFile.sha256(),
                uploadFileName.type().canonicalMimeType(),
                storedFile.storageKey(),
                DocumentVersionStatus.PENDING,
                null,
                now);
        workflowService.createDocumentWithVersion(document, version);
        return new UploadResult(documentId, versionId, uploadFileName.canonicalName(), 1,
                DocumentVersionStatus.PENDING, storedFile.sha256(), storedFile.size(), now);
    }

    private static DocumentSummary toSummary(DocumentRecord document, DocumentVersionRecord version) {
        return new DocumentSummary(
                document.id(),
                document.canonicalName(),
                document.activeVersionId(),
                version.id(),
                version.versionNo(),
                version.mimeType(),
                version.status(),
                version.errorMessage(),
                document.createdAt(),
                document.updatedAt());
    }

    public record UploadResult(
            UUID documentId,
            UUID versionId,
            String fileName,
            int versionNo,
            DocumentVersionStatus status,
            String sha256,
            long size,
            Instant createdAt) {
    }

    public record DocumentSummary(
            UUID documentId,
            String fileName,
            UUID activeVersionId,
            UUID latestVersionId,
            int versionNo,
            String mimeType,
            DocumentVersionStatus status,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record RetryResult(UUID documentId, UUID versionId, DocumentVersionStatus status) {
    }
}
