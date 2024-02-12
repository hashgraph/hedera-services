package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.v7.BlockSignature;
import com.hedera.hapi.streams.v7.BlockStateProof;
import com.hedera.hapi.streams.v7.SiblingHashes;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.state.HederaState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Produces a proof of the state of the ledger at a given block.
 *
 * <p>This BlockStateProofProducer produces a proof for each round.
 */
public class BlockStateProofProducer {
    /** The logger */
    private static final Logger logger = LogManager.getLogger(BlockStateProofProducer.class);

    private ExecutorService executor;

    /* The state of the ledger */
    private final HederaState state;
    /* The round number provided by the Round */
    private final long roundNum;

    private final LinkedList<StateSignatureTransaction> signatures = new LinkedList<>();
    private final AtomicReference<BlockStateProof> proof = new AtomicReference<>();

    public BlockStateProofProducer(
            @NonNull final ExecutorService executor, @NonNull final HederaState state, final long roundNum) {
        this.executor = requireNonNull(executor);
        this.state = requireNonNull(state);
        this.roundNum = roundNum;
        // TODO: We need some more information here to construct the proof. When we construct this object, we need to
        //  know what the consensus roster is so that we can validate the proof before we return it.
    }

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
    public CompletableFuture<BlockStateProof> getBlockStateProof() {
        return CompletableFuture.supplyAsync(this::buildProof, executor);
    }

    private Supplier<BlockStateProof> proofSupplier() {
        // Fast path if the proof has already been constructed.
        final var p = proof.get();
        if (p != null) {
            return () -> p;
        }

        // Return our function that supplies the proof.
        return this::collectProof;
    }

    /**
     * Collect the signatures for the current round and construct a proof.
     * @return the block state proof for the current round
     */
    private BlockStateProof collectProof() {
        final var q = StateSignatureTransactionCollector.getInstance().getQueueForRound(roundNum);

        // Read from the queue to collect the signatures (or a proof if one is provided by the queue).
        while (!proofComplete()) {
            try {
                // Get the next signature from the queue.
                final var e = requireNonNull(q.take(), "Signature should not be null");

                // If we are passed a proof, there is no reason to continue constructing one.
                final var p = tryConsumeProof(e);
                if (p != null) return p;

                // Add the signature to the list of signatures.
                signatures.add(e.sig());

                // See if we have a proof given the signature we just added.
                final var p2 = constructProof();
                if (p2 != null) return setProof(p2);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupt status.
                logger.error("Interrupted while collecting proofs", e);
                // We are interrupted, throw an exception.
                throw new RuntimeException("Thread interrupted during proof collection", e);
            }
        }

        // Return the completed proof.
        return proof.get();
    }

    /**
     * Once we have everything needed to produce a proof, build the proof and return it.
     * @return the block state proof for the current round
     */
    private BlockStateProof constructProof() {
        // Given the signatures we have, try to construct a proof.
        // TODO(nickpoorman): Implement this.
        return null;
    }

    /**
     * Once we have everything needed to produce a proof, build the proof and return it.
     * @return the block state proof for the current round
     */
    private BlockStateProof buildProof() {
        // Construct everything we need for the block proof.

        // Pass the RunningHashes to the BlockStreamProducer so it can create the block proof.
        final var states = state.getReadableStates(BlockRecordService.NAME);
        final var runningHashState = states.<RunningHashes>getSingleton(BlockRecordService.RUNNING_HASHES_STATE_KEY);

        // TODO(nickpoorman): Fill in with the real hashes.
        SecureRandom random = new SecureRandom();
        List<Bytes> treeHashes = new ArrayList<>();
        for (int i = 0; i < 20; i++) { // generate a good amount of sibling hashes
            byte[] hash = new byte[48];
            random.nextBytes(hash);
            treeHashes.add(Bytes.wrap(hash));
        }
        SiblingHashes siblingHashes = new SiblingHashes(treeHashes);

        // TODO(nickpoorman): Fill in with the real signatures.
        List<BlockSignature> blockSignatures = new ArrayList<>();
        for (int i = 0; i < 21; i++) { // 2/3 +1 nodes
            byte[] signature = new byte[48];
            random.nextBytes(signature);
            blockSignatures.add(new BlockSignature(Bytes.wrap(signature), i));
        }

        return BlockStateProof.newBuilder()
                .siblingHashes(siblingHashes)
                .endRunningHashes(runningHashState.get())
                .blockSignatures(blockSignatures)
                .build();
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
    private boolean verifyProof(BlockStateProof proof) {
        // TODO(nickpoorman): Implement this.
        return false;
    }

    /**
     * Set the proof if it's not already set.
     * @param p the proof to set
     * @return the proof that was set or the one that was already set
     */
    private BlockStateProof setProof(BlockStateProof p) {
        // Only set the proof if it's currently not set.
        if (proof.compareAndSet(null, p)) {
            return p;
        }
        // Get the current value of the proof.
        return proof.get();
    }

    /**
     * If we were supplied a proof, we can verify it.
     * @param e the queued state signature transaction
     * @return the proof if it was supplied, otherwise null
     */
    private BlockStateProof tryConsumeProof(@NonNull final QueuedStateSignatureTransaction e) {
        // If a proof was not provided we can't consume it.
        final var p = e.proof();
        if (p == null) return null;

        // If the proof is not null, we can verify and it and set it.
        if (!verifyProof(p)) logger.warn("Received a block proof that was not correct: {}", e.proof());

        // Once we have a proof, set it.
        return setProof(p);
    }
}
