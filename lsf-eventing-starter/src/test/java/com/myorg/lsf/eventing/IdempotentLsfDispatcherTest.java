package com.myorg.lsf.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.context.LsfDispatchOutcome;
import com.myorg.lsf.eventing.idempotency.IdempotencyStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentLsfDispatcherTest {

    private final EventEnvelope envelope = EventEnvelope.builder()
            .eventId("evt-1")
            .eventType("orders.created.v1")
            .payload(new ObjectMapper().createObjectNode().put("orderId", "ORD-1"))
            .build();

    @AfterEach
    void tearDown() {
        LsfDispatchOutcome.clear();
    }

    @Test
    void shouldMarkDuplicateAndSkipDelegateWhenLeaseSaysDuplicate() {
        AtomicBoolean called = new AtomicBoolean(false);
        RecordingStore store = new RecordingStore(IdempotencyStore.Lease.duplicate());
        IdempotentLsfDispatcher dispatcher = new IdempotentLsfDispatcher(env -> called.set(true), store);

        dispatcher.dispatch(envelope);

        assertThat(called).isFalse();
        assertThat(LsfDispatchOutcome.consume()).isEqualTo(LsfDispatchOutcome.DUPLICATE);
        assertThat(store.markDoneCalled).isFalse();
        assertThat(store.releaseCalled).isFalse();
    }

    @Test
    void shouldReleaseLeaseAndRethrowWhenDelegateFails() {
        RecordingStore store = new RecordingStore(IdempotencyStore.Lease.acquired("lease-1"));
        IdempotentLsfDispatcher dispatcher = new IdempotentLsfDispatcher(env -> {
            throw new IllegalStateException("handler failed");
        }, store);

        assertThatThrownBy(() -> dispatcher.dispatch(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("handler failed");

        assertThat(store.releaseCalled).isTrue();
        assertThat(store.releasedEventId).isEqualTo("evt-1");
        assertThat(store.releasedToken).isEqualTo("lease-1");
        assertThat(store.markDoneCalled).isFalse();
    }

    static class RecordingStore implements IdempotencyStore {

        private final Lease lease;
        private boolean markDoneCalled;
        private boolean releaseCalled;
        private String releasedEventId;
        private String releasedToken;

        RecordingStore(Lease lease) {
            this.lease = lease;
        }

        @Override
        public Lease tryBeginProcessing(String eventId) {
            return lease;
        }

        @Override
        public void markDone(String eventId, String token) {
            this.markDoneCalled = true;
        }

        @Override
        public void releaseProcessing(String eventId, String token) {
            this.releaseCalled = true;
            this.releasedEventId = eventId;
            this.releasedToken = token;
        }
    }
}
