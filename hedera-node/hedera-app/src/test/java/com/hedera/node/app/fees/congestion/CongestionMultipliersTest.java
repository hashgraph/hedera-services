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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class CongestionMultipliersTest {
    private CongestionMultipliers congestionMultipliers;

    @Mock
    private UtilizationScaledThrottleMultiplier utilizationScaledThrottleMultiplier;

    @Mock
    private ThrottleMultiplier throttleMultiplier;

    @Mock
    private TransactionInfo txnInfo;

    @Mock
    private ReadableStoreFactory storeFactory;

    @BeforeEach
    void setUp() {
        utilizationScaledThrottleMultiplier = mock(UtilizationScaledThrottleMultiplier.class);
        throttleMultiplier = mock(ThrottleMultiplier.class);
        txnInfo = mock(TransactionInfo.class);
        storeFactory = mock(ReadableStoreFactory.class);

        congestionMultipliers = new CongestionMultipliers(utilizationScaledThrottleMultiplier, throttleMultiplier);
    }

    @Test
    void testUpdateMultiplier() {
        Instant consensusTime = Instant.now();
        congestionMultipliers.updateMultiplier(consensusTime);

        verify(throttleMultiplier).updateMultiplier(consensusTime);
        verify(utilizationScaledThrottleMultiplier).updateMultiplier(consensusTime);
    }

    @Test
    void testMaxCurrentMultiplier() {
        when(throttleMultiplier.currentMultiplier()).thenReturn(2L);
        when(utilizationScaledThrottleMultiplier.currentMultiplier(
                        txnInfo.txBody(), txnInfo.functionality(), storeFactory))
                .thenReturn(3L);

        long maxMultiplier = congestionMultipliers.maxCurrentMultiplier(txnInfo, storeFactory);

        assertEquals(3L, maxMultiplier);
    }

    @Test
    void testGenericCongestionStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(10)};
        when(utilizationScaledThrottleMultiplier.congestionLevelStarts()).thenReturn(congestionStarts);

        Instant[] starts = congestionMultipliers.entityUtilizationCongestionStarts();

        assertEquals(congestionStarts, starts);
    }

    @Test
    void testThrottleMultiplierCongestionStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(5)};
        when(throttleMultiplier.congestionLevelStarts()).thenReturn(congestionStarts);

        Instant[] starts = congestionMultipliers.gasThrottleMultiplierCongestionStarts();

        assertEquals(congestionStarts, starts);
    }

    @Test
    void testResetEntityUtilizationMultiplierStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(10)};
        congestionMultipliers.resetUtilizationScaledThrottleMultiplierStarts(congestionStarts);

        verify(utilizationScaledThrottleMultiplier).resetCongestionLevelStarts(congestionStarts);
    }

    @Test
    void testResetThrottleMultiplierStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(5)};
        congestionMultipliers.resetGasThrottleMultiplierStarts(congestionStarts);

        verify(throttleMultiplier).resetCongestionLevelStarts(congestionStarts);
    }

    @Test
    void testResetExpectations() {
        congestionMultipliers.resetExpectations();

        verify(throttleMultiplier).resetExpectations();
        verify(utilizationScaledThrottleMultiplier).resetExpectations();
    }
}
