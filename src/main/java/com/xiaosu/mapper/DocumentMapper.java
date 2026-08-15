package com.xiaosu.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.xiaosu.domain.DocumentRecord;

public interface DocumentMapper {

    int insert(DocumentRecord document);

    Optional<DocumentRecord> findById(UUID id);

    Optional<DocumentRecord> findByCanonicalName(String canonicalName);

    List<DocumentRecord> findAllNotDeleted();

    int setActiveVersion(
            @Param("documentId") UUID documentId,
            @Param("versionId") UUID versionId,
            @Param("updatedAt") Instant updatedAt);

    int markDeleted(@Param("documentId") UUID documentId, @Param("deletedAt") Instant deletedAt);
}
