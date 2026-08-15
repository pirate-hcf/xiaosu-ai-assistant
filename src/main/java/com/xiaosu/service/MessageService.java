package com.xiaosu.service;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.xiaosu.domain.MessageRecord;
import com.xiaosu.mapper.MessageMapper;

@Service
public class MessageService {

    private final MessageMapper messageMapper;

    public MessageService(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public boolean insertIfAbsent(MessageRecord message) {
        try {
            return messageMapper.insert(message) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<MessageRecord> findByPlatformMessageId(String platformMessageId) {
        return messageMapper.findByPlatformMessageId(platformMessageId);
    }

    public long countByPlatformMessageId(String platformMessageId) {
        return messageMapper.countByPlatformMessageId(platformMessageId);
    }
}
