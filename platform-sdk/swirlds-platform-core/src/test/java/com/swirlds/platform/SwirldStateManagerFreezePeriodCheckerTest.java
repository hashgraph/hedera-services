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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.SwirldStateManagerUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwirldStateManagerFreezePeriodCheckerTest {

    @Test
    void isInFreezePeriodTest() {
        final State mockState = mock(State.class);

        final DualStateImpl mockDualState = mock(DualStateImpl.class);
        final Instant consensusTime = Instant.now();

        final AddressBook addressBook = mock(AddressBook.class);
        when(addressBook.iterator()).thenReturn(Collections.emptyIterator());

        when(mockState.getPlatformDualState()).thenReturn(null);
        assertFalse(
                SwirldStateManagerUtils.isInFreezePeriod(Instant.now(), mockState),
                "when DualState is null, any Instant should not be in freezePeriod");

        when(mockState.getPlatformDualState()).thenReturn(mockDualState);
        for (boolean isInFreezeTime : List.of(true, false)) {
            when(mockDualState.isInFreezePeriod(consensusTime)).thenReturn(isInFreezeTime);
            assertEquals(
                    isInFreezeTime,
                    SwirldStateManagerUtils.isInFreezePeriod(consensusTime, mockState),
                    "swirldStateManager#isInFreezePeriod() should return the same result "
                            + "as current consensus DualState#isInFreezePeriod");
        }
    }
}
