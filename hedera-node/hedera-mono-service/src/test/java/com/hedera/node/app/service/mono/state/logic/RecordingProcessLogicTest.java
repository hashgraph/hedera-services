/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.logic;

import static com.hedera.node.app.service.mono.state.logic.RecordingProcessLogic.REPLAY_TRANSACTIONS_ASSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingProcessLogicTest {
    @Mock
    private ProcessLogic delegate;

    @Mock
    private ConsensusTransactionImpl txn;

    @Mock
    private ReplayAssetRecording assetRecording;

    private RecordingProcessLogic subject;

    @BeforeEach
    void setUp() {
        subject = new RecordingProcessLogic(delegate, assetRecording);
    }

    @Test
    void writesObservedConsensusTxns() {
        final var consensusTimestamp = Instant.ofEpochSecond(1_234_567L);
        final var captor = ArgumentCaptor.forClass(ConsensusTxn.class);
        final var contents = "abcabcabcabcabcabcabcabcabcabc".getBytes(StandardCharsets.UTF_8);
        final var encodedContents = Base64.getEncoder().encodeToString(contents);
        given(txn.getContents()).willReturn(contents);
        given(txn.getConsensusTimestamp()).willReturn(consensusTimestamp);
        final var memberId = 123L;
        subject.incorporateConsensusTxn(txn, memberId);
        verify(delegate).incorporateConsensusTxn(txn, memberId);
        verify(assetRecording).appendJsonLineToReplayAsset(eq(REPLAY_TRANSACTIONS_ASSET), captor.capture());

        final var observed = captor.getValue();
        assertEquals(memberId, observed.getMemberId());
        assertEquals(consensusTimestamp, observed.getConsensusTimestamp());
        assertEquals(encodedContents, observed.getB64Transaction());
    }
}
