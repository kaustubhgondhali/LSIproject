package com.lordsai.lsi.automation.service;

import com.lordsai.lsi.automation.entity.AcadCommunicationLog;
import com.lordsai.lsi.automation.repository.AcadCommunicationLogRepository;
import com.lordsai.lsi.entity.enums.CommunicationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Works through one queued Automation Admin batch in the background, in controlled batches with
 * a pause between them so the mail / WhatsApp provider is never flooded. Each row is delivered
 * in its own transaction; a failure is recorded on that row and the batch continues.
 */
@Component
public class AcadCommunicationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AcadCommunicationDispatcher.class);

    private final AcadCommunicationLogRepository logRepository;
    private final AcadCommunicationSender sender;
    private final int batchSize;
    private final long batchPauseMs;

    public AcadCommunicationDispatcher(AcadCommunicationLogRepository logRepository,
                                       AcadCommunicationSender sender,
                                       @Value("${lsi.communication.batch-size:25}") int batchSize,
                                       @Value("${lsi.communication.batch-pause-ms:2000}") long batchPauseMs) {
        this.logRepository = logRepository;
        this.sender = sender;
        this.batchSize = Math.max(1, batchSize);
        this.batchPauseMs = Math.max(0, batchPauseMs);
    }

    /** Runs on the single-threaded communication executor; returns immediately to the caller. */
    @Async("communicationExecutor")
    public void dispatchAsync(String batchRef) {
        dispatch(batchRef);
    }

    /** Same processing, inline (used when async is disabled, e.g. in tests). */
    public void dispatch(String batchRef) {
        List<AcadCommunicationLog> pending = logRepository.findByBatchRefOrderByIdAsc(batchRef).stream()
                .filter(l -> l.getStatus() == CommunicationStatus.PENDING).toList();
        log.info("[AUTOMATION] Batch {}: {} message(s) queued, batch size {}", batchRef, pending.size(), batchSize);
        int sent = 0;
        int failed = 0;
        for (int i = 0; i < pending.size(); i++) {
            if (i > 0 && i % batchSize == 0 && batchPauseMs > 0) {
                try {
                    Thread.sleep(batchPauseMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[AUTOMATION] Batch {} interrupted after {} message(s)", batchRef, i);
                    return;
                }
            }
            AcadCommunicationLog result = sender.sendOne(pending.get(i).getId());
            if (result.getStatus() == CommunicationStatus.SENT) {
                sent++;
            } else {
                failed++;
            }
        }
        log.info("[AUTOMATION] Batch {} finished: {} sent, {} failed", batchRef, sent, failed);
    }
}
