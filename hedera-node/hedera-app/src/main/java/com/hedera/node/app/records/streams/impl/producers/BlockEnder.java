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

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.v7.BlockStateProof;
import com.hedera.hapi.streams.v7.SiblingHashes;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.swirlds.state.HederaState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockEnder {
    /** The logger */
    private static final Logger logger = LogManager.getLogger(BlockEnder.class);
    /** The {@link BlockStreamFormat} used to serialize items for output. */
    private final BlockStreamFormat format;

    private final HashObject lastRunningHash;
    private final BlockStreamWriter writer;
    private final HederaState state;
    private final RunningHashes runningHashes;
    private final SiblingHashes siblingHashes;
    private final long blockNumber;

    /**
     * Gather everything we need to produce a proof. This must be called after the round has completed at the end of
     * the block, and we have processed and written all the transactions and BlockItems for the round. Meaning the
     * state should be exactly what it should be at the end of producing a block, right before constructing the block
     * proof and before processing of the next block starts.
     */
    private BlockEnder(
            @NonNull final HashObject lastRunningHash,
            @NonNull final BlockStreamWriter writer,
            @NonNull final HederaState state,
            @NonNull final BlockStreamFormat format,
            final long blockNumber) {
        this.lastRunningHash = requireNonNull(lastRunningHash, "Last running hash must be present");
        this.writer = requireNonNull(writer, "Block stream writer must be present");
        this.state = requireNonNull(state, "State must be present");
        this.format = requireNonNull(format, "Block stream format must be present");
        this.blockNumber = blockNumber;

        // We will need the running hashes at the time the block has been completed.
        final var states = state.getReadableStates(BlockRecordService.NAME);
        final var runningHashState = states.<RunningHashes>getSingleton(V0490BlockRecordSchema.RUNNING_HASHES_STATE_KEY);
        // Set the running hashes at the time the block was completed.
        this.runningHashes = requireNonNull(runningHashState.get(), "Running hashes must be present");

        // We also need the sibling hashes at the time the block was completed.
        // TODO(nickpoorman): Get the actual sibling hashes.
        //         this.siblingHashes = requireNonNull(state.getSiblingHashes(), "Sibling hashes must be present");
        // For now, we will just generate some random hashes.
        List<Bytes> hashes = new ArrayList<>(10);
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[48];
            random.nextBytes(bytes);
            hashes.add(Bytes.wrap(bytes));
        }
        this.siblingHashes = new SiblingHashes(hashes);
    }

    public void endBlock(
            @NonNull final CompletableFuture<BlockStateProof> blockPersisted,
            @NonNull final BlockStateProofProducer stateProofProducer) {
        try {
            // Supply the running and sibling hashes to the stateProofProducer.
            stateProofProducer.setRunningHashes(runningHashes);
            stateProofProducer.setSiblingHashes(siblingHashes);

            // Block until the blockStateProof is available. This call makes the operation synchronous and blocks
            // until it is able to get the state proof.
            BlockStateProof proof = stateProofProducer.getBlockStateProof().get();

            writeStateProof(proof);
            closeWriter(lastRunningHash, blockNumber);

            // If operations complete successfully, complete blockPersisted with the proof.
            blockPersisted.complete(proof);
        } catch (InterruptedException e) {
            logger.error("Interrupted while getting block state proof for block {}", blockNumber, e);
            // Re-interrupt the current thread when InterruptedException is caught.
            Thread.currentThread().interrupt();

            // Exceptionally complete blockPersisted with the caught exception.
            blockPersisted.completeExceptionally(e);
        } catch (ExecutionException e) {
            logger.error("Error getting block state proof for block {}", blockNumber, e);
            // Exceptionally complete blockPersisted with the cause of the ExecutionException.
            // ExecutionException wraps the actual exception that caused the problem, so unwrap it.
            blockPersisted.completeExceptionally(e.getCause());
        }
    }

    private void writeStateProof(@NonNull final BlockStateProof blockStateProof) {
        // We do not update running hashes with the block state proof hash like we do for other block items, we simply
        // write it out.
        final var serializedBlockItem = format.serializeBlockStateProof(blockStateProof);
        writeSerializedBlockItem(serializedBlockItem);
    }

    private void writeSerializedBlockItem(@NonNull final Bytes serializedItem) {
        try {
            // Depending on the configuration, this writeItem may be an asynchronous or synchronous operation. The
            // BlockStreamWriterFactory instantiated by dagger will determine this.
            writer.writeItem(serializedItem, lastRunningHash.hash());
        } catch (final Exception e) {
            // This **may** prove fatal. The node should be able to carry on, but then fail when it comes to
            // actually producing a valid record stream file. We need to have some way of letting all nodes know
            // that this node has a problem, so we can make sure at least a minimal threshold of nodes is
            // successfully producing a blockchain.
            logger.error("Error writing block item to block stream writer for block {}", blockNumber, e);
            // Best way to do this is to throw the exception and then propagate it to the blockPersisted future.
            throw new RuntimeException(e);
        }
    }

    private void closeWriter(@NonNull final HashObject lastRunningHash, final long lastBlockNumber) {
        if (writer != null) {
            logger.debug(
                    "Closing block record writer for block {} with running hash {}", lastBlockNumber, lastRunningHash);

            // If we fail to close the writer, then this node is almost certainly going to end up in deep trouble.
            // Make sure this is logged. In the FUTURE we may need to do something more drastic, like shut down the
            // node, or maybe retry a number of times before giving up.
            try {
                writer.close();
            } catch (final Exception e) {
                logger.error("Error closing block record writer for block {}", lastBlockNumber, e);
            }
        }
    }

    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * This is a mutable object that can be used to construct a {@link BlockEnder}. It is passed to different threads to
     * construct a {@link BlockEnder} object, therefore we need the variables to be volatile to ensure that the changes
     * made by one thread are visible to the other threads.
     */
    public static class Builder {
        private volatile HashObject lastRunningHash;
        private volatile BlockStreamWriter writer;
        private volatile HederaState state;
        private volatile BlockStreamFormat format;
        private volatile long blockNumber;

        @NonNull
        public Builder setLastRunningHash(@NonNull final HashObject lastRunningHash) {
            this.lastRunningHash = requireNonNull(lastRunningHash, "Last running hash must be present");
            return this;
        }

        @NonNull
        public Builder setWriter(@NonNull final BlockStreamWriter writer) {
            this.writer = requireNonNull(writer, "Block stream writer must be present");
            return this;
        }

        @NonNull
        public Builder setState(@NonNull final HederaState state) {
            this.state = requireNonNull(state, "State must be present");
            return this;
        }

        @NonNull
        public Builder setFormat(@NonNull final BlockStreamFormat format) {
            this.format = requireNonNull(format, "Block stream format must be present");
            return this;
        }

        @NonNull
        public Builder setBlockNumber(final long blockNumber) {
            this.blockNumber = blockNumber;
            return this;
        }

        @NonNull
        public BlockEnder build() {
            return new BlockEnder(lastRunningHash, writer, state, format, blockNumber);
        }
    }
}
