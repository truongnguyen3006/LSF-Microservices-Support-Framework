package com.myorg.lsf.kafka.admin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "lsf.kafka.admin")
public class LsfKafkaAdminProperties {

    private boolean enabled = false;

    private String basePath = "/lsf/kafka";

    private int defaultLimit = 25;
    private int maxLimit = 200;

    private boolean allowReplay = true;

    private String dlqSuffix = ".DLQ";

    private Duration pollTimeout = Duration.ofSeconds(2);
}
