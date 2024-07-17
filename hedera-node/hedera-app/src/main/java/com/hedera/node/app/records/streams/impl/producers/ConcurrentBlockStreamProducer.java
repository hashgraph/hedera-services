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

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.node.app.records.streams.ProcessUserTransactionResult;
import com.hedera.node.app.records.streams.impl.BlockStreamProducer;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConcurrentBlockStreamProducer implements BlockStreamProducer {

    /** The logger */
    private static final Logger logger = LogManager.getLogger(ConcurrentBlockStreamProducer.class);

    private final ExecutorService executor;
    private final BlockStreamProducer producer;
    //    private final AtomicReference<CompletableFuture<Void>> lastFutureRef;
    private volatile CompletableFuture<Void> lastFutureRef;

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
     * <p>This implementation of BlockStreamProducerConcurrent, the appendSerialAsyncTask method chains asynchronous
     * tasks in
     * such a
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
    public ConcurrentBlockStreamProducer(
            @NonNull final ExecutorService executor, @NonNull final BlockStreamProducer producer) {
        this.executor = executor;
        this.producer = producer;
        // this.lastFutureRef = new AtomicReference<>(CompletableFuture.completedFuture(null));
        this.lastFutureRef = CompletableFuture.completedFuture(null);
    }

    /** {@inheritDoc} */
    @Override
    public void initFromLastBlock(@NonNull final RunningHashes runningHashes, final long lastBlockNumber) {
        appendSerialAsyncTask(() -> producer.initFromLastBlock(runningHashes, lastBlockNumber));
    }

    /**
     * {@inheritDoc}
     * Get the current running hash of block items. This is called on the handle transaction thread and will block until
     * the most recent asynchronous operation as completed. To aid in surfacing problems with the producer, this method
     * throws a runtime exception if the future chain has been halted due to an exception.
     * @return The current running hash upto and including the last record stream item sent in writeRecordStreamItems().
     */
    @Override
    @NonNull
    public Bytes getRunningHash() {
        blockUntilRunningHashesUpdated();
        return producer.getRunningHash();
    }

    /**
     * {@inheritDoc}
     * Get the previous, previous, previous runningHash of all block stream BlockItems. This is called on the handle
     * transaction thread and will block until he most recent asynchronous operation as completed. To aid in surfacing
     * problems with the producer, this method throws a runtime exception if the future chain has been halted due to an
     * exception.
     * @return the previous, previous, previous runningHash of all block stream BlockItems
     */
    @Override
    @Nullable
    public synchronized Bytes getNMinus3RunningHash() {
        blockUntilRunningHashesUpdated();
        return producer.getNMinus3RunningHash();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void beginBlock() {
        appendSerialAsyncTask(producer::beginBlock);
    }

    /** {@inheritDoc} */
    @Override
    public void endBlock() {
        appendSerialAsyncTask(producer::endBlock);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public CompletableFuture<BlockEnder> blockEnder(@NonNull final BlockEnder.Builder builder) {
        // We want to end the block after the previous lastFuture has completed. Only this time, we also must return a
        // future with the result of the endBlock call.
        final CompletableFuture<BlockEnder> enderFuture = new CompletableFuture<>();

        // Chain the operation such that enderFuture is completed with the BlockEnder instance
        // once producer.endBlock() completes.
        appendSerialAsyncTask(() -> producer.blockEnder(builder)
                .thenAccept(enderFuture::complete)
                .exceptionally(ex -> {
                    // Handle exceptions by completing enderFuture exceptionally.
                    enderFuture.completeExceptionally(ex);
                    return null; // CompletableFuture's exceptionally function requires a return value.
                }));

        return enderFuture;
    }

    /** {@inheritDoc} */
    @Override
    public void writeConsensusEvent(@NonNull final ConsensusEvent consensusEvent) {
        appendSerialAsyncTask(() -> producer.writeConsensusEvent(consensusEvent));
    }

    /** {@inheritDoc} */
    @Override
    public void writeSystemTransaction(@NonNull final ConsensusTransaction systemTxn) {
        appendSerialAsyncTask(() -> producer.writeSystemTransaction(systemTxn));
    }

    /** {@inheritDoc} */
    @Override
    public void writeUserTransactionItems(@NonNull final ProcessUserTransactionResult items) {
        appendSerialAsyncTask(() -> producer.writeUserTransactionItems(items));
    }

    @Override
    public void writeStateChanges(@NonNull final StateChanges stateChanges) {
        appendSerialAsyncTask(() -> producer.writeStateChanges(stateChanges));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void close() throws Exception {
        blockUntilRunningHashesUpdated();
        producer.close();
    }

    private synchronized void appendSerialAsyncTask(@NonNull final Runnable task) {
        // Check if the lastFuture completed exceptionally
        if (lastFutureRef.isCompletedExceptionally()) {
            lastFutureRef
                    .exceptionally(ex -> {
                        // Throw a RuntimeException with the original exception
                        throw new CompletionException(ex);
                    })
                    .join(); // This forces the exception to be thrown if present
        }
        // If lastFuture did not complete exceptionally, chain the task so that task only executes after
        // lastFutureRef has completed.
        lastFutureRef = lastFutureRef.thenRunAsync(task, executor);
    }

    private void awaitFutureCompletion(@NonNull final Future<?> future) {
        try {
            future.get(); // Block until the task completes.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for task to complete", e);
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            logger.error("Error occurred during asynchronous task execution", e.getCause());
            throw new RuntimeException(e);
        }
    }

    private void blockUntilRunningHashesUpdated() {
        Future<?> currentUpdateTask = lastFutureRef;
        if (currentUpdateTask == null) return;
        // Wait for the update task to complete and handle potential interruptions.
        awaitFutureCompletion(currentUpdateTask);
    }
}
