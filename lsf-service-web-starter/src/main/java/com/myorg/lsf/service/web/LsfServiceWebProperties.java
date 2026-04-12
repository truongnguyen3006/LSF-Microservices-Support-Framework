package com.myorg.lsf.service.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lsf.service.web")
public class LsfServiceWebProperties {

    private boolean enabled = true;

    private boolean generateCorrelationId = true;

    private boolean generateRequestId = true;

    private boolean echoHeaders = true;
}
