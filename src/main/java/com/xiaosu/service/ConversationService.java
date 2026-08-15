package com.xiaosu.service;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.xiaosu.domain.ConversationRecord;
import com.xiaosu.mapper.ConversationMapper;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;

    public ConversationService(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    public boolean insertIfAbsent(ConversationRecord conversation) {
        try {
            return conversationMapper.insert(conversation) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<ConversationRecord> findBySessionKey(String sessionKey) {
        return conversationMapper.findBySessionKey(sessionKey);
    }

    public long countBySessionKey(String sessionKey) {
        return conversationMapper.countBySessionKey(sessionKey);
    }
}
