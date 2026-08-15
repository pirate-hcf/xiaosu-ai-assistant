package com.xiaosu.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.xiaosu.persistence.model.DocumentVersionStatus;
import com.xiaosu.persistence.repository.DocumentVersionRepository;

@Component
public class PendingDocumentRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingDocumentRecovery.class);

    private final DocumentVersionRepository versionRepository;
    private final DocumentIndexDispatcher dispatcher;

    public PendingDocumentRecovery(
            DocumentVersionRepository versionRepository,
            DocumentIndexDispatcher dispatcher) {
        this.versionRepository = versionRepository;
        this.dispatcher = dispatcher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingVersions() {
        try {
            versionRepository.findByStatus(DocumentVersionStatus.PENDING)
                    .forEach(version -> dispatcher.submit(version.id()));
        } catch (DataAccessException exception) {
            LOGGER.warn("Pending document recovery skipped because the database is unavailable");
        }
    }
}
