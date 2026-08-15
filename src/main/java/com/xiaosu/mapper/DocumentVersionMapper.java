package com.xiaosu.mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.xiaosu.domain.DocumentVersionRecord;
import com.xiaosu.domain.DocumentVersionStatus;

public interface DocumentVersionMapper {

    int insert(DocumentVersionRecord version);

    Optional<DocumentVersionRecord> findById(UUID id);

    Optional<DocumentVersionRecord> findByIdForUpdate(UUID id);

    List<DocumentVersionRecord> findByStatus(DocumentVersionStatus status);

    List<DocumentVersionRecord> findByDocumentId(UUID documentId);

    Optional<DocumentVersionRecord> findLatestByDocumentId(UUID documentId);

    int updateStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") DocumentVersionStatus expectedStatus,
            @Param("newStatus") DocumentVersionStatus newStatus,
            @Param("errorMessage") String errorMessage);
}
