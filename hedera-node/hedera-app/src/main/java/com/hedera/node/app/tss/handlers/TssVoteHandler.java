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

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates and responds to a {@link TssVoteTransactionBody}.
 * <p>Tracked <a href="https://github.com/hashgraph/hedera-services/issues/14750">here</a>
 */
@Singleton
public class TssVoteHandler implements TransactionHandler {

    public static final double CONSENSUS_VOTE_THRESHOLD_ONE_THIRD = 3.0;

    @Inject
    public TssVoteHandler() {
        // Dagger2
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
        final var tssBaseStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var nodeId = context.networkInfo().selfNodeInfo().nodeId();
        final TssVoteMapKey tssVoteMapKey = new TssVoteMapKey(txBody.targetRosterHash(), nodeId);
        if (tssBaseStore.exists(tssVoteMapKey)) {
            // Duplicate vote
            return;
        }

        if (!TssVoteHandler.hasReachedThreshold(txBody, context, CONSENSUS_VOTE_THRESHOLD_ONE_THIRD)) {
            tssBaseStore.put(tssVoteMapKey, txBody);
        }
    }

    /**
     * Check if the threshold number of votes (totaling at least 1/thresholdDenominator of weight) have already been received for the
     * candidate roster, all with the same vote byte array.
     *
     * @param tssVoteTransaction the TssVoteTransaction to check
     * @param context the HandleContext
     * @param thresholdDenominator the denominator of the threshold fraction
     * @return true if the threshold has been reached, false otherwise
     */
    public static boolean hasReachedThreshold(
            TssVoteTransactionBody tssVoteTransaction, HandleContext context, double thresholdDenominator) {
        final var tssBaseStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);

        // Get the target roster from the TssVoteTransactionBody
        Bytes targetRosterHash = tssVoteTransaction.targetRosterHash();

        // Get all votes for the active roster
        Map<RosterEntry, TssVoteTransactionBody> voteByNode = new HashMap<>();

        // Also get the total active roster weight
        long activeRosterTotalWeight = 0;

        Roster activeRoster = rosterStore.getActiveRoster();
        if (activeRoster == null) {
            throw new IllegalArgumentException("No active roster found");
        }

        // For every node in the active roster, check if there is a vote for the target roster hash
        for (RosterEntry rosterEntry : rosterStore.getActiveRoster().rosterEntries()) {
            activeRosterTotalWeight += rosterEntry.weight();
            final TssVoteMapKey tssVoteMapKey = new TssVoteMapKey(targetRosterHash, rosterEntry.nodeId());
            if (tssBaseStore.exists(tssVoteMapKey)) {
                voteByNode.put(rosterEntry, tssBaseStore.getVote(tssVoteMapKey));
            }
        }

        // Initialize a counter for the total weight of votes with the same vote byte array
        long voteWeight = 0L;

        // Iterate over the votes which has the same target roster hash
        for (RosterEntry rosterEntryKey : voteByNode.keySet()) {
            final TssVoteTransactionBody vote = voteByNode.get(rosterEntryKey);
            // If the vote byte array matches the one in the TssVoteTransaction, add the weight of the vote to the
            // counter
            if (vote.tssVote().equals(tssVoteTransaction.tssVote())) {
                voteWeight += rosterEntryKey.weight();
            }
        }

        // Check if the total weight of votes with the same vote byte array is at least 1/thresholdDenominator of the
        // total weight of the
        // network
        return voteWeight >= activeRosterTotalWeight / CONSENSUS_VOTE_THRESHOLD_ONE_THIRD;
    }
}
