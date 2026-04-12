package com.myorg.lsf.saga;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "lsf.saga")
public class LsfSagaProperties {

    private boolean enabled = true;

    private SagaStoreMode store = SagaStoreMode.AUTO;

    private boolean observeDispatch = true;

    private boolean consumeMatchingEvents = true;

    private Duration defaultStepTimeout = Duration.ofMinutes(2);

    private Transport transport = new Transport();

    private TimeoutScanner timeoutScanner = new TimeoutScanner();

    private Jdbc jdbc = new Jdbc();

    @Data
    public static class Transport {
        private SagaTransportMode mode = SagaTransportMode.AUTO;
    }

    @Data
    public static class TimeoutScanner {
        private boolean enabled = true;
        private Duration pollInterval = Duration.ofSeconds(5);
        private int batchSize = 50;
    }

    @Data
    public static class Jdbc {
        private String table = "lsf_saga_instance";
        private SagaSchemaInitialization initializeSchema = SagaSchemaInitialization.EMBEDDED;
    }
}
