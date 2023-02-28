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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.SwirldStateManagerDouble;
import com.swirlds.platform.state.SwirldStateManagerSingle;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SwirldStateManagerFreezePeriodCheckerTest {
    private final State mockState = mock(State.class);
    private final DualStateImpl mockDualState = mock(DualStateImpl.class);
    private final Instant consensusTime = Instant.now();

    private static Stream<Arguments> swirldStateManagers() {
        return Stream.of(
                Arguments.of(spy(SwirldStateManagerSingle.class)), Arguments.of(spy(SwirldStateManagerDouble.class)));
    }

    @ParameterizedTest
    @MethodSource("swirldStateManagers")
    void isInFreezePeriodTest(final SwirldStateManager swirldStateManager) {
        doReturn(mockState).when(swirldStateManager).getConsensusState();

        when(mockState.getPlatformDualState()).thenReturn(null);
        assertFalse(
                swirldStateManager.isInFreezePeriod(Instant.now()),
                "when DualState is null, any Instant should not be in freezePeriod");

        when(mockState.getPlatformDualState()).thenReturn(mockDualState);
        for (boolean isInFreezeTime : List.of(true, false)) {
            when(mockDualState.isInFreezePeriod(consensusTime)).thenReturn(isInFreezeTime);
            assertEquals(
                    isInFreezeTime,
                    swirldStateManager.isInFreezePeriod(consensusTime),
                    "swirldStateManager#isInFreezePeriod() should return the same result "
                            + "as current consensus DualState#isInFreezePeriod");
        }
    }
}
