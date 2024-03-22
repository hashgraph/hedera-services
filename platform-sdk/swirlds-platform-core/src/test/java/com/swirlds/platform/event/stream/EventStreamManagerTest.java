/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.swirlds.base.time.Time;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.RunningEventHashUpdate;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.internal.EventImpl;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventStreamManagerTest {
    private static final MultiStream<EventImpl> multiStreamMock = mock(MultiStream.class);
    private static final EventStreamManager eventStreamManager =
            new EventStreamManager(Time.getCurrent(), multiStreamMock, EventStreamManagerTest::isFreezeEvent);

    private static final EventImpl freezeEvent = mock(EventImpl.class);

    @Test
    void addEventTest() {
        final int nonFreezeEventsNum = 10;
        for (int i = 0; i < nonFreezeEventsNum; i++) {
            final EventImpl event = mock(EventImpl.class);
            eventStreamManager.addEvents(List.of(event));

            verify(multiStreamMock).addObject(event);
            // for non-freeze event, multiStream should not be closed after adding it
            verify(multiStreamMock, never()).close();
        }

        eventStreamManager.addEvents(List.of(freezeEvent));
        verify(multiStreamMock).addObject(freezeEvent);
        // for freeze event, multiStream should be closed after adding it
        verify(multiStreamMock).close();

        final EventImpl eventAddAfterFrozen = mock(EventImpl.class);
        eventStreamManager.addEvents(List.of(eventAddAfterFrozen));
        // after frozen, when adding event to the EventStreamManager, multiStream.add(event) should not be called
        verify(multiStreamMock, never()).addObject(eventAddAfterFrozen);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setStartWriteAtCompleteWindowTest(final boolean startWriteAtCompleteWindow) {
        final Random random = RandomUtils.getRandomPrintSeed();
        final Hash runningHash = RandomUtils.randomHash(random);
        eventStreamManager.updateRunningHash(new RunningEventHashUpdate(runningHash, startWriteAtCompleteWindow));
        verify(multiStreamMock).setRunningHash(runningHash);
    }

    /**
     * used for testing adding freeze event
     *
     * @param event the event to be added
     * @return whether
     */
    private static boolean isFreezeEvent(final EventImpl event) {
        return event == freezeEvent;
    }
}
