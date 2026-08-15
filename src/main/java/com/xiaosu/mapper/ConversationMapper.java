package com.xiaosu.mapper;

import java.util.Optional;

import com.xiaosu.domain.ConversationRecord;

public interface ConversationMapper {

    int insert(ConversationRecord conversation);

    Optional<ConversationRecord> findBySessionKey(String sessionKey);

    long countBySessionKey(String sessionKey);
}
