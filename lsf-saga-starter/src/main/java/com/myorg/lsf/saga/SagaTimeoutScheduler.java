package com.myorg.lsf.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class SagaTimeoutScheduler {

    private final LsfSagaOrchestrator orchestrator;

    @Scheduled(
            initialDelayString = "#{@lsfSagaTimeoutScannerDelayMs}",
            fixedDelayString = "#{@lsfSagaTimeoutScannerDelayMs}"
    )
    public void sweepTimeouts() {
        orchestrator.triggerTimeouts();
    }
}
