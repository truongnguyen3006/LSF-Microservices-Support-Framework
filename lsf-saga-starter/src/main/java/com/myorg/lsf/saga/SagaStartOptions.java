package com.myorg.lsf.saga;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class SagaStartOptions {

    private final String correlationId;
    private final String causationId;
    private final String requestId;
}
