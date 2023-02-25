/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.swirlds.common.system.SwirldState;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SwirldStateManagerUtilsTests {

    private final State state = new State();
    private final SwirldStateMetrics stats = mock(SwirldStateMetrics.class);

    private final PlatformState platformState = new PlatformState();
    private final SwirldState swirldState = new DummySwirldState2();

    @BeforeEach
    void setup() {
        final DualStateImpl dualState = new DualStateImpl();
        state.setSwirldState(swirldState);
        state.setDualState(dualState);
        state.setPlatformState(platformState);
    }

    @Test
    void testFastCopyIsMutable() {
        state.reserve();
        final State result = SwirldStateManagerUtils.fastCopy(state, stats);
        assertFalse(result.isImmutable(), "The copy state should be mutable.");
        assertEquals(
                1,
                state.getReservationCount(),
                "Fast copy should not change the reference count of the state it copies.");
        assertEquals(
                1, result.getReservationCount(), "Fast copy should return a new state with a reference count of 1.");
    }
}
