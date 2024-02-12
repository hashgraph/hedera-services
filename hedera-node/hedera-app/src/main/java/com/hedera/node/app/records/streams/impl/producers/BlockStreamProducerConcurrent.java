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

    private final ExecutorService executor;
    private final BlockStreamProducer producer;
    private final AtomicReference<CompletableFuture<Void>> lastFutureRef;

    /**
     * BlockStreamProducerConcurrent ensures that all the methods called on the producer are executed sequentially in
     * the order in which they are called by the handle thread. This is done by chaining the lastFutureRef to the next
     * write method.
     *
     * <p>By default, the BlockStreamWriterFactory will produce the concurrent block stream writer, so writes will not
     *    block the future chain for this producer. If we need to propagate the status of anything up, we should pass a
     *    CompletableFuture down to be completed, for example, if we need to ensure that a BlockStateProof is produced
     *    and the entire block has been flushed and persisted to disk.
     *
     * <p>This implementation of BlockStreamProducerConcurrent, the doAsync method chains asynchronous tasks in such a
     *    way that they are executed sequentially by updating lastFutureRef with the new task that should run after the
     *    previous one completes. This chaining uses thenCompose, which creates a new stage that, when this stage
     *    completes normally, is executed with this stage's result as the argument to the supplied function.
     *
     * <p>If any task in the chain completes exceptionally (for example, due to an exception thrown during its
     *    execution), the resulting CompletableFuture from thenCompose will also complete exceptionally with a
     *    CompletionException. This exception wraps the original exception that caused the task to fail. An
     *    exceptionally completed future will not execute subsequent completion stages that depend on the future's
     *    normal completion. If one of the futures in the lastFutureRef chain completes exceptionally, it effectively
     *    halts the execution of subsequent tasks that are dependent on normal completion. This could lead to a scenario
     *    where the chain of operations is stopped prematurely, and tasks that are supposed to execute next are skipped.
     *    This may not be an issue, because if one of these tasks fails, this node is unable to produce the block stream
     *    which is the entire purpose of its existence. If the block stream is not produced, the node is in a bad state
     *    and will likely need to restart.
     *
     * @param executor the executor service to use for writes
     * @param producer the producer to wrap
     */
    public BlockStreamProducerConcurrent(
            @NonNull final ExecutorService executor, @NonNull final BlockStreamProducer producer) {
        this.executor = executor;
        this.producer = producer;
        this.lastFutureRef = new AtomicReference<>(CompletableFuture.completedFuture(null));
    }

    @Override
    public void initFromLastBlock(@NonNull final RunningHashes runningHashes, long lastBlockNumber) {
        doAsync(CompletableFuture.runAsync(() -> producer.initFromLastBlock(runningHashes, lastBlockNumber), executor));
    }

    /**
     * Get the current running hash of block items. This is called on the handle transaction thread and will block until
     * the most recent asynchronous operation as completed. To aid in surfacing problems with the producer, this method
     * throws a runtime exception if the future chain has been halted due to an exception.
     * @return The current running hash upto and including the last record stream item sent in writeRecordStreamItems().
     */
    @NonNull
    @Override
    public Bytes getRunningHash() {
        blockUntilRunningHashesUpdated();
        return producer.getRunningHash();
    }

    /**
     * Get the previous, previous, previous runningHash of all block stream BlockItems. This is called on the handle
     * transaction thread and will block until he most recent asynchronous operation as completed. To aid in surfacing
     * problems with the producer, this method throws a runtime exception if the future chain has been halted due to an
     * exception.
     * @return the previous, previous, previous runningHash of all block stream BlockItems
     */
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
            @NonNull final BlockStateProofProducer blockStateProofProducer,
            @NonNull final CompletableFuture<BlockStateProof> blockPersisted) {
        // Ending a block is different in that we have to wait for a block proof. We don't want to hold up the next call
        // to the producer from running, so we need to fork the execution off the chain of futures. We want to attach
        // the new future to lastFutureRef so that it's only executed after lastFutureRef has successfully completed,
        // but it should not replace lastFutureRef. We do not want the next future to be chained off this one.

        // Capture the current last future without replacing it in lastFutureRef.
        final CompletableFuture<Void> currentLastFuture = lastFutureRef.get();

        // Fork off the execution, ensuring it starts after the current last future completes but doesn't block
        // the next operation from running.
        currentLastFuture.thenRunAsync(() -> producer.endBlock(blockStateProofProducer, blockPersisted), executor);
    }

    @Override
    public void writeConsensusEvent(@NonNull final ConsensusEvent consensusEvent) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeConsensusEvent(consensusEvent), executor));
    }

    @Override
    public void writeSystemTransaction(@NonNull final ConsensusTransaction systemTxn) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeSystemTransaction(systemTxn), executor));
    }

    @Override
    public void writeUserTransactionItems(@NonNull final ProcessUserTransactionResult items) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeUserTransactionItems(items), executor));
    }

    @Override
    public void writeStateChanges(@NonNull final StateChanges stateChanges) {
        doAsync(CompletableFuture.runAsync(() -> producer.writeStateChanges(stateChanges), executor));
    }

    @Override
    public void close() throws Exception {
        blockUntilRunningHashesUpdated();
        producer.close();
    }

    private void doAsync(@NonNull final CompletableFuture<Void> updater) {
        lastFutureRef.updateAndGet(lastFuture -> lastFuture.thenCompose(v -> updater));
    }

    private void awaitFutureCompletion(@NonNull final Future<?> future) {
        try {
            future.get(); // Block until the task completes.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for task to complete", e);
            throw new RuntimeException(e);
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
