package com.swirlds.platform.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SignedStateReserverTest {

    @Test
    void basicTest() {
        final int numConsumers = 3;
        final SignedState signedState = new SignedState(
                new TestConfigBuilder(StateConfig.class).getOrCreateConfig().getConfigData(StateConfig.class),
                Mockito.mock(State.class),
                "create",
                false
        );
        final List<ValueReference<ReservedSignedState>> consumers = Stream
                .generate(ValueReference<ReservedSignedState>::new)
                .limit(numConsumers)
                .toList();
        final SignedStateReserver reserver = new SignedStateReserver();
        consumers.forEach(c -> reserver.addConsumer(c::setValue));

        final ReservedSignedState state = signedState.reserve("main");
        assertFalse(state.isClosed(), "we just reserved it, so it should not be closed");
        assertEquals(1, signedState.getReservationCount(), "the reservation count should be 1");
        reserver.accept(state);
        assertTrue(state.isClosed(), "the reserver should have closed our reservation");
        consumers.forEach(c -> assertFalse(c.getValue().isClosed(), "the consumer should not have closed its state"));
        assertEquals(numConsumers, signedState.getReservationCount(),
                "there should be a reservation for each consumer");
        consumers.forEach(c -> c.getValue().close());
    }
}