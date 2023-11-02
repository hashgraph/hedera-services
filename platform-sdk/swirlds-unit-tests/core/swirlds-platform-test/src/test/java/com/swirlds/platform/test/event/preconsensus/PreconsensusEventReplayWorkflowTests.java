/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;
import static com.swirlds.platform.event.preconsensus.PreconsensusEventReplayWorkflow.replayPreconsensusEvents;
import static com.swirlds.platform.test.event.preconsensus.AsyncPreconsensusEventWriterTests.buildGraphGenerator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PreconsensusEventFileManager;
import com.swirlds.platform.event.preconsensus.PreconsensusEventMultiFileIterator;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.event.validation.EventValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PreconsensusEventReplayWorkflow Tests")
class PreconsensusEventReplayWorkflowTests {

    private enum TestPhase {
        REPLAY_EVENTS,
        FLUSH_INTAKE_QUEUE,
        FLUSH_CONSENSUS_ROUND_HANDLER,
        FLUSH_STATE_HASH_SIGN_QUEUE,
        BEGIN_STREAMING_NEW_EVENTS,
        TEST_FINISHED
    }

    @Test
    @DisplayName("Test Replay Basic Workflow")
    void testBasicReplayWorkflow() throws InterruptedException {
        final Random random = getRandomPrintSeed();

        final AtomicReference<TestPhase> phase = new AtomicReference<>(TestPhase.REPLAY_EVENTS);
        final long minimumGenerationNonAncient = random.nextLong(1, 1000);

        final List<GossipEvent> events = new ArrayList<>();
        final StandardGraphGenerator graphGenerator = buildGraphGenerator(random);
        final int eventCount = 1000;
        for (int i = 0; i < eventCount; i++) {
            final EventImpl event = graphGenerator.generateEvent();
            events.add(event.getBaseEvent());
        }
        final Iterator<GossipEvent> eventIterator = events.iterator();

        final PreconsensusEventFileManager preconsensusEventFileManager = mock(PreconsensusEventFileManager.class);
        when(preconsensusEventFileManager.getEventIterator(anyLong())).thenAnswer(invocation -> {
            final PreconsensusEventMultiFileIterator it = mock(PreconsensusEventMultiFileIterator.class);
            when(it.hasNext()).thenAnswer(invocation2 -> eventIterator.hasNext());
            when(it.next()).thenAnswer(invocation2 -> eventIterator.next());
            return it;
        });

        final EventValidator eventValidator = mock(EventValidator.class);
        final AtomicInteger nextIndex = new AtomicInteger(0);
        doAnswer(invocation -> {
                    if (phase.get() != TestPhase.REPLAY_EVENTS) {
                        fail("validateEvent should not be called until after replaying events");
                    }

                    final GossipEvent event = invocation.getArgument(0);
                    assertNotNull(event.getHashedData().getHash());

                    final int index = nextIndex.getAndIncrement();
                    final GossipEvent expectedEvent = events.get(index);

                    assertSame(event.getHashedData(), expectedEvent.getHashedData());

                    if (nextIndex.get() >= eventCount) {
                        phase.set(TestPhase.FLUSH_INTAKE_QUEUE);
                    }

                    return null;
                })
                .when(eventValidator)
                .validateEvent(any());

        final QueueThread<GossipEvent> eventIntakeTaskQueueThread = mock(QueueThread.class);
        doAnswer(invocation -> {
                    assertEquals(TestPhase.FLUSH_INTAKE_QUEUE, phase.get());
                    phase.set(TestPhase.FLUSH_CONSENSUS_ROUND_HANDLER);
                    return null;
                })
                .when(eventIntakeTaskQueueThread)
                .waitUntilNotBusy();

        final ConsensusRoundHandler consensusRoundHandler = mock(ConsensusRoundHandler.class);
        doAnswer(invocation -> {
                    assertEquals(TestPhase.FLUSH_CONSENSUS_ROUND_HANDLER, phase.get());
                    phase.set(TestPhase.FLUSH_STATE_HASH_SIGN_QUEUE);
                    return null;
                })
                .when(consensusRoundHandler)
                .waitUntilNotBusy();

        final QueueThread<ReservedSignedState> stateHashSignQueue = mock(QueueThread.class);
        doAnswer(invocation -> {
                    assertEquals(TestPhase.FLUSH_STATE_HASH_SIGN_QUEUE, phase.get());
                    phase.set(TestPhase.BEGIN_STREAMING_NEW_EVENTS);
                    return null;
                })
                .when(stateHashSignQueue)
                .waitUntilNotBusy();

        final PreconsensusEventWriter preconsensusEventWriter = mock(PreconsensusEventWriter.class);
        doAnswer(invocation -> {
                    assertEquals(TestPhase.BEGIN_STREAMING_NEW_EVENTS, phase.get());
                    phase.set(TestPhase.TEST_FINISHED);
                    return null;
                })
                .when(preconsensusEventWriter)
                .beginStreamingNewEvents();

        final StateManagementComponent stateManagementComponent = mock(StateManagementComponent.class);
        final ReservedSignedState latestImmutableState = mock(ReservedSignedState.class);
        when(latestImmutableState.isNull()).thenReturn(false);
        final SignedState signedState = mock(SignedState.class);
        when(signedState.getRound()).thenReturn(random.nextLong(1, 10000));
        when(signedState.getConsensusTimestamp()).thenReturn(randomInstant(random));
        when(latestImmutableState.get()).thenReturn(signedState);
        when(stateManagementComponent.getLatestImmutableState(any())).thenReturn(latestImmutableState);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        replayPreconsensusEvents(
                platformContext,
                AdHocThreadManager.getStaticThreadManager(),
                Time.getCurrent(),
                preconsensusEventFileManager,
                preconsensusEventWriter,
                eventValidator,
                eventIntakeTaskQueueThread,
                consensusRoundHandler,
                stateHashSignQueue,
                stateManagementComponent,
                minimumGenerationNonAncient);

        assertEquals(TestPhase.TEST_FINISHED, phase.get());
    }
}
