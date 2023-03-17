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

package com.swirlds.common.test;

import static com.swirlds.test.framework.TestQualifierTags.TIME_CONSUMING;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.StartupTime;
import com.swirlds.test.framework.TestTypeTags;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Startup Time Test")
class StartupTimeTest {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TIME_CONSUMING)
    @DisplayName("Test Startup Time")
    void testStartupTime() throws InterruptedException {

        StartupTime.markStartupTime();

        final Duration timeSinceStartup = StartupTime.getTimeSinceStartup();

        final Instant startupTime = StartupTime.getStartupTime();
        Thread.sleep(200);
        StartupTime.markStartupTime();
        final Instant startupTime2 = StartupTime.getStartupTime();

        assertSame(startupTime, startupTime2, "same instant should be returned each time startup time is called");

        final Duration timeSinceStartup2 = StartupTime.getTimeSinceStartup();
        final Duration timeSinceStartup3 = StartupTime.getTimeSinceStartup();

        assertFalse(timeSinceStartup3.minus(timeSinceStartup2).isNegative(), "time since startup should increase");
        assertFalse(timeSinceStartup2.minus(timeSinceStartup).isNegative(), "time since startup should increase");
    }
}
