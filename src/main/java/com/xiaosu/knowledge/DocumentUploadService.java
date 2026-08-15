package com.xiaosu.knowledge;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xiaosu.persistence.PersistenceTransaction;
import com.xiaosu.persistence.exception.DuplicateRecordException;
import com.xiaosu.persistence.model.DocumentRecord;
import com.xiaosu.persistence.model.DocumentVersionRecord;
import com.xiaosu.persistence.model.DocumentVersionStatus;
import com.xiaosu.persistence.repository.DocumentRepository;
import com.xiaosu.persistence.repository.DocumentVersionRepository;

@Service
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final PersistenceTransaction transaction;
    private final FileStore fileStore;
    private final DocumentIndexDispatcher indexDispatcher;
    private final Clock clock;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            PersistenceTransaction transaction,
            FileStore fileStore,
            DocumentIndexDispatcher indexDispatcher) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.transaction = transaction;
        this.fileStore = fileStore;
        this.indexDispatcher = indexDispatcher;
        this.clock = Clock.systemUTC();
    }

    public UploadResult upload(MultipartFile file) {
        UploadFileName uploadFileName = UploadFileName.from(file.getOriginalFilename(), file.getContentType());
        if (documentRepository.findByCanonicalName(uploadFileName.canonicalName()).isPresent()) {
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
        return documentRepository.findAllNotDeleted().stream()
                .map(document -> versionRepository.findLatestByDocumentId(document.id())
                        .map(version -> toSummary(document, version))
                        .orElseThrow(() -> new IllegalStateException("文档缺少版本记录")))
                .toList();
    }

    public RetryResult retry(UUID documentId) {
        RetryResult result = transaction.required(() -> {
            if (documentRepository.findById(documentId).isEmpty()) {
                throw DocumentIndexException.notFound();
            }
            DocumentVersionRecord version = versionRepository.findLatestByDocumentId(documentId)
                    .orElseThrow(DocumentIndexException::notFound);
            if (!versionRepository.updateStatus(
                    version.id(), DocumentVersionStatus.FAILED, DocumentVersionStatus.PENDING, null)) {
                throw DocumentIndexException.notRetryable();
            }
            return new RetryResult(documentId, version.id(), DocumentVersionStatus.PENDING);
        });
        indexDispatcher.submit(result.versionId());
        return result;
    }

    private UploadResult createPendingVersion(UploadFileName uploadFileName, FileStore.StoredFile storedFile) {
        return transaction.required(() -> {
            Instant now = clock.instant();
            UUID documentId = UUID.randomUUID();
            UUID versionId = UUID.randomUUID();
            documentRepository.insert(new DocumentRecord(documentId, uploadFileName.canonicalName(), null, null, now, now));
            versionRepository.insert(new DocumentVersionRecord(
                    versionId,
                    documentId,
                    1,
                    storedFile.sha256(),
                    uploadFileName.type().canonicalMimeType(),
                    storedFile.storageKey(),
                    DocumentVersionStatus.PENDING,
                    null,
                    now));
            return new UploadResult(documentId, versionId, uploadFileName.canonicalName(), 1,
                    DocumentVersionStatus.PENDING, storedFile.sha256(), storedFile.size(), now);
        });
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
