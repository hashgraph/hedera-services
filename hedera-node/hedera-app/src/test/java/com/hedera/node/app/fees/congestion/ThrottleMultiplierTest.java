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

package com.hedera.node.app.fees.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.node.app.hapi.utils.throttles.CongestibleThrottle;
import com.hedera.node.config.types.CongestionMultipliers;
import java.time.Instant;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class ThrottleMultiplierTest {
    private static final long DEFAULT_MULTIPLIER = 1L;
    private static final long THROTTLE_CAPACITY = 100_000L;

    @Mock
    private CongestionMultipliers congestionMultipliers;

    @Mock
    private CongestibleThrottle throttle;

    private ThrottleMultiplier subject;

    @BeforeEach
    void setUp() {
        congestionMultipliers = mock(CongestionMultipliers.class);
        throttle = mock(CongestibleThrottle.class);

        Supplier<CongestionMultipliers> multiplierSupplier = () -> congestionMultipliers;
        Supplier<List<? extends CongestibleThrottle>> throttleSource = () -> List.of(throttle);

        subject = new ThrottleMultiplier(
                "UsageType",
                "AbbrevUsageType",
                "CongestionType",
                mock(LongSupplier.class),
                multiplierSupplier,
                throttleSource);

        assertEquals(DEFAULT_MULTIPLIER, subject.currentMultiplier());
    }

    @Test
    void testUpdateMultiplier() {
        final var firstMultiplier = 2L;
        final var secondMultiplier = 3L;
        final var thirdMultiplier = 4L;
        when(congestionMultipliers.multipliers())
                .thenReturn(new long[] {firstMultiplier, secondMultiplier, thirdMultiplier});

        final var firstUsagePercentTrigger = 5;
        final var secondUsagePercentTrigger = 10;
        final var thirdUsagePercentTrigger = 90;
        when(congestionMultipliers.usagePercentTriggers())
                .thenReturn(new int[] {firstUsagePercentTrigger, secondUsagePercentTrigger, thirdUsagePercentTrigger});
        when(throttle.capacity()).thenReturn(THROTTLE_CAPACITY);
        subject.resetExpectations();

        var usagePercentage = 7;
        when(throttle.used()).thenReturn(usagePercentage * (THROTTLE_CAPACITY / 100));
        subject.updateMultiplier(Instant.now());
        assertEquals(firstMultiplier, subject.currentMultiplier());

        usagePercentage = 50;
        when(throttle.used()).thenReturn(usagePercentage * (THROTTLE_CAPACITY / 100));
        subject.updateMultiplier(Instant.now());
        assertEquals(secondMultiplier, subject.currentMultiplier());

        usagePercentage = 95;
        when(throttle.used()).thenReturn(usagePercentage * (THROTTLE_CAPACITY / 100));
        subject.updateMultiplier(Instant.now());
        assertEquals(thirdMultiplier, subject.currentMultiplier());
    }

    @Test
    void testResetCongestionLevelStarts() {
        Instant[] startTimes = {Instant.now(), Instant.now().plusSeconds(10)};

        subject.resetCongestionLevelStarts(startTimes);

        assertEquals(startTimes[0], subject.congestionLevelStarts()[0]);
        assertEquals(startTimes[1], subject.congestionLevelStarts()[1]);
    }
}
