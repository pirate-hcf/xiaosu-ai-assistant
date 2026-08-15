package com.xiaosu.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.xiaosu.domain.DocumentRecord;
import com.xiaosu.domain.DuplicateRecordException;
import com.xiaosu.mapper.DocumentMapper;

@Service
public class DocumentService {

    private final DocumentMapper documentMapper;

    public DocumentService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public void insert(DocumentRecord document) {
        try {
            documentMapper.insert(document);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateRecordException("document", document.canonicalName(), exception);
        }
    }

    public Optional<DocumentRecord> findById(UUID id) {
        return documentMapper.findById(id);
    }

    public Optional<DocumentRecord> findByCanonicalName(String canonicalName) {
        return documentMapper.findByCanonicalName(canonicalName);
    }

    public List<DocumentRecord> findAllNotDeleted() {
        return documentMapper.findAllNotDeleted();
    }

    public boolean setActiveVersion(UUID documentId, UUID versionId, Instant updatedAt) {
        return documentMapper.setActiveVersion(documentId, versionId, updatedAt) == 1;
    }

    public boolean markDeleted(UUID documentId, Instant deletedAt) {
        return documentMapper.markDeleted(documentId, deletedAt) == 1;
    }
}
