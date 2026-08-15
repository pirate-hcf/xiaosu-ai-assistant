package com.xiaosu.mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.xiaosu.domain.DocumentChunkRecord;

public interface DocumentChunkMapper {

    int insert(DocumentChunkRecord chunk);

    Optional<DocumentChunkRecord> findById(UUID id);

    List<DocumentChunkRecord> findByVersionId(UUID versionId);

    int deleteByVersionId(UUID versionId);
}
