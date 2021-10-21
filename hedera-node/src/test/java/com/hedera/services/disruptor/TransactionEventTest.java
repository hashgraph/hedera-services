package com.hedera.services.disruptor;

import com.hedera.services.utils.PlatformTxnAccessor;
import com.swirlds.common.SwirldDualState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith({ MockitoExtension.class })
class TransactionEventTest {
    @Mock PlatformTxnAccessor accessor;
    @Mock SwirldDualState dualState;

    @Test
    void gettersAndSetters() {
        TransactionEvent event = new TransactionEvent();

        Instant now = Instant.now();
        event.setSubmittingMember(123);
        event.setConsensusTime(now);
        event.setCreationTime(now);
        event.setErrored(true);
        event.setConsensus(true);
        event.setAccessor(accessor);
        event.setDualState(dualState);

        assertEquals(123, event.getSubmittingMember());
        assertEquals(now, event.getConsensusTime());
        assertEquals(now, event.getCreationTime());
        assertTrue(event.isErrored());
        assertTrue(event.isConsensus());
        assertEquals(accessor, event.getAccessor());
        assertEquals(dualState, event.getDualState());
    }

    @Test
    void clearWorks() {
        TransactionEvent event = new TransactionEvent();

        Instant now = Instant.now();
        event.setSubmittingMember(123);
        event.setConsensusTime(now);
        event.setCreationTime(now);
        event.setErrored(true);
        event.setConsensus(true);
        event.setAccessor(accessor);
        event.setDualState(dualState);

        event.clear();

        assertEquals(-1, event.getSubmittingMember());
        assertNull(event.getConsensusTime());
        assertNull(event.getCreationTime());
        assertFalse(event.isErrored());
        assertFalse(event.isConsensus());
        assertNull(event.getAccessor());
        assertNull(event.getDualState());
    }
}
