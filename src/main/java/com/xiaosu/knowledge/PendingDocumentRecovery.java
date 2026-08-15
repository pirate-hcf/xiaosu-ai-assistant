package com.xiaosu.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.xiaosu.domain.DocumentVersionStatus;
import com.xiaosu.service.DocumentVersionService;

@Component
public class PendingDocumentRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingDocumentRecovery.class);

    private final DocumentVersionService versionService;
    private final DocumentIndexDispatcher dispatcher;

    public PendingDocumentRecovery(
            DocumentVersionService versionService,
            DocumentIndexDispatcher dispatcher) {
        this.versionService = versionService;
        this.dispatcher = dispatcher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingVersions() {
        try {
            versionService.findByStatus(DocumentVersionStatus.PENDING)
                    .forEach(version -> dispatcher.submit(version.id()));
        } catch (DataAccessException exception) {
            LOGGER.warn("Pending document recovery skipped because the database is unavailable");
        }
    }
}
