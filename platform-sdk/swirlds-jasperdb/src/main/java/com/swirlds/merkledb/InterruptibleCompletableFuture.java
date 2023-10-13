/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A wrapper around {@link CompletableFuture} that supports interruption
 * when the CompletableFuture is canceled. This wrapper is necessary because {@link CompletableFuture} doesn't
 * interrupt executing thread on cancellation.
 *
 */
public class InterruptibleCompletableFuture<T> {

    /** The wrapped CompletableFuture */
    private volatile CompletableFuture<T> completableFuture;

    /** The thread currently executing the task, if any */
    private volatile Thread executingThread;

    /**
     * Submits a task for asynchronous execution and returns a CompletableFuture
     * representing the pending results of the task.
     * <p>
     * The task will be interrupted if the CompletableFuture is canceled.
     *
     * @param task the task to execute
     * @param executor the executor to use for asynchronous execution
     * @return the CompletableFuture representing the result of the task
     */
    public static <T> InterruptibleCompletableFuture<T> runAsyncInterruptibly(Callable<T> task, Executor executor) {
        InterruptibleCompletableFuture<T> interruptibleCompletableFuture = new InterruptibleCompletableFuture<>();
        interruptibleCompletableFuture.completableFuture = CompletableFuture.supplyAsync(
                () -> {
                    interruptibleCompletableFuture.executingThread = Thread.currentThread();
                    try {
                        try {
                            return task.call();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } finally {
                        interruptibleCompletableFuture.executingThread = null;
                    }
                },
                executor);
        return interruptibleCompletableFuture;
    }

    /**
     * Attempts to cancel the wrapped CompletableFuture and interrupts the
     * executing thread.
     *
     * @return {@code false} if the task could not be canceled, typically
     * because it has already completed normally; {@code true} otherwise
     */
    public boolean cancel() {
        boolean cancelled = completableFuture.cancel(true);
        if (cancelled && executingThread != null) {
            executingThread.interrupt();
        }
        return cancelled;
    }

    /**
     * Returns the wrapped CompletableFuture.
     *
     * @return the wrapped CompletableFuture
     */
    public CompletableFuture<T> asCompletableFuture() {
        return completableFuture;
    }
}
