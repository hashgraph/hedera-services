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

package com.swirlds.common.test.throttle;

import static com.swirlds.common.utility.CompareTo.isLessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.utility.throttle.RateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RateLimiter Tests")
class RateLimiterTests {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    @DisplayName("Period Test")
    void periodTest(final int periodMs) {
        final FakeTime time = new FakeTime(Duration.ofNanos(1));
        final RateLimiter rateLimiter = new RateLimiter(time, Duration.ofMillis(periodMs));

        long count = 0;
        long denied = 0;

        final Duration limit = Duration.ofSeconds(1);
        while (isLessThan(time.elapsed(), limit)) {
            assertEquals(denied, rateLimiter.getDeniedRequests(), "invalid number of denied requests");
            if (rateLimiter.request()) {
                count++;
                denied = 0;
            } else {
                denied++;
            }
            time.tick(Duration.ofNanos(1_000));
        }

        assertTrue(count <= 1000 / periodMs, "count is too high");
        assertTrue(count > 1000 / periodMs / 2, "count is way to low");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    @DisplayName("Frequency Test")
    void frequencyTest(final int periodMs) {
        final FakeTime time = new FakeTime(Duration.ofNanos(1));
        final RateLimiter rateLimiter = new RateLimiter(time, 1000.0 / periodMs);

        long count = 0;
        long denied = 0;

        final Duration limit = Duration.ofSeconds(1);
        while (isLessThan(time.elapsed(), limit)) {
            assertEquals(denied, rateLimiter.getDeniedRequests(), "invalid number of denied requests");
            if (rateLimiter.request()) {
                count++;
                denied = 0;
            } else {
                denied++;
            }
            time.tick(Duration.ofNanos(1_000));
        }

        assertTrue(count <= 1000 / periodMs, "count is too high");
        assertTrue(count > 1000 / periodMs / 2, "count is way to low");
    }
}
