/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.test.state.DummySwirldState;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.utility.ThrowingRunnable;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.SwirldStateManagerImpl;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class ConsensusRoundHandlerTests extends AbstractEventHandlerTests {

    private EventStreamManager<EventImpl> eventStreamManager;
    private QueueThread<ReservedSignedState> stateHashSignQueue;

    private ConsensusRoundHandler consensusRoundHandler;

    @Override
    @BeforeEach
    public void setup() {
        super.setup();
        eventStreamManager = mock(EventStreamManager.class);
        stateHashSignQueue = mock(QueueThread.class);
    }

    /**
     * Verify that the consensus handler thread does not make reconnect wait for it to drain the queue of
     * consensus rounds.
     */
    @RepeatedTest(10)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Reconnect should not wait for queue to be drained")
    void queueNotDrainedOnReconnect() {
        final SwirldStateManager swirldStateManager = mock(SwirldStateManager.class);

        // The maximum number of events drained to the QueueThread buffer at a time
        final long maxRoundsInBuffer = 100;
        final long sleepMillisPerRound = 10;

        // Tracks the number of events handled from the queue
        final AtomicInteger numEventsHandled = new AtomicInteger(0);

        // sleep for a little while to pretend to handle an event
        doAnswer((e) -> {
                    Thread.sleep(sleepMillisPerRound);
                    numEventsHandled.incrementAndGet();
                    return null;
                })
                .when(swirldStateManager)
                .handleConsensusRound(any(ConsensusRound.class));

        // the thread is not interruptable
        when(swirldStateManager.getStopBehavior()).thenReturn(Stoppable.StopBehavior.BLOCKING);

        final ExecutorService executor = Executors.newFixedThreadPool(1);

        // Set up a separate thread to invoke clear
        final Callable<Void> clear = (ThrowingRunnable) () -> consensusRoundHandler.clear();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        consensusRoundHandler = new ConsensusRoundHandler(
                platformContext,
                getStaticThreadManager(),
                selfId.getId(),
                settingsProvider,
                swirldStateManager,
                consensusHandlingMetrics,
                eventStreamManager,
                stateHashSignQueue,
                e -> {},
                () -> {},
                (round) -> {},
                null);

        final int numRounds = 500;
        final ConsensusRound round = mock(ConsensusRound.class);

        // Start the consensus handler and add events to the queue for it to handle
        consensusRoundHandler.start();
        for (int i = 0; i < numRounds; i++) {
            consensusRoundHandler.consensusRound(round);
        }

        // Make the separate thread invoke clear()
        final Future<Void> future = executor.submit(clear);

        // Wait up to the amount of time it would take for two full buffer of events to be handled. This entire time
        // shouldn't be needed, but it's a max time to allow for different systems and other programs running at the
        // same time. As long as this is less than the amount of time it takes to handle all the events in the queue,
        // the value is valid.
        final long maxMillisToWait = Math.round(maxRoundsInBuffer * (sleepMillisPerRound) * 2);

        // Wait for clear() to complete
        await().atMost(Duration.of(maxMillisToWait, ChronoUnit.MILLIS)).until(future::isDone);

        // Verify that no more than 2 buffers worth of events were handled
        assertTrue(
                numEventsHandled.get() < maxRoundsInBuffer * 2,
                "Consensus handler should not enter another doWork() cycle after prepareForReconnect() is called");

        assertEquals(0, consensusRoundHandler.getRoundsInQueue(), "Consensus queue should be empty");
    }

    /**
     * Tests that consensus events are passed to {@link EventStreamManager#addEvents(List)} exactly once.
     */
    @Test
    void testConsensusEventStream() {
        final SwirldState swirldState = new DummySwirldState();
        initConsensusHandler(swirldState);
        testEventStream(eventStreamManager, consensusRoundHandler::consensusRound);
    }

    /**
     * Verifies that {@link EventStreamManager#addEvents(List)} is called the desired number of times.
     *
     * @param eventStreamManager
     * 		the instance of {@link EventStreamManager} used by {@link ConsensusRoundHandler}
     * @param roundConsumer
     * 		the round consumer to test
     */
    private void testEventStream(
            final EventStreamManager<EventImpl> eventStreamManager, final Consumer<ConsensusRound> roundConsumer) {
        final List<EventImpl> events = createEvents(10, 10, true);
        final ConsensusRound round = mock(ConsensusRound.class);
        when(round.getConsensusEvents()).thenReturn(events);
        roundConsumer.accept(round);
        verify(eventStreamManager, times(1)).addEvents(events);
    }

    private void initConsensusHandler(final SwirldState swirldState) {
        final State state = new State();
        state.setSwirldState(swirldState);

        when(settingsProvider.getMaxEventQueueForCons()).thenReturn(500);

        final SwirldStateManager swirldStateManager = new SwirldStateManagerImpl(
                selfId,
                preConsensusSystemTransactionManager,
                postConsensusSystemTransactionManager,
                mock(SwirldStateMetrics.class),
                settingsProvider,
                () -> false,
                state);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        consensusRoundHandler = new ConsensusRoundHandler(
                platformContext,
                getStaticThreadManager(),
                selfId.getId(),
                settingsProvider,
                swirldStateManager,
                consensusHandlingMetrics,
                eventStreamManager,
                stateHashSignQueue,
                e -> {},
                () -> {},
                (round) -> {},
                null);
        consensusRoundHandler.start();
    }
}
