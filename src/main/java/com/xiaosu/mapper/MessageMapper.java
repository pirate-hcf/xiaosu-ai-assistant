package com.xiaosu.mapper;

import java.util.Optional;

import com.xiaosu.domain.MessageRecord;

public interface MessageMapper {

    int insert(MessageRecord message);

    Optional<MessageRecord> findByPlatformMessageId(String platformMessageId);

    long countByPlatformMessageId(String platformMessageId);
}
