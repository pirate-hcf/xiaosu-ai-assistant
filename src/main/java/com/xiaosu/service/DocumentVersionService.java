package com.xiaosu.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.xiaosu.domain.DocumentVersionRecord;
import com.xiaosu.domain.DocumentVersionStatus;
import com.xiaosu.domain.DuplicateRecordException;
import com.xiaosu.mapper.DocumentVersionMapper;

@Service
public class DocumentVersionService {

    private final DocumentVersionMapper versionMapper;

    public DocumentVersionService(DocumentVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    public void insert(DocumentVersionRecord version) {
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException exception) {
            String key = version.documentId() + ":" + version.versionNo();
            throw new DuplicateRecordException("document version", key, exception);
        }
    }

    public Optional<DocumentVersionRecord> findById(UUID id) {
        return versionMapper.findById(id);
    }

    public Optional<DocumentVersionRecord> findByIdForUpdate(UUID id) {
        return versionMapper.findByIdForUpdate(id);
    }

    public List<DocumentVersionRecord> findByStatus(DocumentVersionStatus status) {
        return versionMapper.findByStatus(status);
    }

    public List<DocumentVersionRecord> findByDocumentId(UUID documentId) {
        return versionMapper.findByDocumentId(documentId);
    }

    public Optional<DocumentVersionRecord> findLatestByDocumentId(UUID documentId) {
        return versionMapper.findLatestByDocumentId(documentId);
    }

    public boolean updateStatus(
            UUID id,
            DocumentVersionStatus expectedStatus,
            DocumentVersionStatus newStatus,
            String errorMessage) {
        return versionMapper.updateStatus(id, expectedStatus, newStatus, errorMessage) == 1;
    }
}
