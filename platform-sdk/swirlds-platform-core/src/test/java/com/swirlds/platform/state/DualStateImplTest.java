/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MICROS;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class DualStateImplTest {
    private static final int TIME_DIFF = 1;

    static final List<ChronoUnit> timeUnits = List.of(NANOS, MICROS, MILLIS, SECONDS, MINUTES, HOURS, DAYS);

    private final Instant freezeTime = Instant.now();

    @Test
    void isInFreezePeriodTest() {
        final DualStateImpl dualState = new DualStateImpl();
        assertFalse(
                dualState.isInFreezePeriod(Instant.now()),
                "when freezeTime is null, any Instant should not be in freezePeriod");

        dualState.setFreezeTime(freezeTime);

        // when lastFrozenTime is null
        for (ChronoUnit unit : timeUnits) {
            assertFalse(
                    dualState.isInFreezePeriod(freezeTime.minus(TIME_DIFF, unit)),
                    "Instant before freeze time should not be in freeze period");

            assertTrue(
                    dualState.isInFreezePeriod(freezeTime),
                    "when lastFrozenTime is null, Instant the same as freeze time should be in freeze period");

            assertTrue(
                    dualState.isInFreezePeriod(freezeTime.plus(TIME_DIFF, unit)),
                    "when lastFrozenTime is null, Instant after freeze time should be in freeze period");
        }

        // when lastFrozenTime is the same as freezeTime
        dualState.setLastFrozenTime(freezeTime);
        for (ChronoUnit unit : timeUnits) {
            assertFalse(
                    dualState.isInFreezePeriod(freezeTime.minus(TIME_DIFF, unit)),
                    "Instant before freeze time should not be in freeze period");
            assertFalse(
                    dualState.isInFreezePeriod(this.freezeTime),
                    "when lastFrozenTime is the same as freezeTime, any Instant should be not in freeze period");
            assertFalse(
                    dualState.isInFreezePeriod(freezeTime.plus(TIME_DIFF, unit)),
                    "when lastFrozenTime is the same as freezeTime, any Instant should not be in freeze period");
        }

        // when lastFrozenTime is after freezeTime
        for (ChronoUnit unit : timeUnits) {
            dualState.setLastFrozenTime(freezeTime.plus(TIME_DIFF * 2, unit));
            assertFalse(
                    dualState.isInFreezePeriod(freezeTime.minus(TIME_DIFF, unit)),
                    "Instant before freeze time should not be in freeze period");
            assertFalse(
                    dualState.isInFreezePeriod(this.freezeTime),
                    "when lastFrozenTime is after freezeTime, any Instant should be not in freeze period");
            assertFalse(
                    dualState.isInFreezePeriod(freezeTime.plus(TIME_DIFF, unit)),
                    "when lastFrozenTime is after freezeTime, any Instant should not be in freeze period");
        }
    }
}
