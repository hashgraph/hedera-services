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

package com.swirlds.platform.test.components;

import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.event.EventUtils.calculateNewEventCreationTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.junit.tags.TestComponentTags;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.platform.test.event.EventMocks;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("EventUtils Tests")
class EventUtilsTests {

    @Test
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getTimeCreated() Test")
    void getTimeCreatedTest() {
        final Instant now = Instant.now();

        assertEquals(
                now, EventUtils.getChildTimeCreated(now, null), "time should not be increased for null self parent");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);

        Mockito.when(hashedData.getTimeCreated()).thenReturn(now.minusNanos(1000));

        assertEquals(
                now,
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should not be increased if parent is in the past with no transactions");

        Mockito.when(hashedData.getTransactions()).thenReturn(new SwirldTransaction[100]);
        assertEquals(
                now,
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should not be increased if parent is in the past with a few transactions");

        Mockito.when(hashedData.getTransactions()).thenReturn(new SwirldTransaction[2000]);
        assertEquals(
                now.plusNanos(1000),
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should be increased so that 1 nanosecond passes per previous transaction");

        Mockito.when(hashedData.getTransactions()).thenReturn(new SwirldTransaction[0]);
        Mockito.when(hashedData.getTimeCreated()).thenReturn(now);
        assertEquals(
                now.plusNanos(1),
                EventUtils.getChildTimeCreated(now, EventMocks.mockEvent(hashedData)),
                "time should be increased so that 1 nanosecond since event with 0 transactions");
    }

    @Test
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getEventGeneration() Test")
    void getEventGenerationTest() {
        assertEquals(-1, EventUtils.getEventGeneration(null), "generation of a null event should equal -1");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        Mockito.when(hashedData.getGeneration()).thenReturn(1234L);

        assertEquals(
                hashedData.getGeneration(),
                EventUtils.getEventGeneration(EventMocks.mockEvent(hashedData)),
                "should return generation of non-null event");
    }

    @Test
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getEventHash() Test")
    void getEventHashTest() {
        assertNull(EventUtils.getEventHash(null), "hash of null event should be null");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        Mockito.when(hashedData.getHash()).thenReturn(randomHash());

        assertSame(
                hashedData.getHash().getValue(),
                EventUtils.getEventHash(EventMocks.mockEvent(hashedData)),
                "should return the hash of a non-null event");
    }

    @Test
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("getOtherParentCreatorId() Test")
    void getOtherParentCreatorIdTest() {
        assertEquals(
                EventConstants.CREATOR_ID_UNDEFINED,
                EventUtils.getCreatorId(null),
                "null event should have creator ID = CREATOR_ID_UNDEFINED");

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        Mockito.when(hashedData.getCreatorId()).thenReturn(new NodeId(4321L));

        assertEquals(
                hashedData.getCreatorId(),
                EventUtils.getCreatorId(EventMocks.mockEvent(hashedData)),
                "should have returned creator id of event");
    }

    @Test
    @DisplayName("calculateNewEventCreationTime Test()")
    void calculateNewEventCreationTimeTest() {
        final Instant parentTime = Instant.now();

        // now is after minimum time, no transactions
        final Instant now1 = parentTime.plusNanos(10);
        final Instant calculatedTime1 = calculateNewEventCreationTime(now1, parentTime, 0);
        assertEquals(now1, calculatedTime1);

        // now is after minimum time with transactions
        final Instant now2 = parentTime.plusNanos(10);
        final Instant calculatedTime2 = calculateNewEventCreationTime(now2, parentTime, 5);
        assertEquals(now2, calculatedTime2);

        // now is before minimum time, no transactions
        final Instant now3 = parentTime.minusNanos(10);
        final Instant calculatedTime3 = calculateNewEventCreationTime(now3, parentTime, 0);
        assertEquals(parentTime.plusNanos(1), calculatedTime3);

        // now is before minimum time because of transactions
        final Instant now4 = parentTime.plusNanos(10);
        final Instant calculatedTime4 = calculateNewEventCreationTime(now4, parentTime, 20);
        assertEquals(parentTime.plusNanos(20), calculatedTime4);

        // exact time no transactions
        final Instant now5 = parentTime;
        final Instant calculatedTime5 = calculateNewEventCreationTime(now5, parentTime, 0);
        assertEquals(parentTime.plusNanos(1), calculatedTime5);
    }
}
