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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsRunningAverage;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MiscRunningAvgsTest {
    private static final double halfLife = 10.0;

    private Platform platform;
    private RunningAvgFactory factory;

    private MiscRunningAvgs subject;

    @BeforeEach
    void setup() throws Exception {
        factory = mock(RunningAvgFactory.class);
        platform = mock(Platform.class);

        subject = new MiscRunningAvgs(factory, halfLife);
    }

    @Test
    void registersExpectedStatEntries() {
        final var retries = mock(StatEntry.class);
        final var waitMs = mock(StatEntry.class);
        final var queueSizes = mock(StatEntry.class);
        final var submitSizes = mock(StatEntry.class);
        final var gasPerSec = mock(StatEntry.class);
        given(
                        factory.from(
                                MiscRunningAvgs.Names.ACCOUNT_LOOKUP_RETRIES,
                                MiscRunningAvgs.Descriptions.ACCOUNT_LOOKUP_RETRIES,
                                subject.accountLookupRetries))
                .willReturn(retries);
        given(
                        factory.from(
                                MiscRunningAvgs.Names.ACCOUNT_RETRY_WAIT_MS,
                                MiscRunningAvgs.Descriptions.ACCOUNT_RETRY_WAIT_MS,
                                subject.accountRetryWaitMs))
                .willReturn(waitMs);
        given(
                        factory.from(
                                MiscRunningAvgs.Names.WRITE_QUEUE_SIZE_RECORD_STREAM,
                                MiscRunningAvgs.Descriptions.WRITE_QUEUE_SIZE_RECORD_STREAM,
                                subject.writeQueueSizeRecordStream))
                .willReturn(queueSizes);
        given(
                        factory.from(
                                MiscRunningAvgs.Names.HANDLED_SUBMIT_MESSAGE_SIZE,
                                MiscRunningAvgs.Descriptions.HANDLED_SUBMIT_MESSAGE_SIZE,
                                subject.handledSubmitMessageSize))
                .willReturn(submitSizes);
        given(
                        factory.from(
                                MiscRunningAvgs.Names.GAS_PER_CONSENSUS_SEC,
                                MiscRunningAvgs.Descriptions.GAS_PER_CONSENSUS_SEC,
                                subject.gasPerConsSec))
                .willReturn(gasPerSec);

        subject.registerWith(platform);

        verify(platform).addAppStatEntry(retries);
        verify(platform).addAppStatEntry(waitMs);
        verify(platform).addAppStatEntry(queueSizes);
        verify(platform).addAppStatEntry(submitSizes);
        verify(platform).addAppStatEntry(gasPerSec);
    }

    @Test
    void recordsToExpectedAvgs() {
        final var retries = mock(StatsRunningAverage.class);
        final var waitMs = mock(StatsRunningAverage.class);
        final var queueSize = mock(StatsRunningAverage.class);
        final var submitSizes = mock(StatsRunningAverage.class);
        final var hashS = mock(StatsRunningAverage.class);
        final var gasPerSec = mock(StatsRunningAverage.class);
        subject.accountLookupRetries = retries;
        subject.accountRetryWaitMs = waitMs;
        subject.handledSubmitMessageSize = submitSizes;
        subject.writeQueueSizeRecordStream = queueSize;
        subject.hashQueueSizeRecordStream = hashS;
        subject.gasPerConsSec = gasPerSec;

        subject.recordAccountLookupRetries(1);
        subject.recordAccountRetryWaitMs(2.0);
        subject.recordHandledSubmitMessageSize(3);
        subject.writeQueueSizeRecordStream(4);
        subject.hashQueueSizeRecordStream(5);
        subject.recordGasPerConsSec(6L);

        verify(retries).recordValue(1.0);
        verify(waitMs).recordValue(2.0);
        verify(submitSizes).recordValue(3.0);
        verify(queueSize).recordValue(4.0);
        verify(hashS).recordValue(5);
        verify(gasPerSec).recordValue(6L);
    }
}
