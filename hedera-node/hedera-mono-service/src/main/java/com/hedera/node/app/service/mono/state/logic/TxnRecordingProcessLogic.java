package com.hedera.node.app.service.mono.state.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class TxnRecordingProcessLogic implements ProcessLogic {
    public static final String REPLAY_TRANSACTIONS_FILE = "replay-transactions.json";

    private final ProcessLogic delegate;
    private final List<ConsensusTxn> consensusTxns = new ArrayList<>();

    public TxnRecordingProcessLogic(@NonNull final ProcessLogic delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public void incorporateConsensusTxn(
            @NonNull final ConsensusTransaction platformTxn,
            final long submittingMember) {
        final var next = new ConsensusTxn();
        next.setB64Transaction(Base64.getEncoder().encodeToString(platformTxn.getContents()));
        next.setMemberId(submittingMember);
        consensusTxns.add(next);
        delegate.incorporateConsensusTxn(platformTxn, submittingMember);
    }

    public void recordTo(final File replayDir) throws IOException {
        final var replayTxnLoc = Paths.get(
                replayDir.toPath().toAbsolutePath().toString(),
                REPLAY_TRANSACTIONS_FILE);
        final var om = new ObjectMapper();
        om.writer().writeValue(Files.newOutputStream(replayTxnLoc), consensusTxns);
    }
}
