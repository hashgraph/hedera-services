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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.test.fixtures.state.DummySwirldState;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

        final ExecutorService executor = Executors.newFixedThreadPool(1);

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        consensusRoundHandler = new ConsensusRoundHandler(
                platformContext,
                swirldStateManager,
                stateHashSignQueue,
                e -> {},
                mock(StatusActionSubmitter.class),
                (round) -> {},
                new BasicSoftwareVersion(1));

        final int numRounds = 500;
        final ConsensusRound round = mock(ConsensusRound.class);

        for (int i = 0; i < numRounds; i++) {
            consensusRoundHandler.handleConsensusRound(round);
        }

        // Verify that no more than 2 buffers worth of events were handled
        assertTrue(
                numEventsHandled.get() < maxRoundsInBuffer * 2,
                "Consensus handler should not enter another doWork() cycle after prepareForReconnect() is called");
    }

    /**
     * Tests that consensus events are passed to {@link EventStreamManager#addEvents(List)} exactly once.
     */
    @Test
    void testConsensusEventStream() {
        final SwirldState swirldState = new DummySwirldState();
        initConsensusHandler(swirldState);
        testEventStream(eventStreamManager, consensusRoundHandler::handleConsensusRound);
    }

    /**
     * Verifies that {@link EventStreamManager#addEvents(List)} is called the desired number of times.
     *
     * @param eventStreamManager the instance of {@link EventStreamManager} used by {@link ConsensusRoundHandler}
     * @param roundConsumer      the round consumer to test
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

        final AddressBook addressBook = new RandomAddressBookGenerator().build();

        final PlatformState platformState = mock(PlatformState.class);
        when(platformState.getClassId()).thenReturn(PlatformState.CLASS_ID);
        when(platformState.copy()).thenReturn(platformState);
        when(platformState.getAddressBook()).thenReturn(addressBook);

        state.setPlatformState(platformState);

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.MAX_EVENT_QUEUE_FOR_CONS, 500)
                .getOrCreateConfig();
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        final SwirldStateManager swirldStateManager = new SwirldStateManager(
                platformContext,
                addressBook,
                selfId,
                consensusSystemTransactionManager,
                mock(SwirldStateMetrics.class),
                mock(StatusActionSubmitter.class),
                state,
                new BasicSoftwareVersion(1));

        consensusRoundHandler = new ConsensusRoundHandler(
                platformContext,
                swirldStateManager,
                stateHashSignQueue,
                e -> {},
                mock(StatusActionSubmitter.class),
                (round) -> {},
                new BasicSoftwareVersion(1));
    }
}
