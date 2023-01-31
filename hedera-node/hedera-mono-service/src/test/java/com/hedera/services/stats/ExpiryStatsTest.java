/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryStatsTest {
    private static final double halfLife = 10.0;

    @Mock private Platform platform;
    @Mock private RunningAverageMetric idsScannedPerConsSec;
    @Mock private Counter contractsRemoved;
    @Mock private Counter contractsRenewed;
    @Mock private Metrics metrics;

    private ExpiryStats subject;

    @BeforeEach
    void setup() {
        subject = new ExpiryStats(halfLife);
    }

    @Test
    void registersExpectedStatEntries() {
        setMocks();
        given(platform.getMetrics()).willReturn(metrics);

        subject.registerWith(platform);

        verify(metrics, times(3)).getOrCreate(any());
    }

    @Test
    void recordsToExpectedMetrics() {
        setMocks();

        subject.countRemovedContract();
        subject.countRenewedContract();
        subject.includeIdsScannedInLastConsSec(5L);

        verify(contractsRemoved).increment();
        verify(contractsRenewed).increment();
        verify(idsScannedPerConsSec).update(5.0);
    }

    private void setMocks() {
        subject.setIdsScannedPerConsSec(idsScannedPerConsSec);
        subject.setContractsRemoved(contractsRemoved);
        subject.setContractsRenewed(contractsRenewed);
    }
}
