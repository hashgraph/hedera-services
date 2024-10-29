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

import static com.hedera.node.app.tss.handlers.TssUtils.getThresholdForTssMessages;
import static com.hedera.node.app.tss.handlers.TssUtils.getTssMessages;
import static com.hedera.node.app.tss.handlers.TssUtils.validateTssMessages;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.swirlds.common.crypto.Signature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.InstantSource;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
    private Executor libraryExecutor;
    private TssMetrics tssMetrics;

    @Inject
    public TssCryptographyManager(
            @NonNull final TssLibrary tssLibrary,
            @NonNull final AppContext.Gossip gossip,
            @NonNull @TssLibraryExecutor final Executor libraryExecutor,
            @NonNull final TssMetrics tssMetrics) {
        this.tssLibrary = requireNonNull(tssLibrary);
        this.gossip = requireNonNull(gossip);
        this.libraryExecutor = requireNonNull(libraryExecutor);
        this.tssMetrics = requireNonNull(tssMetrics);
    }

    /**
     * Handles a TssMessageTransaction.
     * This method validates the TssMessages and computes the ledger id if the threshold
     * is met.
     * Then signs the ledgerId with the node's RSA key and returns the signature with the computed ledgerID.
     * If the threshold is not met, the method returns null.
     * The most expensive operations involving {@link TssLibrary} are
     * executed asynchronously.
     *
     * @param op                      the TssMessageTransaction
     * @param tssParticipantDirectory the TSS participant directory
     * @param context                 the handle context
     * @return a CompletableFuture containing the ledger id and signature if the threshold is met, null otherwise
     */
    public CompletableFuture<LedgerIdWithSignature> handleTssMessageTransaction(
            @NonNull final TssMessageTransactionBody op,
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final HandleContext context) {
        final var tssStore = context.storeFactory().writableStore(WritableTssStore.class);
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

    /**
     * Compute and sign the ledger id if the threshold is met. If the threshold is not met, return null.
     * The most expensive operations involving {@link TssLibrary} are executed asynchronously.
     *
     * @param tssMessageBodies        the list of TSS messages
     * @param tssParticipantDirectory the TSS participant directory
     * @return a CompletableFuture containing the ledger id and signature if the threshold is met, null otherwise
     */
    private CompletableFuture<LedgerIdWithSignature> computeAndSignLedgerIdIfApplicable(
            @NonNull final List<TssMessageTransactionBody> tssMessageBodies,
            final TssParticipantDirectory tssParticipantDirectory) {
        return CompletableFuture.supplyAsync(
                () -> {
                    // Validate TSS transactions and set the vote bit set.
                    final var validTssOps = validateTssMessages(tssMessageBodies, tssParticipantDirectory, tssLibrary);
                    boolean tssMessageThresholdMet = isThresholdMet(validTssOps, tssParticipantDirectory);

                    // If the threshold is not met, return
                    if (!tssMessageThresholdMet) {
                        return null;
                    }
                    final var aggregationCalculationStart =
                            InstantSource.system().instant();
                    final var validTssMessages = getTssMessages(validTssOps);
                    final var computedPublicShares =
                            tssLibrary.computePublicShares(tssParticipantDirectory, validTssMessages);

                    // compute the ledger id and sign it
                    final var ledgerId = tssLibrary.aggregatePublicShares(computedPublicShares);
                    final var signature = gossip.sign(ledgerId.publicKey().toBytes());

                    final BitSet tssVoteBitSet = computeTssVoteBitSet(validTssOps);

                    final var aggregationCalculationEnd = InstantSource.system().instant();
                    final var durationOfAggregation = Duration.between(
                                    aggregationCalculationStart, aggregationCalculationEnd)
                            .toSeconds();
                    tssMetrics.updateAggregationTime(durationOfAggregation);

                    return new LedgerIdWithSignature(ledgerId, signature, tssVoteBitSet);
                },
                libraryExecutor);
    }

    /**
     * Compute the TSS vote bit set. No need to validate the TSS messages here as they have already been validated.
     *
     * @param validIssBodies the valid TSS messages
     * @return the TSS vote bit set
     */
    private BitSet computeTssVoteBitSet(@NonNull final List<TssMessageTransactionBody> validIssBodies) {
        final var tssVoteBitSet = new BitSet();
        for (TssMessageTransactionBody op : validIssBodies) {
            tssVoteBitSet.set((int) op.shareIndex());
        }
        return tssVoteBitSet;
    }

    /**
     * Check if the threshold consensus weight is met to submit a {@link TssVoteTransactionBody}.
     * The threshold is met if more than half the consensus weight has been received.
     *
     * @param validTssMessages        the valid TSS messages
     * @param tssParticipantDirectory the TSS participant directory
     * @return true if the threshold is met, false otherwise
     */
    private boolean isThresholdMet(
            @NonNull final List<TssMessageTransactionBody> validTssMessages,
            @NonNull final TssParticipantDirectory tssParticipantDirectory) {
        final var numShares = tssParticipantDirectory.getShareIds().size();
        // If more than 1/2 the consensus weight has been received, then the threshold is met
        return validTssMessages.size() >= getThresholdForTssMessages(numShares);
    }

    /**
     * A record containing the ledger id, signature, and TSS vote bit set to be used in generating {@link TssVoteTransactionBody}.
     */
    public record LedgerIdWithSignature(
            @NonNull PairingPublicKey ledgerId, @NonNull Signature signature, @NonNull BitSet tssVoteBitSet) {}
}
