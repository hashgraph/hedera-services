package com.hedera.node.app.service.mono.state.logic;

import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static com.hedera.node.app.service.mono.state.logic.RecordingProcessLogic.REPLAY_TRANSACTIONS_ASSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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