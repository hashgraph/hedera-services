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

import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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

    private final Executor libraryExecutor;
    private final TssMetrics tssMetrics;
    private final TssLibrary tssLibrary;
    private final InstantSource instantSource;
    private final AppContext.Gossip gossip;

    @Inject
    public TssCryptographyManager(
            @NonNull final TssLibrary tssLibrary,
            @NonNull final AppContext appContext,
            @NonNull @TssLibraryExecutor final Executor libraryExecutor,
            @NonNull final TssMetrics tssMetrics,
            @NonNull final InstantSource instantSource) {
        this.tssLibrary = requireNonNull(tssLibrary);
        this.gossip = requireNonNull(appContext.gossip());
        this.libraryExecutor = requireNonNull(libraryExecutor);
        this.tssMetrics = requireNonNull(tssMetrics);
        this.instantSource = requireNonNull(instantSource);
    }

    /**
     * A signed vote containing the ledger id with a bit set denoting the threshold TSS messages used to compute it.
     */
    public record Vote(
            @NonNull BlsPublicKey ledgerPublicKey, @NonNull Signature signature, @NonNull BitSet thresholdMessages) {
        public @NonNull Bytes ledgerId() {
            return Bytes.wrap(ledgerPublicKey.toBytes());
        }

        public @NonNull Bytes bitSet() {
            return Bytes.wrap(thresholdMessages.toByteArray());
        }
    }

    /**
     * Schedules work to try to compute a signed vote for the new key material of a roster referenced by the
     * given hash, based on incorporating all available {@link TssMessage}s, if
     * the threshold number of messages are available. The signature is with the node's RSA key used for gossip.
     *
     * @param targetRosterHash the hash of the target roster
     * @param directory the TSS participant directory
     * @param context the handle context to use in setting up the computation
     * @return a future resolving to the signed vote if given message passes the threshold, or null otherwise
     */
    public CompletableFuture<Vote> getVoteFuture(
            @NonNull final Bytes targetRosterHash,
            @NonNull final TssParticipantDirectory directory,
            @NonNull final HandleContext context) {
        final var tssStore = context.storeFactory().writableStore(WritableTssStore.class);
        final var tssMessageBodies = tssStore.getTssMessageBodies(targetRosterHash);
        final var voteKey = new TssVoteMapKey(
                targetRosterHash, context.networkInfo().selfNodeInfo().nodeId());
        if (tssStore.getVote(voteKey) == null) {
            return computeVote(tssMessageBodies, directory).exceptionally(e -> {
                log.error("Error computing public keys and signing", e);
                return null;
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Schedules work to compute and sign the ledger id if given {@link TssMessageTransactionBody} messages contain
     * a threshold number of valid {@link TssMessage}s.
     *
     * @param tssMessageBodies the list of TSS message bodies
     * @param tssParticipantDirectory the TSS participant directory
     * @return a future that resolves to the ledger id and signature if the threshold is met
     */
    private CompletableFuture<Vote> computeVote(
            @NonNull final List<TssMessageTransactionBody> tssMessageBodies,
            @NonNull final TssParticipantDirectory tssParticipantDirectory) {
        return CompletableFuture.supplyAsync(
                () -> {
                    final var tssMessages = validateTssMessages(tssMessageBodies, tssParticipantDirectory, tssLibrary);
                    if (!isThresholdMet(tssMessages, tssParticipantDirectory)) {
                        return null;
                    }
                    final var aggregationStart = instantSource.instant();
                    final var validTssMessages = getTssMessages(tssMessages, tssParticipantDirectory, tssLibrary);
                    final var publicShares = tssLibrary.computePublicShares(tssParticipantDirectory, validTssMessages);
                    final var ledgerId = tssLibrary.aggregatePublicShares(publicShares);
                    final var signature = gossip.sign(ledgerId.toBytes());
                    final var thresholdMessages = asBitSet(tssMessages);
                    final var aggregationEnd = instantSource.instant();
                    tssMetrics.updateAggregationTime(
                            Duration.between(aggregationStart, aggregationEnd).toMillis());
                    return new Vote(ledgerId, signature, thresholdMessages);
                },
                libraryExecutor);
    }

    /**
     * Compute the TSS vote bit set. No need to validate the TSS messages here as they have already been validated.
     *
     * @param thresholdMessages the valid TSS messages
     * @return the TSS vote bit set
     */
    private BitSet asBitSet(@NonNull final List<TssMessageTransactionBody> thresholdMessages) {
        final var tssVoteBitSet = new BitSet();
        for (TssMessageTransactionBody op : thresholdMessages) {
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
}
