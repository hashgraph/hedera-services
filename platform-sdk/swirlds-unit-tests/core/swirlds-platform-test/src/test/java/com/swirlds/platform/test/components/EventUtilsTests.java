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

import static com.swirlds.common.utility.EventUtils.calculateNewEventCreationTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventUtils Tests")
class EventUtilsTests {
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
