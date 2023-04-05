package com.hedera.node.app.service.mono.state.logic;

import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Base64;
import java.util.Objects;

/**
 * A version of {@link ProcessLogic} that records all observed consensus transactions to a
 * replay asset for later use in verifying modularized business logic with replay facilities.
 */
public class RecordingProcessLogic implements ProcessLogic {
    private static final Logger log = LogManager.getLogger(RecordingProcessLogic.class);
    public static final String REPLAY_TRANSACTIONS_ASSET = "replay-transactions.json";

    private final ProcessLogic delegate;
    private final ReplayAssetRecording assetRecording;

    public RecordingProcessLogic(
            @NonNull final ProcessLogic delegate,
            @NonNull final ReplayAssetRecording assetRecording) {
        this.delegate = Objects.requireNonNull(delegate);
        this.assetRecording = assetRecording;
        log.info("Process logic recording is enabled");
    }

    @Override
    public void incorporateConsensusTxn(
            @NonNull final ConsensusTransaction platformTxn,
            final long submittingMember) {
        final var next = new ConsensusTxn();
        next.setB64Transaction(Base64.getEncoder().encodeToString(platformTxn.getContents()));
        next.setMemberId(submittingMember);
        next.setConsensusTimestamp(platformTxn.getConsensusTimestamp().toString());
        assetRecording.appendJsonLineToReplayAsset(REPLAY_TRANSACTIONS_ASSET, next);
        delegate.incorporateConsensusTxn(platformTxn, submittingMember);
    }
}
