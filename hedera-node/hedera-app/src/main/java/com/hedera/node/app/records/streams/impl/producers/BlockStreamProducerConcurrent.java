package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.streams.v7.BlockStateProof;
import com.hedera.hapi.streams.v7.StateChanges;
import com.hedera.node.app.records.streams.ProcessUserTransactionResult;
import com.hedera.node.app.records.streams.impl.BlockStreamProducer;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class BlockStreamProducerConcurrent implements BlockStreamProducer {

    /** The logger */
    private static final Logger logger = LogManager.getLogger(BlockStreamProducerConcurrent.class);

    private final BlockStreamProducer producer;
    private final ExecutorService executor;
    private final AtomicReference<CompletableFuture<Void>> lastFutureRef;

    /**
     * BlockStreamProducerConcurrent ensures that all the methods called on the producer are executed sequentially in
     * the order in which they are called by the handle thread. This is done by chaining the lastFutureRef to the next
     * write method.
     *
     * <p>By default, the BlockStreamWriterFactory will produce the concurrent block stream writer, so writes will not
     *    block the future chain for this producer. If we need to propagate the status of anything up, we should pass a
     *    CompletableFuture down to be completed, for example, if need to ensure that a BlockStateProof is produced and
     *    the entire block has been flushed and persisted to disk.
     *
     * @param executor the executor service to use for writes
     * @param producer the producer to wrap
     */
    public BlockStreamProducerConcurrent(@NonNull final BlockStreamProducer producer, ExecutorService executor) {
        this.producer = producer;
        this.executor = executor;
        this.lastFutureRef = new AtomicReference<>(CompletableFuture.completedFuture(null));
    }

    @Override
    public void initFromLastBlock(@NonNull RunningHashes runningHashes, long lastBlockNumber) {
        doAsync(CompletableFuture.runAsync(() -> producer.initFromLastBlock(runningHashes, lastBlockNumber), executor));
    }

    @NonNull
    @Override
    public Bytes getRunningHash() {
        blockUntilRunningHashesUpdated();
        return producer.getRunningHash();
    }

    @Nullable
    @Override
    public Bytes getNMinus3RunningHash() {
        blockUntilRunningHashesUpdated();
        return producer.getNMinus3RunningHash();
    }

    @Override
    public void beginBlock() {
        doAsync(CompletableFuture.runAsync(producer::beginBlock, executor));
    }

    @Override
    public void endBlock(
            @NonNull BlockStateProofProducer blockStateProofProducer,
            @NonNull CompletableFuture<BlockStateProof> blockPersisted) {
        doAsync(CompletableFuture.runAsync(() -> producer.endBlock(blockStateProofProducer, blockPersisted), executor));
    }

    @Override
    public void writeConsensusEvent(@NonNull ConsensusEvent consensusEvent) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeConsensusEvent(consensusEvent), executor));
    }

    @Override
    public void writeSystemTransaction(@NonNull ConsensusTransaction systemTxn) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeSystemTransaction(systemTxn), executor));
    }

    @Override
    public void writeUserTransactionItems(@NonNull ProcessUserTransactionResult items) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeUserTransactionItems(items), executor));
    }

    @Override
    public void writeStateChanges(@NonNull StateChanges stateChanges) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeStateChanges(stateChanges), executor));
    }

    @Override
    public void close() throws Exception {
        blockUntilRunningHashesUpdated();
        producer.close();
    }

    private CompletableFuture<Void> doAsync(CompletableFuture<Void> updater) {
        return lastFutureRef.updateAndGet(lastFuture -> lastFuture.thenCompose(v -> updater));
    }

    private void awaitFutureCompletion(Future<?> future) {
        try {
            future.get(); // Block until the task completes
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for task to complete", e);
            // TODO: Should we throw here?
        } catch (ExecutionException e) {
            logger.error("Error occurred during task execution", e.getCause());
            throw new RuntimeException(e);
        }
    }

    private void blockUntilRunningHashesUpdated() {
        Future<?> currentUpdateTask = lastFutureRef.get();
        if (currentUpdateTask == null) return;
        // Wait for the update task to complete and handle potential interruptions.
        awaitFutureCompletion(currentUpdateTask);
    }
}
