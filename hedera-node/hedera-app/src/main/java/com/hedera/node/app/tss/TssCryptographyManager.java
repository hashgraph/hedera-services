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

package com.hedera.node.app.tss;

import static com.hedera.node.app.tss.handlers.TssUtils.getTssMessages;
import static com.hedera.node.app.tss.handlers.TssUtils.validateTssMessages;

import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import com.swirlds.common.crypto.Signature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is yet to be implemented
 */
@Singleton
public class TssCryptographyManager {
    private static final Logger log = LogManager.getLogger(TssCryptographyManager.class);
    private final TssLibrary tssLibrary;
    private AppContext.Gossip gossip;

    @Inject
    public TssCryptographyManager(@NonNull final TssLibrary tssLibrary, @NonNull final AppContext.Gossip gossip) {
        this.tssLibrary = tssLibrary;
        this.gossip = gossip;
    }

    /**
     * Submit TSS message transactions to the transaction pool
     */
    public CompletableFuture<LedgerIdWithSignature> handleTssMessageTransaction(
            @NonNull final TssMessageTransactionBody op,
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final HandleContext context) {
        final var tssStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var targetRosterHash = op.targetRosterHash();
        final var tssMessageBodies = tssStore.getTssMessages(targetRosterHash);

        final var isVoteSubmitted = tssStore.getVote(TssVoteMapKey.newBuilder()
                        .nodeId(context.networkInfo().selfNodeInfo().nodeId())
                        .rosterHash(targetRosterHash)
                        .build())
                != null;
        // If the node didn't submit a TssVoteTransaction, validate all TssMessages and compute the vote bit set
        // to see if a threshold is met
        if (!isVoteSubmitted) {
            return computeAndSignLedgerIdIfApplicable(tssMessageBodies, tssParticipantDirectory)
                    .exceptionally(e -> {
                        log.error("Error computing public keys and signing", e);
                        return null;
                    });
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<LedgerIdWithSignature> computeAndSignLedgerIdIfApplicable(
            @NonNull final List<TssMessageTransactionBody> tssMessageBodies,
            final TssParticipantDirectory tssParticipantDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            // Validate TSS transactions and set the vote bit set.
            final var validTssOps = validateTssMessages(tssMessageBodies, tssParticipantDirectory, tssLibrary);
            boolean tssMessageThresholdMet = isThresholdMet(validTssOps, tssParticipantDirectory);

            // If the threshold is not met, return
            if (!tssMessageThresholdMet) {
                return null;
            }
            final var validTssMessages = getTssMessages(validTssOps);
            final var computedPublicShares = tssLibrary.computePublicShares(tssParticipantDirectory, validTssMessages);

            // compute the ledger id and sign it
            final var ledgerId = tssLibrary.aggregatePublicShares(computedPublicShares);
            final var signature = gossip.sign(ledgerId.publicKey().toBytes());

            final BitSet tssVoteBitSet = computeTssVoteBitSet(validTssOps, tssParticipantDirectory);
            return new LedgerIdWithSignature(ledgerId, signature, tssVoteBitSet);
        });
    }

    private BitSet computeTssVoteBitSet(
            @NonNull final List<TssMessageTransactionBody> tssMessageBodies,
            final TssParticipantDirectory tssParticipantDirectory) {
        final var tssVoteBitSet = new BitSet();
        for (TssMessageTransactionBody op : tssMessageBodies) {
            final var isValid = tssLibrary.verifyTssMessage(
                    tssParticipantDirectory, new TssMessage(op.tssMessage().toByteArray()));
            if (!isValid) {
                tssVoteBitSet.set((int) op.shareIndex());
            }
        }
        return tssVoteBitSet;
    }

    private boolean isThresholdMet(
            @NonNull final List<TssMessageTransactionBody> validTssMessages,
            @NonNull final TssParticipantDirectory tssParticipantDirectory) {
        final var numShares = tssParticipantDirectory.getShareIds().size();
        // If more than 1/2 the consensus weight has been received, then the threshold is met
        return validTssMessages.size() >= ((numShares + 2) / 2);
    }

    public static Map<Long, Long> computeNodeShares(
            @NonNull final List<RosterEntry> rosterEntries, final long maxTssMessagesPerNode) {
        final var maxWeight =
                rosterEntries.stream().mapToLong(RosterEntry::weight).max().orElse(0);
        final var shares = new LinkedHashMap<Long, Long>();
        for (final var entry : rosterEntries) {
            final var numShares = ((maxTssMessagesPerNode * entry.weight() + maxWeight - 1) / maxWeight);
            shares.put(entry.nodeId(), numShares);
        }
        return shares;
    }

    public record LedgerIdWithSignature(
            @NonNull PairingPublicKey ledgerId, @NonNull Signature signature, @NonNull BitSet tssVoteBitSet) {}
}
