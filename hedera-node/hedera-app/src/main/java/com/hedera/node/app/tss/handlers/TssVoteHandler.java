/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates and responds to a {@link TssVoteTransactionBody}.
 */
@Singleton
public class TssVoteHandler implements TransactionHandler {

    public static final long THRESHOLD_DENOMINATOR_1_3 = 3L;
    private final TssCryptographyManager tssCryptographyManager;

    @Inject
    public TssVoteHandler(@NonNull final TssCryptographyManager tssCryptographyManager) {
        // Dagger2
        this.tssCryptographyManager = tssCryptographyManager;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txBody = context.body().tssVoteOrThrow();
        // Check if the sourceRosterHash and targetRosterHash correspond to existing rosters in the system?
        //   RosterStateId.ROSTER_STATES_KEY

        // 1. If a threshold number of votes (totaling at least 1/3 of weight), all with the same vote byte array,
        //    have already been received for the candidate roster, then discard the TssVoteTransaction.
        if (hasReachedThreshold(txBody, context, THRESHOLD_DENOMINATOR_1_3)) {
            return;
        }

        final var nodeId = context.networkInfo().selfNodeInfo().nodeId();
        final TssVoteMapKey tssVoteMapKey = new TssVoteMapKey(txBody.targetRosterHash(), nodeId);

        final var tssBaseStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        // 2. Insert into the TssVoteMap if it is correct to do so.
        //   Don’t insert multiple votes from the same node; discard it if it is redundant.
        if (!tssBaseStore.exists(tssVoteMapKey)) {
            tssBaseStore.put(tssVoteMapKey, txBody);

            // 3. Send the TssVoteTransaction to the TssCryptographyManager.
            tssCryptographyManager.onTssVoteTransaction(txBody, context);
        }
    }

    /**
     * Check if the threshold number of votes (totaling at least 1/3 of weight) have already been received for the
     * candidate roster, all with the same vote byte array.
     *
     * @param tssVoteTransaction the TssVoteTransaction to check
     * @param context the HandleContext
     * @return true if the threshold has been reached, false otherwise
     */
    public static boolean hasReachedThreshold(
            TssVoteTransactionBody tssVoteTransaction, HandleContext context, long thresholdDenominator) {
        final var tssBaseStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var stakingInfoStore = context.storeFactory().readableStore(ReadableStakingInfoStore.class);

        // Get the target roster from the TssVoteTransactionBody
        Bytes candidateRosterHash = tssVoteTransaction.targetRosterHash();

        // Get all votes for the candidate roster
        Map<StakingNodeInfo, TssVoteTransactionBody> votes = new HashMap<>();
        Set<Long> nodeIds = stakingInfoStore.getAll();
        for (long nodeId : nodeIds) {
            StakingNodeInfo stakingNodeInfo = stakingInfoStore.get(nodeId);
            if (stakingNodeInfo != null && !stakingNodeInfo.deleted()) {
                final TssVoteMapKey tssVoteMapKey = new TssVoteMapKey(candidateRosterHash, nodeId);
                if (tssBaseStore.exists(tssVoteMapKey)) {
                    votes.put(stakingNodeInfo, tssBaseStore.getVote(tssVoteMapKey));
                }
            }
        }

        // Calculate the total weight of the network
        final var totalWeight =
                context.configuration().getConfigData(StakingConfig.class).sumOfConsensusWeights();

        // Initialize a counter for the total weight of votes with the same vote byte array
        long voteWeight = 0L;

        // Iterate over the votes
        for (StakingNodeInfo stakingNodeInfo : votes.keySet()) {
            final var vote = votes.get(stakingNodeInfo);
            // If the vote byte array matches the one in the TssVoteTransaction, add the weight of the vote to the
            // counter
            if (vote.tssVote().equals(tssVoteTransaction.tssVote())) {
                voteWeight += stakingNodeInfo.weight();
            }
        }

        // Check if the total weight of votes with the same vote byte array is at least 1/thresholdDenominator of the
        // total weight of the
        // network
        return voteWeight >= totalWeight / thresholdDenominator;
    }
}
