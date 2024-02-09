/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.HashAlgorithm;
import com.hedera.hapi.streams.HashObject;
import com.hedera.hapi.streams.v7.BlockStateProof;
import com.hedera.hapi.streams.v7.StateChanges;
import com.hedera.node.app.records.streams.ProcessUserTransactionResult;
import com.hedera.node.app.records.streams.impl.BlockStreamProducer;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A concurrent implementation of {@link BlockStreamProducer} where all operations happen in a different thread from the
 * "handle" thread. This implementation should be more performant and reduce the impact to TPS by not blocking the
 * handle thread from which it is called.
 *
 * <p>A BlockStreamProducer itself only responsible for delegating serialization of data it's passed from write methods
 * and computing running hashes from the serialized data.
 */
public final class BlockStreamProducerConcurrent implements BlockStreamProducer {
    /**
     * The executor service for running the block stream producer. While we are currently using a single thread
     * executor we should ensure state access is properly handled.
     */
    private final ExecutorService serialExecutorService = Executors.newSingleThreadExecutor();
    /**
     * Maybe we should use the platform executor here? What we need is a pool of executors to write the files out.
     * We're talking mostly IO and gzip compression. The work stealing pool is really a ForkJoin pool that has async
     * enabled to process as a LIFO execution order which means we'll have lower latency for the last item submitted to
     * the service. We chain our write futures, so we can ensure the order of the writes.
     */
    private final ExecutorService parallelExecutorService = Executors.newWorkStealingPool();
    /**
     * We must keep track of the last task that was submitted to the running hashes. This is so handle thread can do
     * something like:
     * 1. Call `writeUserTransactionItems` which will submit a task to the executor service.
     * 2. Call `getRunningHash` or `getNMinus3RunningHash` which will block until the task from step 1 is complete.
     *
     * We assume that this is only updated by the handle thread. This is a volatile field, so we can ensure that
     * the handle thread and any other thread can see the most recent value when calling `getRunningHash` or
     * `getNMinus3RunningHash`.
     */
    private volatile Future<?> lastUpdateTaskFuture = null;
    /** The logger */
    private static final Logger logger = LogManager.getLogger(BlockStreamProducerConcurrent.class);
    /** Creates new {@link BlockStreamWriter} instances */
    private final BlockStreamWriterFactory writerFactory;
    /** The HAPI protobuf version. Does not change during execution. */
    private final SemanticVersion hapiVersion;
    /** The {@link BlockStreamFormat} used to serialize items for output. */
    private final BlockStreamFormat format;
    /**
     * The {@link BlockStreamWriter} to use for writing produced blocks. A new one is created at the beginning
     * of each new block.
     */
    private volatile ConcurrentBlockStreamWriter writer;
    /** The running hash at end of the last user transaction */
    private final AtomicReference<Bytes> runningHash = new AtomicReference<>(null);
    /** The previous running hash */
    private final AtomicReference<Bytes> runningHashNMinus1 = new AtomicReference<>(null);
    /** The previous, previous running hash */
    private final AtomicReference<Bytes> runningHashNMinus2 = new AtomicReference<>(null);
    /** The previous, previous, previous running hash */
    private final AtomicReference<Bytes> runningHashNMinus3 = new AtomicReference<>(null);

    private final AtomicLong currentBlockNumber = new AtomicLong(-1);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * Construct BlockStreamProducerConcurrent
     *
     * @param nodeInfo the current node information
     * @param format The format to use for the block stream
     * @param writerFactory constructs the writers for the block stream, one per block
     */
    @Inject
    public BlockStreamProducerConcurrent(
            @NonNull final SelfNodeInfo nodeInfo,
            @NonNull final BlockStreamFormat format,
            @NonNull final BlockStreamWriterFactory writerFactory) {
        this.writerFactory = requireNonNull(writerFactory);
        this.format = requireNonNull(format);
        hapiVersion = nodeInfo.hapiVersion();
    }

    // =========================================================================================================================================================================
    // public methods

    /** {@inheritDoc} */
    @Override
    public void initFromLastBlock(@NonNull final RunningHashes runningHashes, long lastBlockNumber) {
        if (this.runningHash.get() != null) {
            throw new IllegalStateException("initFromLastBlock() must only be called once");
        }

        this.runningHash.set(runningHashes.runningHash());
        this.runningHashNMinus1.set(runningHashes.nMinus1RunningHash());
        this.runningHashNMinus2.set(runningHashes.nMinus2RunningHash());
        this.runningHashNMinus3.set(runningHashes.nMinus3RunningHash());
        this.currentBlockNumber.set(lastBlockNumber);
    }

    /** {@inheritDoc} */
    public void beginBlock() {
        lastUpdateTaskFuture = submitSerialTask(() -> {
            final var blockNumber = this.currentBlockNumber.incrementAndGet();
            final var lastRunningHash = getRunningHashObject();

            logger.debug(
                    "Initializing block stream writer for block {} with running hash {}",
                    currentBlockNumber,
                    lastRunningHash);

            openWriter(blockNumber, lastRunningHash);
        });
    }

    /** {@inheritDoc} */
    public void endBlock(
            @NonNull final BlockStateProofProducer blockStateProofProducer,
            @NonNull final CompletableFuture<BlockStateProof> blockPersisted) {

        // Submit the task to our single threaded executor service. This ensures this task isn't executed
        // until all the tasks for building this block happen before it.
        lastUpdateTaskFuture = submitSerialTask(() -> {

            // Once we have completed the proceeding tasks, and this task is being executed (meaning we are at the end
            // of the block), we need to construct the block proof. However, we do not want to hold up any other blocks
            // from being produced on our serial executor. Therefore, we need to submit the task to the
            // parallelExecutorService.

            // We must get the running hash object at this point before we submit the task to the parallel executor
            // service. If we were to submit it after, we could potentially process the next block item before
            // retrieving the running hash object, which would be incorrect.
            final var lastRunningHash = getRunningHashObject();
            final var currentBlockNumber = this.currentBlockNumber.get();

            parallelExecutorService.submit(() -> {
                blockStateProofProducer
                        .getBlockStateProof(parallelExecutorService)
                        .thenComposeAsync(
                                proof -> writeStateProof(proof) // Write the state proof and then close the writer.
                                        .thenComposeAsync(
                                                v -> closeWriter(lastRunningHash, currentBlockNumber),
                                                parallelExecutorService)
                                        .thenApplyAsync(
                                                v -> proof,
                                                parallelExecutorService), // Return the proof as the last stage.
                                parallelExecutorService)
                        .thenAcceptAsync(blockPersisted::complete, parallelExecutorService)
                        .exceptionallyAsync(
                                ex -> {
                                    blockPersisted.completeExceptionally(ex);
                                    return null;
                                },
                                parallelExecutorService);
            });
        });
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        // Submit the cleanup task
        Future<?> cleanupFuture = submitSerialTask(() -> {
            if (!isClosed.compareAndSet(false, true)) {
                throw new RuntimeException("BlockStreamProducerConcurrent is already closed.");
            }
            // We don't need a lock while we assume executorService is a single thread executor. If that changes,
            // we will need to add a lock here since we are modifying multiple variables.
            final var lastRunningHash = getRunningHashObject();
            closeWriter(lastRunningHash, this.currentBlockNumber.get())
                    .join(); // Wait for the completion of the write operation.

            // This may be unnecessary if we are already checking isClosed.
            runningHash.set(null);
            runningHashNMinus1.set(null);
            runningHashNMinus2.set(null);
            runningHashNMinus3.set(null);
        });

        // Wait for the cleanup task to complete and handle potential interruptions
        awaitFutureCompletion(cleanupFuture);

        // Shutdown and await termination of the executor service
        shutdownExecutorService(serialExecutorService);
    }

    private void awaitFutureCompletion(Future<?> future) {
        try {
            future.get(); // Block until the task completes
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for task to complete", e);
        } catch (ExecutionException e) {
            logger.error("Error occurred during task execution", e.getCause());
        }
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.error("Executor service did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes getRunningHash() {
        blockUntilRunningHashesUpdated();
        return getRunningHashNonBlocking();
    }

    @NonNull
    public Bytes getRunningHashNonBlocking() {
        return runningHash.get();
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Bytes getNMinus3RunningHash() {
        blockUntilRunningHashesUpdated();
        return runningHashNMinus3.get();
    }

    private void blockUntilRunningHashesUpdated() {
        Future<?> currentUpdateTask = lastUpdateTaskFuture;
        if (currentUpdateTask == null) return;
        // Wait for the update task to complete and handle potential interruptions.
        awaitFutureCompletion(currentUpdateTask);
    }

    /** {@inheritDoc} */
    public void writeConsensusEvent(@NonNull final ConsensusEvent consensusEvent) {
        lastUpdateTaskFuture = submitSerialTask(() -> {
            final var serializedBlockItem = format.serializeConsensusEvent(consensusEvent);
            updateRunningHashes(serializedBlockItem);
            writeSerializedBlockItem(serializedBlockItem).join(); // Wait for the completion of the write operation.
        });
    }

    /** {@inheritDoc} */
    public void writeSystemTransaction(@NonNull final ConsensusTransaction systemTxn) {
        lastUpdateTaskFuture = submitSerialTask(() -> {
            final var serializedBlockItem = format.serializeSystemTransaction(systemTxn);
            updateRunningHashes(serializedBlockItem);
            writeSerializedBlockItem(serializedBlockItem).join(); // Wait for the completion of the write operation.
        });
    }

    /** {@inheritDoc} */
    public void writeUserTransactionItems(@NonNull final ProcessUserTransactionResult result) {
        lastUpdateTaskFuture = submitSerialTask(() -> {
            // We reuse this messageDigest to avoid creating a new one for each item.
            final MessageDigest messageDigest = format.getMessageDigest();

            // Not going to parallelize this until we have some benchmarks that prove we need to. By doing these
            // sequentially, we can easily maintain the order, and don't have to create multiple messageDigests. The
            // overhead of doing this in parallel would likely be higher than processing the small number of items we
            // expect to get sequentially.

            result.transactionRecordStream().forEach(item -> {
                final var serializedBlockItems = format.serializeUserTransaction(item);
                serializedBlockItems.forEach(serializedBlockItem -> {
                    updateRunningHashesWithMessageDigest(messageDigest, serializedBlockItem);
                    writeSerializedBlockItem(serializedBlockItem)
                            .join(); // Wait for the completion of the write operation.
                });
            });
        });
    }

    /** {@inheritDoc} */
    public void writeStateChanges(@NonNull final StateChanges stateChanges) {
        lastUpdateTaskFuture = submitSerialTask(() -> {
            final var serializedBlockItem = format.serializeStateChanges(stateChanges);
            updateRunningHashes(serializedBlockItem);
            writeSerializedBlockItem(serializedBlockItem).join(); // Wait for the completion of the write operation.
        });
    }

    /** {@inheritDoc} */
    public CompletableFuture<Void> writeStateProof(@NonNull final BlockStateProof blockStateProof) {
        // We do not update running hashes with the block state proof hash like we do for other block items, we
        // simply write it out.
        final var serializedBlockItem = format.serializeBlockStateProof(blockStateProof);
        return writeSerializedBlockItem(serializedBlockItem);
    }

    /**
     * Throw an exception if this producer has already been closed.
     */
    private void throwIfClosed() {
        if (isClosed.get()) {
            throw new IllegalStateException("BlockStreamProducerConcurrent is closed");
        }
    }

    /**
     * Submit a task to the executor serial service and return a Future for the result. When the task is executed, it
     * will check if the producer has been closed. If it has already been closed, we throw an exception signaling
     * that the producer is closed an that no more data can be produced.
     * @param task the task to submit
     * @return a Future for the result of the task
     */
    private Future<?> submitSerialTask(Runnable task) {
        return serialExecutorService.submit(() -> {
            // TODO: I think we should do this out of band from the executor service queue, as well
            //  as close.
            throwIfClosed();
            task.run();
        });
    }

    private HashObject asHashObject(@NonNull final Bytes hash) {
        return new HashObject(HashAlgorithm.SHA_384, (int) hash.length(), hash);
    }

    private HashObject getRunningHashObject() {
        return asHashObject(getRunningHashNonBlocking());
    }

    private CompletableFuture<Void> closeWriter(@NonNull final HashObject lastRunningHash, final long lastBlockNumber) {
        if (writer == null) {
            logger.debug(
                    "Writer cannot be null. Closing block record writer for block {} with running hash {}",
                    lastBlockNumber,
                    lastRunningHash);
            // Return a CompletableFuture that is immediately completed exceptionally due to the null writer.
            // Maybe we should throw an exception immediately instead of returning a failed future?
            return CompletableFuture.failedFuture(new IllegalStateException("Writer cannot be null"));
        }

        return writer.closeSequentiallyAsync(lastRunningHash)
                .thenAccept(v -> logger.debug("Closed block record writer for block {}", lastBlockNumber))
                .exceptionally(e -> {
                    // If we fail to close the writer, then this node is almost certainly going to end up in deep
                    // trouble. Make sure this is logged. In the FUTURE we may need to do something more drastic, like
                    // shut down the node, or maybe retry a number of times before giving up.
                    logger.error("Error closing block record writer for block {}", lastBlockNumber, e);
                    return null;
                });
    }

    /**
     * openWriter uses the writerFactory to create a new writer and initialize it for each block. The writer is not
     * re-used.
     * @param newBlockNumber
     * @param lastRunningHash
     */
    private void openWriter(final long newBlockNumber, @NonNull final HashObject lastRunningHash) {
        try {
            writer = new ConcurrentBlockStreamWriter(parallelExecutorService, writerFactory.create());
            writer.init(currentBlockNumber.get());
        } catch (final Exception e) {
            // This represents an almost certainly fatal error. In the FUTURE we should look at dealing with this in a
            // more comprehensive and consistent way. Maybe we retry a bunch of times before giving up, then restart
            // the node. Or maybe we block forever. Or maybe we disable event intake while we keep trying to get this
            // to work. Or maybe we just shut down the node.
            logger.error("Error creating or initializing a block record writer for block {}", newBlockNumber, e);
            throw e;
        }
    }

    private void updateRunningHashes(@NonNull final Bytes serializedItem) {
        updateRunningHashesWithMessageDigest(format.getMessageDigest(), serializedItem);
    }

    private void updateRunningHashesWithMessageDigest(
            @NonNull final MessageDigest messageDigest, @NonNull final Bytes serializedItem) {
        // Update the running hashes, shifting each down one position and calculating the new running hash
        // by adding each serialized BlockItem to the current running hash.
        Bytes newRunningHash = format.computeNewRunningHash(messageDigest, runningHash.get(), serializedItem);

        // Shift the running hashes
        shiftAndUpdateRunningHashes(newRunningHash);
    }

    private void shiftAndUpdateRunningHashes(Bytes newRunningHash) {
        // Shift the running hashes down one position
        runningHashNMinus3.set(runningHashNMinus2.get());
        runningHashNMinus2.set(runningHashNMinus1.get());
        runningHashNMinus1.set(runningHash.get());

        // Update the latest running hash with the new value
        runningHash.set(newRunningHash);
    }

    private CompletableFuture<Void> writeSerializedBlockItem(@NonNull final Bytes serializedItem) {
        return writer.writeItemSequentiallyAsync(serializedItem);
    }
}
