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

package com.hedera.node.app.service.mono.state.migration;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.context.StateChildren;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.utils.replay.PbjLeafConverters;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Objects;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toB64Encoding;
import static com.hedera.node.app.service.mono.state.submerkle.RecordingSequenceNumber.REPLAY_SEQ_NOS_ASSET;

public class RecordingMigrationManager implements MigrationManager {
    private static final Logger log = LogManager.getLogger(RecordingMigrationManager.class);
    public static final String INITIAL_ACCOUNTS_ASSET = "initial-accounts.txt";

    private final StateChildren stateChildren;
    private final MigrationManager delegate;
    private final ReplayAssetRecording assetRecording;

    public RecordingMigrationManager(
            @NonNull final MigrationManager delegate,
            @NonNull final StateChildren stateChildren,
            @NonNull final ReplayAssetRecording assetRecording) {
        this.delegate = Objects.requireNonNull(delegate);
        this.stateChildren = Objects.requireNonNull(stateChildren);
        this.assetRecording = assetRecording;
    }

    @Override
    public void publishMigrationRecords(@NonNull final Instant now) {
        delegate.publishMigrationRecords(now);
        // Restart the recording for sequence numbers, since we only want to replay for user-submitted transactions
        assetRecording.restartReplayAsset(REPLAY_SEQ_NOS_ASSET);

        log.info("Recording {} initial system accounts", stateChildren.accounts().size());
        stateChildren.accounts().forEach((num, account) -> {
            final var pbjAccount = PbjLeafConverters.accountFromMerkle((MerkleAccount) account);
            assetRecording.appendPlaintextToAsset(INITIAL_ACCOUNTS_ASSET, toB64Encoding(pbjAccount, Account.class));
        });
    }
}
