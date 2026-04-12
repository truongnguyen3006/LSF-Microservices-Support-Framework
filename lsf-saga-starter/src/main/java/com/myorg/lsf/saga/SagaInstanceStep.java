package com.myorg.lsf.saga;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstanceStep {

    private String name;
    private SagaStepStatus status;
    private Long startedAtMs;
    private Long completedAtMs;
    private Long timeoutAtMs;
    private String commandEventId;
    private String replyEventId;
    private String failureReason;
    private Long compensationStartedAtMs;
    private Long compensationCompletedAtMs;
    private Long compensationTimeoutAtMs;
    private String compensationCommandEventId;
    private String compensationReplyEventId;
    private String compensationFailureReason;

    public static SagaInstanceStep initial(String name) {
        return new SagaInstanceStep(
                name,
                SagaStepStatus.PENDING,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SagaInstanceStep copy() {
        return new SagaInstanceStep(
                name,
                status,
                startedAtMs,
                completedAtMs,
                timeoutAtMs,
                commandEventId,
                replyEventId,
                failureReason,
                compensationStartedAtMs,
                compensationCompletedAtMs,
                compensationTimeoutAtMs,
                compensationCommandEventId,
                compensationReplyEventId,
                compensationFailureReason
        );
    }
}
