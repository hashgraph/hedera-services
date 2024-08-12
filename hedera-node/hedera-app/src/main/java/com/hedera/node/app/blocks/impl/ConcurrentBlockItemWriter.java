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

package com.hedera.node.app.blocks.impl;

import com.hedera.node.app.blocks.BlockItemWriter;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * ConcurrentBlockStreamWriter is a wrapper around a BlockItemWriter that allows for concurrent writes to multiple
 * writers. The write methods do not block so that the caller may continue to make progress.
 *
 * <p>This implementation of ConcurrentBlockStreamWriter, the doAsync method chains asynchronous tasks in such a
 *    way that they are executed sequentially by updating lastFutureRef with the new task that should run after the
 *    previous one completes. This chaining uses thenRunAsync, which creates a new stage that, when this stage
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
 */
public class ConcurrentBlockItemWriter implements BlockItemWriter {

    private final BlockItemWriter writer;
    private final ExecutorService executor;
    private volatile CompletableFuture<Void> lastFutureRef;

    /**
     * ConcurrentBlockItemWriter ensures that all the methods called on the writer are executed sequentially in the
     * order in which they are called. This is done by chaining the lastFutureRef to the next write method.
     * @param executor the executor service to use for writes
     * @param writer the writer to wrap
     */
    public ConcurrentBlockItemWriter(@NonNull final ExecutorService executor, @NonNull final BlockItemWriter writer) {
        this.executor = executor;
        this.writer = writer;
        this.lastFutureRef = CompletableFuture.completedFuture(null);
    }

    @Override
    public void openBlock(long blockNumber) {
        appendSerialAsyncTask(() -> writer.openBlock(blockNumber));
    }

    @Override
    public void writeItem(@NonNull Bytes serializedItem) {
        appendSerialAsyncTask(() -> writer.writeItem(serializedItem));
    }

    @Override
    public void closeBlock() {
        appendSerialAsyncTask(writer::closeBlock);
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
}
