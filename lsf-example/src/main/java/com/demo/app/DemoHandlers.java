package com.demo.app;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfEventHandler;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContext;
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoHandlers {
    private final Environment springEnv;
    private final MeterRegistry meterRegistry;
    private final ApplicationContext applicationContext;


    @PostConstruct
    void initMetrics() {
        // pre-register để /actuator/metrics/lsf.it.handled không bao giờ 404
        meterRegistry.counter("lsf.it.handled");
    }

    @LsfEventHandler(value = TestController.DEMO_EVENT_TYPE, payload = HelloPayload.class)
    public void onHello(EventEnvelope envelope, HelloPayload payload) {
        String port = springEnv.getProperty("server.port");
        String eventId = envelope.getEventId();

        if (eventId != null && eventId.startsWith("FAIL_")) {
            log.warn("FORCED FAIL on port={} eventId={} hello={}", port, eventId, payload.getHello());
            throw new RuntimeException("Simulated handler failure for eventId=" + eventId);
        }

        log.info("HANDLED on port={} eventId={} hello={}", port, eventId, payload.getHello());
        markHandledForIT();

        // count "handled successfully"
        meterRegistry.counter("lsf.it.handled").increment();
    }
    private void markHandledForIT() {
        try {
            if (!applicationContext.containsBean("testHandlerProbe")) return;
            Object probe = applicationContext.getBean("testHandlerProbe");
            probe.getClass().getMethod("markHandled").invoke(probe);
        } catch (Exception ignored) {
            // production không có bean này -> no-op
        }
    }


}
