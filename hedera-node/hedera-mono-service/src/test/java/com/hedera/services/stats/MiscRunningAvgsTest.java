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
import static org.mockito.BDDMockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MiscRunningAvgsTest {
    private static final double halfLife = 10.0;

    @Mock private Platform platform;

    @Mock private RunningAverageMetric gasPerSec;
    @Mock private RunningAverageMetric submitSizes;
    @Mock private RunningAverageMetric queueSize;
    @Mock private RunningAverageMetric hashS;
    @Mock private Metrics metrics;
    private MiscRunningAvgs subject;

    @BeforeEach
    void setup() {
        subject = new MiscRunningAvgs(halfLife);
    }

    @Test
    void registersExpectedStatEntries() {
        setMocks();
        given(platform.getMetrics()).willReturn(metrics);

        subject.registerWith(platform);

        verify(metrics, times(4)).getOrCreate(any());
    }

    @Test
    void recordsToExpectedAvgs() {
        setMocks();

        subject.recordHandledSubmitMessageSize(3);
        subject.writeQueueSizeRecordStream(4);
        subject.hashQueueSizeRecordStream(5);
        subject.recordGasPerConsSec(6L);

        verify(submitSizes).update(3.0);
        verify(queueSize).update(4.0);
        verify(hashS).update(5);
        verify(gasPerSec).update(6L);
    }

    private void setMocks() {
        subject.setHandledSubmitMessageSize(submitSizes);
        subject.setWriteQueueSizeRecordStream(queueSize);
        subject.setHashQueueSizeRecordStream(hashS);
        subject.setGasPerConsSec(gasPerSec);
    }
}
