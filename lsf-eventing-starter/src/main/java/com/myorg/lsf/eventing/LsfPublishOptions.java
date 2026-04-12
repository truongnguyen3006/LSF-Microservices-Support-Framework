package com.myorg.lsf.eventing;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class LsfPublishOptions {

    private final String correlationId;
    private final String causationId;
    private final String requestId;
    private final String producer;
}
