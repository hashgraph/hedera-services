/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformStateFacadeTest {

    @Test
    void isInFreezePeriodTest() {

        final Instant t1 = Instant.now();
        final Instant t2 = t1.plusSeconds(1);
        final Instant t3 = t2.plusSeconds(1);
        final Instant t4 = t3.plusSeconds(1);

        // No freeze time set
        assertFalse(PlatformStateFacade.isInFreezePeriod(t1, null, null));

        // No freeze time set, previous freeze time set
        assertFalse(PlatformStateFacade.isInFreezePeriod(t2, null, t1));

        // Freeze time is in the future, never frozen before
        assertFalse(PlatformStateFacade.isInFreezePeriod(t2, t3, null));

        // Freeze time is in the future, frozen before
        assertFalse(PlatformStateFacade.isInFreezePeriod(t2, t3, t1));

        // Freeze time is in the past, never frozen before
        assertTrue(PlatformStateFacade.isInFreezePeriod(t2, t1, null));

        // Freeze time is in the past, frozen before at an earlier time
        assertTrue(PlatformStateFacade.isInFreezePeriod(t3, t2, t1));

        // Freeze time in the past, already froze at that exact time
        assertFalse(PlatformStateFacade.isInFreezePeriod(t3, t2, t2));
    }
}
