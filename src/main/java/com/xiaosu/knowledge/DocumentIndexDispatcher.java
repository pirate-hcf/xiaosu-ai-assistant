package com.xiaosu.knowledge;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class DocumentIndexDispatcher {

    private final DocumentIndexService documentIndexService;

    public DocumentIndexDispatcher(DocumentIndexService documentIndexService) {
        this.documentIndexService = documentIndexService;
    }

    public void submit(UUID versionId) {
        documentIndexService.index(versionId);
    }
}
