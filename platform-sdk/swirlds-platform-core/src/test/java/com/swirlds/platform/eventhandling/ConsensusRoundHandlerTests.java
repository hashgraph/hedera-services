/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.base.function.CheckedConsumer;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.SignedStateGarbageCollector;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsensusRoundHandler}.
 */
class ConsensusRoundHandlerTests {
    private static ConsensusRound mockConsensusRound(
            @NonNull final EventImpl keystoneEvent, @NonNull final List<EventImpl> events, final long roundNumber) {
        final ConsensusRound consensusRound = mock(ConsensusRound.class);
        when(consensusRound.getConsensusEvents()).thenReturn(events);
        when(consensusRound.getConsensusTimestamp())
                .thenReturn(Time.getCurrent().now());
        when(consensusRound.getKeystoneEvent()).thenReturn(keystoneEvent);
        when(consensusRound.getRoundNum()).thenReturn(roundNumber);
        when(consensusRound.isEmpty()).thenReturn(events.isEmpty());

        return consensusRound;
    }

    private static EventImpl mockEvent() throws InterruptedException {
        final RunningHash runningHash = mock(RunningHash.class);
        final Hash hash = mock(Hash.class);
        final StandardFuture<Hash> futureHash = mock(StandardFuture.class);
        when(futureHash.getAndRethrow()).thenReturn(hash);
        when(runningHash.getFutureHash()).thenReturn(futureHash);
        final GossipEvent gossipEvent = mock(GossipEvent.class);
        final EventImpl outputEvent = mock(EventImpl.class);
        when(outputEvent.getRunningHash()).thenReturn(runningHash);
        when(outputEvent.getBaseEvent()).thenReturn(gossipEvent);

        return outputEvent;
    }

    private static SwirldStateManager mockSwirldStateManager(@NonNull final PlatformState platformState) {
        final State consensusState = mock(State.class);
        final State stateForSigning = mock(State.class);
        when(consensusState.getPlatformState()).thenReturn(platformState);
        final SwirldStateManager swirldStateManager = mock(SwirldStateManager.class);
        when(swirldStateManager.getConsensusState()).thenReturn(consensusState);
        when(swirldStateManager.getStateForSigning()).thenReturn(stateForSigning);

        return swirldStateManager;
    }

    @Test
    @DisplayName("Normal operation")
    void normalOperation() throws InterruptedException {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final PlatformState platformState = mock(PlatformState.class);
        final SwirldStateManager swirldStateManager = mockSwirldStateManager(platformState);

        final CheckedConsumer<GossipEvent, InterruptedException> waitForEventDurability = mock(CheckedConsumer.class);
        final StatusActionSubmitter statusActionSubmitter = mock(StatusActionSubmitter.class);

        final ConsensusRoundHandler consensusRoundHandler = new ConsensusRoundHandler(
                platformContext,
                swirldStateManager,
                mock(SignedStateGarbageCollector.class),
                waitForEventDurability,
                statusActionSubmitter,
                mock(SoftwareVersion.class));

        final EventImpl keystoneEvent = mockEvent();
        final List<EventImpl> events = List.of(mockEvent(), mockEvent(), mockEvent());

        final long consensusRoundNumber = 5L;
        final ConsensusRound consensusRound = mockConsensusRound(keystoneEvent, events, consensusRoundNumber);

        final StateAndRound handlerOutput = consensusRoundHandler.handleConsensusRound(consensusRound);
        assertNotEquals(null, handlerOutput, "new state should have been created");
        assertEquals(
                1,
                handlerOutput.reservedSignedState().get().getReservationCount(),
                "state should be returned with a reservation");

        for (final EventImpl event : events) {
            verify(event).consensusReached();
        }
        verify(statusActionSubmitter, never()).submitStatusAction(any(FreezePeriodEnteredAction.class));
        verify(waitForEventDurability).accept(keystoneEvent.getBaseEvent());
        verify(swirldStateManager).handleConsensusRound(consensusRound);
        verify(swirldStateManager, never()).savedStateInFreezePeriod();
        verify(platformState)
                .setRunningEventHash(
                        events.getLast().getRunningHash().getFutureHash().getAndRethrow());
    }

    @Test
    @DisplayName("Round in freeze period")
    void freezeHandling() throws InterruptedException {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final PlatformState platformState = mock(PlatformState.class);
        final SwirldStateManager swirldStateManager = mockSwirldStateManager(platformState);
        when(swirldStateManager.isInFreezePeriod(any())).thenReturn(true);

        final CheckedConsumer<GossipEvent, InterruptedException> waitForEventDurability = mock(CheckedConsumer.class);
        final StatusActionSubmitter statusActionSubmitter = mock(StatusActionSubmitter.class);

        final ConsensusRoundHandler consensusRoundHandler = new ConsensusRoundHandler(
                platformContext,
                swirldStateManager,
                mock(SignedStateGarbageCollector.class),
                waitForEventDurability,
                statusActionSubmitter,
                mock(SoftwareVersion.class));

        final EventImpl keystoneEvent = mockEvent();
        final List<EventImpl> events = List.of(mockEvent(), mockEvent(), mockEvent());

        final long consensusRoundNumber = 5L;
        final ConsensusRound consensusRound = mockConsensusRound(keystoneEvent, events, consensusRoundNumber);

        final StateAndRound handlerOutput = consensusRoundHandler.handleConsensusRound(consensusRound);
        assertNotEquals(null, handlerOutput, "new state should have been created");
        assertEquals(
                1,
                handlerOutput.reservedSignedState().get().getReservationCount(),
                "state should be returned with a reservation");

        for (final EventImpl event : events) {
            verify(event, times(1)).consensusReached();
        }
        verify(statusActionSubmitter).submitStatusAction(any(FreezePeriodEnteredAction.class));
        verify(waitForEventDurability).accept(keystoneEvent.getBaseEvent());
        verify(swirldStateManager).handleConsensusRound(consensusRound);
        verify(swirldStateManager).savedStateInFreezePeriod();
        verify(platformState)
                .setRunningEventHash(
                        events.getLast().getRunningHash().getFutureHash().getAndRethrow());

        final ConsensusRound postFreezeConsensusRound = mockConsensusRound(keystoneEvent, events, consensusRoundNumber);
        final StateAndRound postFreezeOutput = consensusRoundHandler.handleConsensusRound(postFreezeConsensusRound);
        assertNull(postFreezeOutput, "no state should be created after freeze period");

        // these methods were called once from the first round, and shouldn't have been called again from the second
        for (final EventImpl event : events) {
            verify(event).consensusReached();
        }
        verify(statusActionSubmitter).submitStatusAction(any(FreezePeriodEnteredAction.class));
        verify(waitForEventDurability).accept(keystoneEvent.getBaseEvent());
        verify(swirldStateManager).handleConsensusRound(consensusRound);
        verify(swirldStateManager).savedStateInFreezePeriod();
        verify(platformState)
                .setRunningEventHash(
                        events.getLast().getRunningHash().getFutureHash().getAndRethrow());
    }
}
