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

import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.replay.ConsensusTxn;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Base64;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A version of {@link ProcessLogic} that records all observed consensus transactions to a
 * replay asset for later use in verifying modularized business logic with replay facilities.
 */
public class RecordingProcessLogic implements ProcessLogic {
    private static final Logger log = LogManager.getLogger(RecordingProcessLogic.class);
    public static final String REPLAY_TRANSACTIONS_ASSET = "replay-transactions.txt";

    private final ProcessLogic delegate;
    private final ReplayAssetRecording assetRecording;

    public RecordingProcessLogic(
            @NonNull final ProcessLogic delegate, @NonNull final ReplayAssetRecording assetRecording) {
        this.delegate = Objects.requireNonNull(delegate);
        this.assetRecording = assetRecording;
        log.info("Process logic recording is enabled");
    }

    @Override
    public void incorporateConsensusTxn(@NonNull final ConsensusTransaction platformTxn, final long submittingMember) {
        final var next = new ConsensusTxn();
        next.setB64Transaction(Base64.getEncoder().encodeToString(platformTxn.getContents()));
        next.setMemberId(submittingMember);
        next.setConsensusTimestamp(platformTxn.getConsensusTimestamp().toString());
        assetRecording.appendJsonToAsset(REPLAY_TRANSACTIONS_ASSET, next);
        delegate.incorporateConsensusTxn(platformTxn, submittingMember);
    }
}
