package com.xiaosu.mapper;

public interface DatabaseCleanupMapper {

    int deleteMessages();

    int deleteConversations();

    int deleteChunks();

    int clearActiveVersions();

    int deleteVersions();

    int deleteDocuments();

    int deleteSettings();

    long countDocuments();

    long countVersions();
}
