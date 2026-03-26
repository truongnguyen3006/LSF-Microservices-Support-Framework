package com.myorg.lsf.outbox.admin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lsf.outbox.admin")
public class LsfOutboxAdminProperties {

    private boolean enabled = false;

    /** Base path for endpoints. */
    private String basePath = "/lsf/outbox";

    private int defaultLimit = 50;
    private int maxLimit = 200;

    /** Delete endpoint OFF by default (an toàn). */
    private boolean allowDelete = false;
    private boolean allowRetry = true;

}