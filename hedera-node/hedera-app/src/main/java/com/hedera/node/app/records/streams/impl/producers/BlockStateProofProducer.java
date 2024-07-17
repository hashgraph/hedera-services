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

package com.hedera.node.app.records.streams.impl.producers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.BlockSignature;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.HederaState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Produces a proof of the state of the ledger at a given block.
 *
 * <p>This BlockStateProofProducer produces a proof for each round.
 */
public class BlockStateProofProducer {
    /** The logger */
    private static final Logger logger = LogManager.getLogger(BlockStateProofProducer.class);

    private final ExecutorService executor;

    /* The state of the ledger */
    private final HederaState state;
    /* The round number provided by the Round */
    private final long roundNum;

    private final CopyOnWriteArrayList<QueuedStateSignatureTransaction> signatures = new CopyOnWriteArrayList<>();
    private final AtomicReference<BlockProof> proof = new AtomicReference<>();
    private final AddressBook consensusRoster;
    private final int requiredNumberOfSignatures;
    private volatile RunningHashes runningHashes;
    //private volatile SiblingHashes siblingHashes;

    public BlockStateProofProducer(
            @NonNull final ExecutorService executor,
            @NonNull final HederaState state,
            final long roundNum,
            final AddressBook consensusRoster) {
        this.executor = requireNonNull(executor);
        this.state = requireNonNull(state);
        this.roundNum = roundNum;
        // TODO: We need some more information here to construct the proof. When we construct this object, we need to
        //  know what the consensus roster is so that we can validate the proof before we return it.
        // For now, we will simply require a number of signatures to be provided.
        this.consensusRoster = consensusRoster;
        // We need 1/3 + 1 signatures to produce a proof.
        this.requiredNumberOfSignatures = (int) Math.floor(consensusRoster.getSize() * 1.0 / 3.0) + 1;
    }

    public void setRunningHashes(@NonNull final RunningHashes runningHashes) {
        this.runningHashes = requireNonNull(runningHashes, "Running hashes cannot be null");
    }

    /*public void setSiblingHashes(@NonNull final SiblingHashes siblingHashes) {
        this.siblingHashes = requireNonNull(siblingHashes, "Sibling hashes cannot be null");
    }*/

    /**
     * Get the block state proof for the current round. This will return a future that will complete with the block
     * proof for the current round as soon as we have enough signatures. Signature gathering happens asynchronously
     * and is not guaranteed to complete immediately, therefore we do not want to block the handle thread.
     *
     * <p>This should be run on the executor service that was provided and any forked futures should also be run on
     * the executor provided.
     *
     * <p>The resulting future must be executed on a thread where it is acceptable to block until we have received the
     * signatures we need to construct the proof.
     *
     * @return a future that will complete with the block state proof for the current round
     */
    @NonNull
    public CompletableFuture<BlockProof> getBlockProof() {
        // Using the Supplier, return a future that will complete with the block proof for the current round as soon as
        // we have enough signatures.
        return CompletableFuture.supplyAsync(proofSupplier(), executor);
    }

    /**
     * Get the supplier for the block state proof for the current round.
     * @return the supplier for the block state proof for the current round
     */
    @NonNull
    private Supplier<BlockProof> proofSupplier() {
        final var c = StateSignatureTransactionCollector.getInstance();

        // Fast path if the proof has already been constructed.
        final var p = proof.get();
        if (p != null) {
            // Mark the proof collected on the collector.
            c.roundComplete(p, roundNum);
            return () -> p;
        }

        final var p2 = this.collectProof(c);
        c.roundComplete(p2, roundNum);
        // Return our function that supplies the proof.
        return () -> p2;
    }

    /**
     * Collect the signatures for the current round and construct a proof.
     * @return the block state proof for the current round
     */
    @NonNull
    private BlockProof collectProof(@NonNull final StateSignatureTransactionCollector c) {
        final var q = c.getQueueForRound(roundNum);

        // Read from the queue to collect the signatures (or a proof if one is provided by the queue).
        while (!proofComplete()) {
            // Get the next signature from the queue.
            QueuedStateSignatureTransaction e;
            try {
                final var queueItem = q.take();
                e = requireNonNull(queueItem, "Signature should not be null");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // Restore the interrupt status.
                logger.error("Interrupted while taking from queue: " + ex);
                throw new RuntimeException("Thread interrupted during take operation", ex);
            } catch (Exception ex) {
                logger.error("Error during take operation", ex);
                throw new RuntimeException("Error during take operation", ex);
            }

            // If we are passed a proof, there is no reason to continue constructing one.
            final var p = tryConsumeProof(e);
            if (p != null) {
                return p;
            }

            // Verify and add the signature to the list of signatures.
            final var sig = tryConsumeSignature(e);
            if (sig == null) {
                continue; // No signature was added, skip the following steps.
            }

            // See if we have a proof given the signature we just added.
            final var proof = constructProof();
            if (proof != null) return setProof(proof);
        }

        // Return the completed proof.
        return proof.get();
    }

    /**
     * Once we have everything needed to produce a proof, build the proof and return it.
     * @return the block state proof for the current round or null if we don't have enough signatures
     */
    @Nullable
    private BlockProof constructProof() {
        // Given the signatures we have, try to construct a proof.
        // Do we have enough signatures?
        if (!haveEnoughSignatures()) return null;
        // If we have enough signatures, build the proof.
        return buildProof();
    }

    /**
     * Determine if we have enough signatures to produce a proof for this round given the consensus roster for this
     * round.
     * @return true if we have enough signatures to produce a proof, otherwise false
     */
    private boolean haveEnoughSignatures() {
        // We the correct number of signatures to produce a proof.
        return signatures.size() >= requiredNumberOfSignatures;
    }

    @NonNull
    private Stream<BlockSignature> buildBlockSignatures() {
        return signatures.stream()
                .map(sst -> new BlockSignature(
                        sst.sig()
                                .getStateSignaturePayload()
                                .signature()));
    }

    /**
     * Once we have everything needed to produce a proof, build the proof and return it.
     * @return the block state proof for the current round
     */
    @NonNull
    private BlockProof buildProof() {
        final var blockSignatures = buildBlockSignatures().toList();
        assert runningHashes != null : "Running hashes should not be null";
        assert blockSignatures != null : "Block signatures should not be null";
        assert !blockSignatures.isEmpty() : "Block signatures should not be empty";

        final var proof = BlockProof.newBuilder()
                //.blockSignature(blockSignatures) block
                .build();

        // Construct the block proof with the information we gathered.
        return proof;
    }

    /**
     * Check if the proof is complete.
     * @return true if the proof is complete, otherwise false
     */
    private boolean proofComplete() {
        final var p = proof.get();
        return p != null;
    }

    /**
     * Verify the proof.
     * @param proof the proof to verify
     * @return true if the proof is valid, otherwise false
     */
    private boolean verifyProof(@NonNull final BlockProof proof) {
        // TODO(nickpoorman): Implement this.
        return true;
    }

    /**
     * Set the proof if it's not already set.
     * @param p the proof to set
     * @return the proof that was set or the one that was already set
     */
    @NonNull
    private BlockProof setProof(@NonNull final BlockProof p) {
        // Only set the proof if it's currently not set.
        if (proof.compareAndSet(null, p)) return p;
        // Get the current value of the proof.
        return proof.get();
    }

    /**
     * If we were supplied a proof, we can verify it.
     * @param e the queued state signature transaction
     * @return the proof if it was supplied, otherwise null
     */
    @Nullable
    private BlockProof tryConsumeProof(@NonNull final QueuedStateSignatureTransaction e) {
        // If a proof was not provided we can't consume it.
        final var p = e.proof();
        if (p == null) return null;

        // If the proof is not null, we can verify and it and set it.
        if (!verifyProof(p)) logger.warn("Received a block proof that was not valid: {}", p);

        // Once we have a proof, set it.
        return setProof(p);
    }

    /**
     * If we were supplied a signature, we can verify it and add it to our list of signatures.
     * @param t the queued state signature transaction
     * @return the queued state signature transaction if it was valid, otherwise null
     */
    @Nullable
    private QueuedStateSignatureTransaction tryConsumeSignature(@NonNull final QueuedStateSignatureTransaction t) {
        // If a signature was not provided or the nodeId is not set we can't consume it.
        if (t.sig() == null || t.nodeId() == -1) return null;

        // If the signature is not null, we can verify and it and add it to our collected signatures.
        if (!verifySignature(t.nodeId(), t.sig())) logger.warn("Received a block signature that was not valid: {}", t);

        // Once have verified the signature, set it.
        signatures.add(t);

        return t;
    }

    private boolean verifySignature(final long nodeId, @NonNull final StateSignatureTransaction sig) {
        // TODO(nickpoorman): Implement this.
        return true;
    }
}
