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

package com.swirlds.common.test.throttle;

import static com.swirlds.common.utility.CompareTo.isLessThan;
import static com.swirlds.common.utility.throttle.MinimumTime.runWithMinimumTime;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.time.OSTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MinimumTime Test")
class MinimumTimeTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    @DisplayName("Period Test")
    void minimumTimeTest(final int periodMs) throws InterruptedException {
        final AtomicInteger count = new AtomicInteger(0);

        final Duration limit = Duration.ofSeconds(1);

        final Instant start = Instant.now();

        while (isLessThan(Duration.between(start, Instant.now()), limit)) {
            runWithMinimumTime(OSTime.getInstance(), count::incrementAndGet, Duration.ofMillis(periodMs));
        }

        assertTrue(count.get() <= 1000 / periodMs, "count is too high");
        assertTrue(count.get() > 1000 / periodMs / 2, "count is way too low");
    }
}
