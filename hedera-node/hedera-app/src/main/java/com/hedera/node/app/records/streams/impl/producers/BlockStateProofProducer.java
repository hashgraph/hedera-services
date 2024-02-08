package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.v7.BlockSignature;
import com.hedera.hapi.streams.v7.BlockStateProof;
import com.hedera.hapi.streams.v7.SiblingHashes;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.state.HederaState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.Round;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Produces a proof of the state of the ledger at a given block. This is used by the {@link BlockStreamManagerImpl}.
 *
 * <p>This BlockStateProofProducer produces a proof for each round.
 */
public class BlockStateProofProducer {

    /* The state of the ledger */
    private final HederaState state;
    /* The round number provided by the Round */
    private final long roundNum;

    // TODO(nickpoorman): My concern with the following is that we may have state changes and transactions from
    //  handle that we are going to have to buffer up, and then write to the following block if we grab the
    //  StateSignatureTransaction from handle. Seems the only clean way to do it is from preHandle.
    // We need a few things here.
    // 1. We need to be able to get all the StateSignatureTransaction events that have come in.
    // 2. We need a way to execute writing the block proof only once we have enough signatures. This will
    // probably need to be some sort of future unless we have a guarantee that no more handles will be called
    // until the all the StateSignatureTransaction events have been processed, which is certainly not the
    // case with preHandle, so we should assume it's not the case.
    // 3. Once we have enough signatures, we need to execute the completable future that is responsible for
    // producing the block proof and closing this block.

    public BlockStateProofProducer(@NonNull final HederaState state, final long roundNum) {
        this.state = state;
        this.roundNum = roundNum;
    }

    public CompletableFuture<BlockStateProof> getBlockStateProof() {
        // TODO(nickpoorman): Implement this.
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<BlockStateProof> buildProof() {
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

        return CompletableFuture.completedFuture(BlockStateProof.newBuilder()
                .siblingHashes(siblingHashes)
                .endRunningHashes(runningHashState.get())
                .blockSignatures(blockSignatures)
                .build());
    }
}
