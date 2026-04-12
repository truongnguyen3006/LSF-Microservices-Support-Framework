package com.myorg.lsf.saga;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {

    private String sagaId;
    private String definitionName;
    private SagaStatus status;
    private SagaPhase phase;
    private Integer currentStepIndex;
    private Integer compensationStepIndex;
    private String currentStep;
    private String correlationId;
    private String requestId;
    private String causationId;
    private String lastEventId;
    private String failureReason;
    private JsonNode stateData;
    private List<SagaInstanceStep> steps = new ArrayList<>();
    private Long nextTimeoutAtMs;
    private Long createdAtMs;
    private Long updatedAtMs;
    private Long version;

    public SagaInstance copy() {
        List<SagaInstanceStep> copiedSteps = new ArrayList<>();
        for (SagaInstanceStep step : steps) {
            copiedSteps.add(step.copy());
        }
        return new SagaInstance(
                sagaId,
                definitionName,
                status,
                phase,
                currentStepIndex,
                compensationStepIndex,
                currentStep,
                correlationId,
                requestId,
                causationId,
                lastEventId,
                failureReason,
                stateData == null ? null : stateData.deepCopy(),
                copiedSteps,
                nextTimeoutAtMs,
                createdAtMs,
                updatedAtMs,
                version
        );
    }
}
