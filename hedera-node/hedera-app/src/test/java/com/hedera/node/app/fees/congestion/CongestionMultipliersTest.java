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

import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
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
    private EntityUtilizationMultiplier entityUtilizationMultiplier;

    @Mock
    private ThrottleMultiplier throttleMultiplier;

    @Mock
    private TransactionInfo txnInfo;

    @Mock
    private ReadableStoreFactory storeFactory;

    @BeforeEach
    void setup() {
        entityUtilizationMultiplier = mock(EntityUtilizationMultiplier.class);
        throttleMultiplier = mock(ThrottleMultiplier.class);
        txnInfo = mock(TransactionInfo.class);
        storeFactory = mock(ReadableStoreFactory.class);

        congestionMultipliers = new CongestionMultipliers(entityUtilizationMultiplier, throttleMultiplier);
    }

    @Test
    void testUpdateMultiplier() {
        Instant consensusTime = Instant.now();
        congestionMultipliers.updateMultiplier(consensusTime);

        verify(throttleMultiplier).updateMultiplier(consensusTime);
        verify(entityUtilizationMultiplier).updateMultiplier(consensusTime);
    }

    @Test
    void testMaxCurrentMultiplier() {
        when(throttleMultiplier.currentMultiplier()).thenReturn(2L);
        when(entityUtilizationMultiplier.currentMultiplier(txnInfo.txBody(), txnInfo.functionality(), storeFactory))
                .thenReturn(3L);

        long maxMultiplier = congestionMultipliers.maxCurrentMultiplier(txnInfo, storeFactory);

        assertEquals(3L, maxMultiplier);
    }

    @Test
    void testGenericCongestionStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(10)};
        when(entityUtilizationMultiplier.congestionLevelStarts()).thenReturn(congestionStarts);

        Instant[] starts = congestionMultipliers.entityUtilizationCongestionStarts();

        assertEquals(congestionStarts, starts);
    }

    @Test
    void testThrottleMultiplierCongestionStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(5)};
        when(throttleMultiplier.congestionLevelStarts()).thenReturn(congestionStarts);

        Instant[] starts = congestionMultipliers.throttleMultiplierCongestionStarts();

        assertEquals(congestionStarts, starts);
    }

    @Test
    void testResetEntityUtilizationMultiplierStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(10)};
        congestionMultipliers.resetEntityUtilizationMultiplierStarts(congestionStarts);

        verify(entityUtilizationMultiplier).resetCongestionLevelStarts(congestionStarts);
    }

    @Test
    void testResetThrottleMultiplierStarts() {
        Instant[] congestionStarts = {Instant.now(), Instant.now().plusSeconds(5)};
        congestionMultipliers.resetThrottleMultiplierStarts(congestionStarts);

        verify(throttleMultiplier).resetCongestionLevelStarts(congestionStarts);
    }

    @Test
    void testResetExpectations() {
        congestionMultipliers.resetExpectations();

        verify(throttleMultiplier).resetExpectations();
        verify(entityUtilizationMultiplier).resetExpectations();
    }
}
