/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.test.event.preconsensus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.wiring.wires.output.StandardOutputWire;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link PcesReplayer} class
 */
@DisplayName("PcesReplayer Tests")
class PcesReplayerTests {

    @Test
    @DisplayName("Test standard operation")
    void testStandardOperation() {
        final FakeTime time = new FakeTime();

        final StandardOutputWire<PlatformEvent> eventOutputWire = mock(StandardOutputWire.class);
        final AtomicInteger eventOutputCount = new AtomicInteger(0);

        // whenever an event is forwarded to the output wire, increment the count
        doAnswer(invocation -> {
                    eventOutputCount.incrementAndGet();
                    return null;
                })
                .when(eventOutputWire)
                .forward(any());

        final AtomicBoolean flushIntakeCalled = new AtomicBoolean(false);
        final Runnable flushIntake = () -> flushIntakeCalled.set(true);

        final AtomicBoolean flushTransactionHandlingCalled = new AtomicBoolean(false);
        final Runnable flushTransactionHandling = () -> flushTransactionHandlingCalled.set(true);

        final ReservedSignedState latestImmutableState = mock(ReservedSignedState.class);
        final SignedState signedState = mock(SignedState.class);
        when(latestImmutableState.get()).thenReturn(signedState);

        final Supplier<ReservedSignedState> latestImmutableStateSupplier = () -> latestImmutableState;

        final PcesReplayer replayer = new PcesReplayer(
                time, eventOutputWire, flushIntake, flushTransactionHandling, latestImmutableStateSupplier, () -> true);

        final List<PlatformEvent> events = new ArrayList<>();
        final int eventCount = 100;

        for (int i = 0; i < eventCount; i++) {
            final PlatformEvent event = new TestingEventBuilder(Randotron.create())
                    .setAppTransactionCount(0)
                    .setSystemTransactionCount(0)
                    .build();

            events.add(event);
        }

        final Iterator<PlatformEvent> eventIterator = events.iterator();
        final IOIterator<PlatformEvent> ioIterator = new IOIterator<>() {
            @Override
            public boolean hasNext() {
                return eventIterator.hasNext();
            }

            @Override
            public PlatformEvent next() {
                return eventIterator.next();
            }
        };

        replayer.replayPces(ioIterator);

        assertEquals(eventCount, eventOutputCount.get());
        assertTrue(flushIntakeCalled.get());
        assertTrue(flushTransactionHandlingCalled.get());
    }
}
