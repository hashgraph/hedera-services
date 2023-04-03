package com.hedera.node.app.service.mono.state.logic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static com.hedera.node.app.service.mono.state.logic.TxnRecordingProcessLogic.REPLAY_TRANSACTIONS_FILE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TxnRecordingProcessLogicTest {
    @Mock
    private ProcessLogic delegate;
    @Mock
    private ConsensusTransactionImpl txn;
    @TempDir
    private File tempDir;

    private TxnRecordingProcessLogic subject;

    @BeforeEach
    void setUp() {
        subject = new TxnRecordingProcessLogic(delegate);
    }

    @Test
    void writesObservedConsensusTxns() throws IOException {
        final var contents = "abcabcabcabcabcabcabcabcabcabc".getBytes(StandardCharsets.UTF_8);
        given(txn.getContents()).willReturn(contents);
        final var memberId = 123L;
        subject.incorporateConsensusTxn(txn, memberId);
        verify(delegate).incorporateConsensusTxn(txn, memberId);

        // and:
        subject.recordTo(tempDir);
        final var om = new ObjectMapper();
        final var replayTxnsLoc = Paths.get(
                tempDir.toPath().toAbsolutePath().toString(),
                REPLAY_TRANSACTIONS_FILE);
        @SuppressWarnings("unchecked")
        final var written = (List<ConsensusTxn>)
                om.readValue(
                        Files.newInputStream(replayTxnsLoc),
                        new TypeReference<List<ConsensusTxn>>() {});
        assertEquals(1, written.size());
        final var txn = written.get(0);
        assertEquals(memberId, txn.getMemberId());
        final var writtenContents = Base64.getDecoder().decode(txn.getB64Transaction());
        assertArrayEquals(contents, writtenContents);
    }
}