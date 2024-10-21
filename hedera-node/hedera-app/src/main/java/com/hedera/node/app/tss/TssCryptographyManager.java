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

import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.roster.ReadableRosterStore;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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

    private NodeId nodeId;
    private final TssLibrary tssLibrary;
    private TssParticipantDirectory tssParticipantDirectory;
    private AppContext.LedgerSigner ledgerSigner;
    public TssCryptographyManager(
            @NonNull final NodeId nodeId,
            @NonNull final TssLibrary tssLibrary,
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final AppContext.LedgerSigner ledgerSigner) {
        this.nodeId = nodeId;
        this.tssLibrary = tssLibrary;
        this.tssParticipantDirectory = tssParticipantDirectory;
        this.ledgerSigner = ledgerSigner;
    }

    /**
     * Submit TSS message transactions to the transaction pool
     */
    public CompletableFuture<LedgerIdAndSignature> handleTssMessageTransaction(
            @NonNull final TssMessageTransactionBody op,
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final HandleContext context) {
        final var tssStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);

        final var targetRosterHash = op.targetRosterHash();
        final var sourceRosterHash = op.sourceRosterHash();

        final var tssMessageBodies = tssStore.getTssMessages(targetRosterHash);

        final var hasVoteSubmitted = tssStore.getVote(TssVoteMapKey.newBuilder()
                        .nodeId(nodeId.id())
                        .rosterHash(targetRosterHash)
                        .build())
                != null;
        // If the node didn't submit a TssVoteTransaction, validate all TssMessages and compute the vote bit set
        // to see if threshold is met
        // FUTURE: Add !votingClosed.contains(targetRosterHash)
        if (!hasVoteSubmitted) {
            final var rosterEntries = rosterStore.get(sourceRosterHash).rosterEntries();
            final TssConfig tssConfig = context.configuration().getConfigData(TssConfig.class);
            final var maxSharesPerNode = tssConfig.maxSharesPerNode();
            // Validate the TSSMessages and if the threshold is met and public keys for a target roster have
            // not been computed yet compute the public keys
            return computePublicKeysAndSignIfThresholdMet(tssMessageBodies, maxSharesPerNode, rosterEntries)
                    .exceptionally(e -> {
                        log.error("Error computing public keys and signing", e);
                        return null;
                    });
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<LedgerIdAndSignature> computePublicKeysAndSignIfThresholdMet(
            @NonNull final List<TssMessageTransactionBody> tssMessageBodies,
            @NonNull final long maxSharesPerNode,
            @NonNull final List<RosterEntry> rosterEntries) {
        return CompletableFuture.supplyAsync(() -> {
            // validate TSS transactions and set the vote bit set.
            // This could be an in memory data structure in the future
            final var validTssMessages = validateTssMessages(tssMessageBodies);
            boolean tssMessageThresholdMet = isThresholdMet(validTssMessages, maxSharesPerNode, rosterEntries);
            if (!tssMessageThresholdMet) {
                return null;
            }
            final var tssMessages = validTssMessages.stream()
                    .map(TssMessageTransactionBody::tssMessage)
                    .map(k -> new TssMessage(k.toByteArray()))
                    .toList();
            final var computedPublicShares = tssLibrary.computePublicShares(tssParticipantDirectory, tssMessages);

            // compute the ledger id
            final var ledgerId = tssLibrary.aggregatePublicShares(computedPublicShares);
            // final var recoveredPrivateShares = tssLibrary.decryptPrivateShares(tssParticipantDirectory, tssMessages);
            //  copyPrivateShares(targetRosterHash, sourceRosterHash, recoveredPrivateShares);

            // If the target roster hash is not in the votingClosed set, and there is no TssVoteTransaction
            // from this node.
            // FUTURE: Add !votingClosed.contains(targetRosterHash)
            final var signature = ledgerSigner.sign(ledgerId.publicKey().toBytes());

            final BitSet tssVoteBitSet = computeTssVoteBitSet(validTssMessages);
            return new LedgerIdAndSignature(ledgerId, signature, tssVoteBitSet);
        });
    }

    private List<TssMessageTransactionBody> validateTssMessages(
            @NonNull final List<TssMessageTransactionBody> tssMessages) {
        final var validTssMessages = new LinkedList<TssMessageTransactionBody>();
        for (TssMessageTransactionBody op : tssMessages) {
            final var isValid = tssLibrary.verifyTssMessage(
                    tssParticipantDirectory, new TssMessage(op.tssMessage().toByteArray()));
            if (isValid) {
                validTssMessages.add(op);
            }
        }
        return validTssMessages;
    }

    private BitSet computeTssVoteBitSet(@NonNull final List<TssMessageTransactionBody> tssMessageBodies) {
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
            final long maxTssMessagesPerNode,
            @NonNull final List<RosterEntry> rosterEntries) {
        final var nodeShareCounts = sharesFromWeight(rosterEntries, maxTssMessagesPerNode);
        final var numShares =
                nodeShareCounts.values().stream().mapToLong(Long::longValue).sum();

        // If more than 1/2 the consensus weight has been received, then the threshold is met
        return validTssMessages.size() >= ((numShares + 2) / 2);
    }

    public static Map<Long, Long> sharesFromWeight(
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

    public record LedgerIdAndSignature(
            @NonNull PairingPublicKey ledgerId, @NonNull Signature signature, @NonNull BitSet tssVoteBitSet) {}
}
