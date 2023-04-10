/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.stream;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.RandomUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventStreamManagerTest {
    private static final NodeId selfId = new NodeId(false, 0);
    private static final String nodeName = "node0";
    private static final String eventsLogDir = "eventStream/";
    private static final long eventsLogPeriod = 5;
    private static final int eventStreamQueueCapacity = 100;

    private static final String INITIALIZE_NOT_NULL = "after initialization, the instance should not be null";
    private static final String INITIALIZE_QUEUE_EMPTY = "after initialization, hash queue should be empty";
    private static final String UNEXPECTED_VALUE = "unexpected value";

    private static EventStreamManager<ObjectForTestStream> disableStreamingInstance;
    private static EventStreamManager<ObjectForTestStream> enableStreamingInstance;

    private static final Hash initialHash = RandomUtils.randomHash();

    private static final MultiStream<ObjectForTestStream> multiStreamMock = mock(MultiStream.class);
    private static final EventStreamManager<ObjectForTestStream> EVENT_STREAM_MANAGER =
            new EventStreamManager<>(multiStreamMock, EventStreamManagerTest::isFreezeEvent);

    private static final ObjectForTestStream freezeEvent = mock(ObjectForTestStream.class);

    @BeforeAll
    static void init() throws Exception {
        disableStreamingInstance = new EventStreamManager<>(
                getStaticThreadManager(),
                selfId,
                mock(Signer.class),
                nodeName,
                false,
                eventsLogDir,
                eventsLogPeriod,
                eventStreamQueueCapacity,
                EventStreamManagerTest::isFreezeEvent);

        enableStreamingInstance = new EventStreamManager<>(
                getStaticThreadManager(),
                selfId,
                mock(Signer.class),
                nodeName,
                true,
                eventsLogDir,
                eventsLogPeriod,
                eventStreamQueueCapacity,
                EventStreamManagerTest::isFreezeEvent);
    }

    @Test
    void initializeTest() {
        assertNull(
                disableStreamingInstance.getStreamFileWriter(),
                "When eventStreaming is disabled, streamFileWriter instance should be null");
        assertNotNull(disableStreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
        assertNotNull(disableStreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
        assertEquals(0, disableStreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
        assertEquals(0, disableStreamingInstance.getEventStreamingQueueSize(), INITIALIZE_QUEUE_EMPTY);

        assertNotNull(
                enableStreamingInstance.getStreamFileWriter(),
                "When eventStreaming is enabled, streamFileWriter instance should not be null");
        assertNotNull(enableStreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
        assertNotNull(enableStreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
        assertEquals(0, enableStreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
        assertEquals(0, enableStreamingInstance.getEventStreamingQueueSize(), INITIALIZE_QUEUE_EMPTY);
    }

    @Test
    void setInitialHashTest() {
        EVENT_STREAM_MANAGER.setInitialHash(initialHash);
        verify(multiStreamMock).setRunningHash(initialHash);
        assertEquals(initialHash, EVENT_STREAM_MANAGER.getInitialHash(), "initialHash is not set");
    }

    @Test
    void addEventTest() throws InterruptedException {
        EventStreamManager<ObjectForTestStream> eventStreamManager =
                new EventStreamManager<>(multiStreamMock, EventStreamManagerTest::isFreezeEvent);
        assertFalse(
                eventStreamManager.getFreezePeriodStarted(),
                "freezePeriodStarted should be false after initialization");
        final int nonFreezeEventsNum = 10;
        for (int i = 0; i < nonFreezeEventsNum; i++) {
            ObjectForTestStream event = mock(ObjectForTestStream.class);
            eventStreamManager.addEvent(event);
            verify(multiStreamMock).addObject(event);
            // for non-freeze event, multiStream should not be closed after adding it
            verify(multiStreamMock, never()).close();
            assertFalse(
                    eventStreamManager.getFreezePeriodStarted(),
                    "freezePeriodStarted should be false after adding non-freeze event");
        }
        eventStreamManager.addEvent(freezeEvent);
        verify(multiStreamMock).addObject(freezeEvent);
        // for freeze event, multiStream should be closed after adding it
        verify(multiStreamMock).close();
        assertTrue(
                eventStreamManager.getFreezePeriodStarted(), "freezePeriodStarted should be true adding freeze event");

        ObjectForTestStream eventAddAfterFrozen = mock(ObjectForTestStream.class);
        eventStreamManager.addEvent(eventAddAfterFrozen);
        // after frozen, when adding event to the EventStreamManager, multiStream.add(event) should not be called
        verify(multiStreamMock, never()).addObject(eventAddAfterFrozen);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setStartWriteAtCompleteWindowTest(boolean startWriteAtCompleteWindow) {
        enableStreamingInstance.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
        assertEquals(
                startWriteAtCompleteWindow,
                enableStreamingInstance.getStreamFileWriter().getStartWriteAtCompleteWindow(),
                UNEXPECTED_VALUE);
    }

    /**
     * used for testing adding freeze event
     *
     * @param event
     * 		the event to be added
     * @return whether
     */
    private static boolean isFreezeEvent(final ObjectForTestStream event) {
        return event == freezeEvent;
    }
}
