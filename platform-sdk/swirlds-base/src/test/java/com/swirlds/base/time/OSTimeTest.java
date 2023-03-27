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

package com.swirlds.base.time;

import com.swirlds.base.time.internal.OSTime;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OSTimeTest {

    @Test
    void testNanoTime() {
        // given
        final Time time = OSTime.getInstance();
        final long before = time.nanoTime();

        // when
        tickNs();
        final long toTest = time.nanoTime();
        tickNs();
        final long after = time.nanoTime();

        // then
        Assertions.assertTrue(before < toTest);
        Assertions.assertTrue(toTest < after);
    }

    @Test
    void testCurrentTimeMillis() {
        // given
        final Time time = OSTime.getInstance();
        final long before = time.currentTimeMillis();

        // when
        tickMs();
        final long toTest = time.currentTimeMillis();
        tickMs();
        final long after = time.currentTimeMillis();

        // then
        Assertions.assertTrue(before < toTest);
        Assertions.assertTrue(toTest < after);
    }

    @Test
    void testNow() {
        // given
        final Time time = OSTime.getInstance();
        final Instant before = time.now();

        // when
        tickNs();
        final Instant toTest = time.now();
        tickNs();
        final Instant after = time.now();

        // then
        Assertions.assertTrue(before.isBefore(toTest));
        Assertions.assertTrue(toTest.isBefore(after));
    }

    private static void tickNs() {
        final long start = System.nanoTime();
        while (System.nanoTime() == start) {
            // wait
        }
    }

    private static void tickMs() {
        final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() == start) {
            // wait
        }
    }
}
