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

package com.swirlds.common.test.metrics.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.common.metrics.platform.DefaultIntegerPairAccumulator;
import com.swirlds.common.test.fixtures.FakeTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class CountPerSecondTest {
    private final FakeTime clock = new FakeTime(Instant.EPOCH, Duration.ZERO);
    private CountPerSecond metric;

    @BeforeEach
    void reset() {
        final Metrics metrics = mock(Metrics.class);
        when(metrics.getOrCreate(any())).thenAnswer((Answer<IntegerPairAccumulator<Double>>) invocation -> {
            final IntegerPairAccumulator.Config<Double> config = invocation.getArgument(0);
            return new DefaultIntegerPairAccumulator<>(config);
        });
        metric = new CountPerSecond(
                metrics,
                new CountPerSecond.Config("a", "b").withDescription("c").withUnit("d"),
                clock);
    }

    @Test
    void basic() {
        clock.set(Duration.ZERO);
        metric.count();
        clock.set(Duration.ofMillis(500));
        metric.count();

        assertEquals(4.0, metric.get(), "2 counts in half a second should be 4/second");
    }

    @Test
    void intOverflow() {
        // set the clock so that the milli epoch is just before the int overflow
        clock.set(Duration.ofMillis(Integer.MAX_VALUE - 1));
        metric.reset();
        assertEquals(Integer.MAX_VALUE - 1, metric.getMilliTime(), "the metric clock should be just below max int");
        metric.count(1);
        clock.tick(Duration.ofMillis(1));
        assertEquals(1000.0, metric.get(), "1 count in 1 millisecond should be 1000/second");
        assertEquals(0, metric.getMilliTime(), "the clock should have overflown and should now be 0");
    }

    @Test
    void noTimePassed() {
        assertEquals(0.0, metric.get(), "if the count is 0, then the return value should always be 0");
        metric.count();
        assertTrue(
                0.0 < metric.get(),
                "if now time has passed, then the per second count would be infinite. "
                        + "this should never happen in the real world, "
                        + "but we want to make sure that the value returned is at least positive");
    }
}
