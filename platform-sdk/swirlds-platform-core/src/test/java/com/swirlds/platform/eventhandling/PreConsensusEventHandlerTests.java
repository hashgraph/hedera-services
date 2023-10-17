/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.threading.utility.ThrowingRunnable;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class PreConsensusEventHandlerTests extends AbstractEventHandlerTests {

    private PreConsensusEventHandler preConsensusEventHandler;
    private SwirldStateManager swirldStateManager;

    @Override
    @BeforeEach
    public void setup() {
        super.setup();
    }

    /**
     * Verify that the pre-consensus handler thread does not make reconnect wait for it to drain the queue of
     * pre-consensus events.
     */
    @RepeatedTest(10)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Reconnect should not wait for queue to be drained")
    void queueNotDrainedOnReconnect() {
        final SwirldStateManager swirldStateManager = mock(SwirldStateManager.class);

        // The maximum number of events drained to the QueueThread buffer at a time
        final long maxEventsInBuffer = 100;
        final long sleepMillisPerEvent = 10;

        // Tracks the number of events handled from the queue
        final AtomicInteger numEventsHandled = new AtomicInteger(0);

        // sleep for a little while to pretend to handle an event
        doAnswer((e) -> {
                    Thread.sleep(sleepMillisPerEvent);
                    numEventsHandled.incrementAndGet();
                    return null;
                })
                .when(swirldStateManager)
                .handlePreConsensusEvent(any(EventImpl.class));

        final ExecutorService executor = Executors.newFixedThreadPool(1);

        // Set up a separate thread to invoke clear
        final Callable<Void> clear = (ThrowingRunnable) () -> preConsensusEventHandler.clear();

        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final ThreadConfig threadConfig = configuration.getConfigData(ThreadConfig.class);

        preConsensusEventHandler = new PreConsensusEventHandler(
                new NoOpMetrics(),
                getStaticThreadManager(),
                selfId,
                swirldStateManager,
                consensusMetrics,
                threadConfig);

        final int numEvents = 1000;
        final EventImpl event = mock(EventImpl.class);

        // Start the pre-consensus handler and add events to the queue for it to handle
        preConsensusEventHandler.start();
        for (int i = 0; i < numEvents; i++) {
            preConsensusEventHandler.preconsensusEvent(event);
        }

        // Make the separate thread invoke clear()
        final Future<Void> future = executor.submit(clear);

        // Wait up to the amount of time it would take for two full buffer of events to be handled. This entire time
        // shouldn't be needed, but it's a max time to allow for different systems and other programs running at the
        // same time. As long as this is less than the amount of time it takes to handle all the events in the queue,
        // the value is valid.
        final long maxMillisToWait = Math.round(maxEventsInBuffer * (sleepMillisPerEvent) * 2);

        // Wait for clear() to complete
        await().atMost(Duration.of(maxMillisToWait, ChronoUnit.MILLIS)).until(future::isDone);

        // Verify that no more than 2 buffers worth of events were handled
        assertTrue(
                numEventsHandled.get() < maxEventsInBuffer * 2,
                "pre-consensus handler should not enter another doWork() cycle after prepareForReconnect() is called");

        assertEquals(0, preConsensusEventHandler.getQueueSize(), "Pre-consensus queue should be empty");
    }

    /**
     * Test that null and empty events are discarded.
     */
    @Test
    void testEmptyEventsDiscarded() {
        final SwirldStateManager swirldStateManager = mock(SwirldStateManager.class);

        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final ThreadConfig threadConfig = configuration.getConfigData(ThreadConfig.class);

        preConsensusEventHandler = new PreConsensusEventHandler(
                new NoOpMetrics(),
                getStaticThreadManager(),
                selfId,
                swirldStateManager,
                consensusMetrics,
                threadConfig);

        assertDoesNotThrow(
                () -> preConsensusEventHandler.preconsensusEvent(null),
                "null events should be discarded and not added to the queue.");
        assertEquals(0, preConsensusEventHandler.getQueueSize(), "queue should be empty");

        final EventImpl emptyEvent = createEvents(1, 0, true).get(0);
        assertTrue(emptyEvent.isEmpty(), "The generated event should be empty");
        preConsensusEventHandler.preconsensusEvent(emptyEvent);
        assertEquals(0, preConsensusEventHandler.getQueueSize(), "Empty events should not be added to the queue");
    }
}
