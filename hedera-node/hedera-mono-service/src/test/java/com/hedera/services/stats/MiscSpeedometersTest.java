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
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MiscSpeedometersTest {
    private static final double halfLife = 10.0;

    @Mock private Platform platform;
    @Mock private SpeedometerMetric syncVerifies;
    @Mock private SpeedometerMetric txnRejections;
    @Mock private Metrics metrics;

    private MiscSpeedometers subject;

    @BeforeEach
    void setup() {
        platform = mock(Platform.class);
        given(platform.getMetrics()).willReturn(metrics);
        given(metrics.getOrCreate(any())).willReturn(syncVerifies).willReturn(txnRejections);

        subject = new MiscSpeedometers(halfLife);
    }

    @Test
    void registersExpectedStatEntries() {
        subject.setSyncVerifications(syncVerifies);
        subject.setPlatformTxnRejections(txnRejections);

        subject.registerWith(platform);

        verify(metrics, times(2)).getOrCreate(any());
    }

    @Test
    void cyclesExpectedSpeedometers() {
        subject.registerWith(platform);

        subject.cycleSyncVerifications();
        subject.cyclePlatformTxnRejections();

        verify(syncVerifies).cycle();
        verify(txnRejections).cycle();
    }
}
