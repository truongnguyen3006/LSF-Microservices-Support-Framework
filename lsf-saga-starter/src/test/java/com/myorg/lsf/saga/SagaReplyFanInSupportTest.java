package com.myorg.lsf.saga;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SagaReplyFanInSupportTest {

    @Test
    void shouldTrackPendingRepliesUntilSessionCompletesSuccessfully() {
        SagaReplyFanInSession started = SagaReplyFanInSupport.start(
                "ORD-001",
                2,
                "evt-1",
                "req-1",
                1_000L,
                Duration.ofSeconds(30)
        );

        SagaReplyFanInUpdate firstUpdate = SagaReplyFanInSupport.apply(
                started,
                SagaReplyFanInSignal.successful(),
                2_000L,
                Duration.ofSeconds(30)
        );

        assertThat(firstUpdate.outcome()).isEqualTo(SagaReplyFanInOutcome.IN_PROGRESS);
        assertThat(firstUpdate.session().receivedReplies()).isEqualTo(1);
        assertThat(firstUpdate.session().pendingReplies()).isEqualTo(1);
        assertThat(firstUpdate.session().failed()).isFalse();

        SagaReplyFanInUpdate completed = SagaReplyFanInSupport.apply(
                firstUpdate.session(),
                SagaReplyFanInSignal.successful(),
                3_000L,
                Duration.ofSeconds(30)
        );

        assertThat(completed.outcome()).isEqualTo(SagaReplyFanInOutcome.COMPLETED_SUCCESS);
        assertThat(completed.session().receivedReplies()).isEqualTo(2);
        assertThat(completed.session().isComplete()).isTrue();
        assertThat(completed.session().failed()).isFalse();
    }

    @Test
    void shouldKeepFailureStateAndCompleteAsFailureAfterLastReply() {
        SagaReplyFanInSession started = SagaReplyFanInSupport.start(
                "ORD-002",
                3,
                "evt-2",
                "req-2",
                10_000L,
                Duration.ofSeconds(20)
        );

        SagaReplyFanInUpdate pendingFailure = SagaReplyFanInSupport.apply(
                started,
                SagaReplyFanInSignal.failure("inventory rejected"),
                11_000L,
                Duration.ofSeconds(20)
        );

        assertThat(pendingFailure.outcome()).isEqualTo(SagaReplyFanInOutcome.IN_PROGRESS);
        assertThat(pendingFailure.session().failed()).isTrue();
        assertThat(pendingFailure.session().failureReason()).isEqualTo("inventory rejected");

        SagaReplyFanInUpdate secondUpdate = SagaReplyFanInSupport.apply(
                pendingFailure.session(),
                SagaReplyFanInSignal.successful(),
                12_000L,
                Duration.ofSeconds(20)
        );
        SagaReplyFanInUpdate completed = SagaReplyFanInSupport.apply(
                secondUpdate.session(),
                SagaReplyFanInSignal.successful(),
                13_000L,
                Duration.ofSeconds(20)
        );

        assertThat(completed.outcome()).isEqualTo(SagaReplyFanInOutcome.COMPLETED_FAILURE);
        assertThat(completed.session().failed()).isTrue();
        assertThat(completed.session().failureReason()).isEqualTo("inventory rejected");
    }

    @Test
    void shouldIgnoreExpiredAndAlreadyCompletedSessions() {
        SagaReplyFanInSession expired = SagaReplyFanInSupport.start(
                "ORD-003",
                1,
                "evt-3",
                "req-3",
                100L,
                Duration.ofSeconds(5)
        );

        SagaReplyFanInUpdate expiredUpdate = SagaReplyFanInSupport.apply(
                expired,
                SagaReplyFanInSignal.successful(),
                5_100L,
                Duration.ofSeconds(5)
        );

        assertThat(expiredUpdate.outcome()).isEqualTo(SagaReplyFanInOutcome.EXPIRED);
        assertThat(expiredUpdate.shouldIgnore()).isTrue();

        SagaReplyFanInSession completed = SagaReplyFanInSupport.apply(
                SagaReplyFanInSupport.start("ORD-004", 1, "evt-4", "req-4", 1_000L, Duration.ofSeconds(5)),
                SagaReplyFanInSignal.successful(),
                2_000L,
                Duration.ofSeconds(5)
        ).session();

        SagaReplyFanInUpdate duplicateUpdate = SagaReplyFanInSupport.apply(
                completed,
                SagaReplyFanInSignal.successful(),
                2_500L,
                Duration.ofSeconds(5)
        );

        assertThat(duplicateUpdate.outcome()).isEqualTo(SagaReplyFanInOutcome.IGNORED_ALREADY_COMPLETE);
        assertThat(duplicateUpdate.shouldIgnore()).isTrue();
    }
}
