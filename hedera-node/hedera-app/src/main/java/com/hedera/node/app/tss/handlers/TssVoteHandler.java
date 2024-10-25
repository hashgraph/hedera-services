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
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates and responds to a {@link TssVoteTransactionBody}.
 * <p>Tracked <a href="https://github.com/hashgraph/hedera-services/issues/14750">here</a>
 */
@Singleton
public class TssVoteHandler implements TransactionHandler {

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
        final var tssBaseStore = context.storeFactory().writableStore(WritableTssStore.class);
        final TssVoteMapKey tssVoteMapKey = new TssVoteMapKey(
                txBody.targetRosterHash(), context.creatorInfo().nodeId());
        if (tssBaseStore.exists(tssVoteMapKey)) {
            // Duplicate vote
            return;
        }

        if (!TssVoteHandler.hasReachedThreshold(txBody, context)) {
            tssBaseStore.put(tssVoteMapKey, txBody);
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
            @NonNull final TssVoteTransactionBody tssVoteTransaction, @NonNull final HandleContext context) {
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);

        final Roster activeRoster = rosterStore.getActiveRoster();
        if (activeRoster == null) {
            throw new IllegalArgumentException("No active roster found");
        }
        // Get the target roster from the TssVoteTransactionBody
        final Bytes targetRosterHash = tssVoteTransaction.targetRosterHash();

        // Also get the total active roster weight
        long activeRosterTotalWeight = 0;
        // Initialize a counter for the total weight of votes with the same vote byte array
        long voteWeight = 0L;
        final var tssBaseStore = context.storeFactory().writableStore(WritableTssStore.class);
        // For every node in the active roster, check if there is a vote for the target roster hash
        for (final RosterEntry rosterEntry : activeRoster.rosterEntries()) {
            activeRosterTotalWeight += rosterEntry.weight();
            final var tssVoteMapKey = new TssVoteMapKey(targetRosterHash, rosterEntry.nodeId());
            if (tssBaseStore.exists(tssVoteMapKey)) {
                final var vote = tssBaseStore.getVote(tssVoteMapKey);
                // If the vote byte array matches the one in the TssVoteTransaction, add the weight of the vote to the
                // counter
                if (vote.tssVote().equals(tssVoteTransaction.tssVote())) {
                    voteWeight += rosterEntry.weight();
                }
            }
        }

        // Check if the total weight of votes with the same vote byte array is at least 1/3 of the
        // total weight of the network
        // Adding a +1 to the threshold to account for rounding errors.
        return voteWeight >= (activeRosterTotalWeight / 3) + ((activeRosterTotalWeight % 3) == 0 ? 0 : 1);
    }
}
