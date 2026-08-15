package com.xiaosu.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.xiaosu.domain.DocumentChunkRecord;
import com.xiaosu.domain.DuplicateRecordException;
import com.xiaosu.mapper.DocumentChunkMapper;

@Service
public class DocumentChunkService {

    private final DocumentChunkMapper chunkMapper;

    public DocumentChunkService(DocumentChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    public void insert(DocumentChunkRecord chunk) {
        try {
            chunkMapper.insert(chunk);
        } catch (DuplicateKeyException exception) {
            String key = chunk.versionId() + ":" + chunk.chunkNo();
            throw new DuplicateRecordException("document chunk", key, exception);
        }
    }

    public Optional<DocumentChunkRecord> findById(UUID id) {
        return chunkMapper.findById(id);
    }

    public List<DocumentChunkRecord> findByVersionId(UUID versionId) {
        return chunkMapper.findByVersionId(versionId);
    }

    public int deleteByVersionId(UUID versionId) {
        return chunkMapper.deleteByVersionId(versionId);
    }
}
