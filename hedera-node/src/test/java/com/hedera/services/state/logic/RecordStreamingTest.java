/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.state.logic;

import static com.hedera.services.state.logic.RecordStreaming.PENDING_USER_TXN_BLOCK_NO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;
import com.swirlds.common.crypto.RunningHash;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordStreamingTest {
    @Mock private RecordStreamObject topLevelRso;
    @Mock private RecordStreamObject firstFollowingChildRso;
    @Mock private RecordStreamObject secondFollowingChildRso;
    @Mock private RecordStreamObject firstPrecedingChildRso;
    @Mock private RecordStreamObject systemRso;
    @Mock private BlockManager blockManager;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private NonBlockingHandoff nonBlockingHandoff;

    private RecordStreaming subject;

    @BeforeEach
    void setUp() {
        subject = new RecordStreaming(blockManager, recordsHistorian, nonBlockingHandoff);
    }

    @Test
    void streamsAllRecordsAtExpectedTimes() {
        givenCollabSetup();
        givenAlignable(
                firstPrecedingChildRso,
                topLevelRso,
                firstFollowingChildRso,
                secondFollowingChildRso,
                systemRso);
        given(systemRso.getRunningHash()).willReturn(mockSystemHash);

        given(recordsHistorian.hasPrecedingChildRecords()).willReturn(true);
        given(recordsHistorian.getPrecedingChildRecords())
                .willReturn(List.of(firstPrecedingChildRso));
        given(recordsHistorian.hasFollowingChildRecords()).willReturn(true);
        given(recordsHistorian.getTopLevelRecord()).willReturn(topLevelRso);
        given(nonBlockingHandoff.offer(topLevelRso)).willReturn(true);
        given(recordsHistorian.getFollowingChildRecords())
                .willReturn(List.of(firstFollowingChildRso, secondFollowingChildRso));
        given(nonBlockingHandoff.offer(firstPrecedingChildRso)).willReturn(true);
        given(nonBlockingHandoff.offer(firstFollowingChildRso)).willReturn(true);
        given(nonBlockingHandoff.offer(secondFollowingChildRso)).willReturn(true);
        given(nonBlockingHandoff.offer(systemRso)).willReturn(true);

        subject.streamUserTxnRecords();
        subject.streamSystemRecord(systemRso);

        verify(nonBlockingHandoff).offer(firstPrecedingChildRso);
        verify(nonBlockingHandoff).offer(firstFollowingChildRso);
        verify(nonBlockingHandoff).offer(topLevelRso);
        verify(nonBlockingHandoff).offer(secondFollowingChildRso);
        verify(nonBlockingHandoff).offer(systemRso);
        verify(blockManager).updateCurrentBlockHash(mockUserHash);
        verify(blockManager).updateCurrentBlockHash(mockSystemHash);
    }

    @Test
    void streamsJustTopLevelWithNoChildrenAvail() {
        givenCollabSetup();
        givenAlignable(topLevelRso);

        given(recordsHistorian.getTopLevelRecord()).willReturn(topLevelRso);
        given(nonBlockingHandoff.offer(topLevelRso)).willReturn(false).willReturn(true);

        subject.streamUserTxnRecords();

        verify(nonBlockingHandoff, times(2)).offer(topLevelRso);
        verify(blockManager).updateCurrentBlockHash(mockUserHash);

        subject.resetBlockNo();

        assertEquals(PENDING_USER_TXN_BLOCK_NO, subject.getBlockNo());
    }

    @Test
    void usesCurrentBlockNumberIfNoUserRecordsCouldBeStreamed() {
        given(blockManager.getAlignmentBlockNumber()).willReturn(someBlockNo);
        givenAlignable(systemRso);
        given(systemRso.getRunningHash()).willReturn(mockSystemHash);
        given(nonBlockingHandoff.offer(systemRso)).willReturn(true);

        subject.streamSystemRecord(systemRso);

        verify(nonBlockingHandoff).offer(systemRso);
        verify(blockManager).updateCurrentBlockHash(mockSystemHash);
    }

    private void givenCollabSetup() {
        given(recordsHistorian.getTopLevelRecord()).willReturn(topLevelRso);
        given(topLevelRso.getTimestamp()).willReturn(aTime);
        given(blockManager.updateAndGetAlignmentBlockNumber(aTime)).willReturn(someBlockNo);
        given(recordsHistorian.lastRunningHash()).willReturn(mockUserHash);
    }

    private void givenAlignable(final RecordStreamObject... mockRsos) {
        for (final var mockRso : mockRsos) {
            given(mockRso.withBlockNumber(someBlockNo)).willReturn(mockRso);
        }
    }

    private static final Instant aTime = Instant.ofEpochSecond(1_234_567L, 890);
    private static final long someBlockNo = 123_456;
    private static final RunningHash mockUserHash = new RunningHash();
    private static final RunningHash mockSystemHash = new RunningHash();
}
