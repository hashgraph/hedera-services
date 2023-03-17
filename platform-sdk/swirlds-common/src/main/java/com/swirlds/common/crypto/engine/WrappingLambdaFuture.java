/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto.engine;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * A lightweight wrapping future implementation that delegates to the inner future. This implementation uses {@link
 * Supplier} to provide the inner future and the final value to be returned.
 *
 * @param <InnerReturnType>
 * 		the type of object returned by the inner {@link Future}
 * @param <ReturnType>
 * 		the type of object returned by the {@link #get()} methods
 */
public class WrappingLambdaFuture<ReturnType, InnerReturnType> implements Future<ReturnType> {

    private final Supplier<Future<InnerReturnType>> innerFutureSupplier;
    private final Supplier<ReturnType> returnTypeSupplier;
    private volatile Future<InnerReturnType> innerFuture;

    /**
     * Internal Use - Default Constructor
     *
     * @param innerFutureSupplier
     * 		the supplier of the inner future
     * @param returnTypeSupplier
     * 		the supplier of the ReturnType
     */
    public WrappingLambdaFuture(
            final Supplier<Future<InnerReturnType>> innerFutureSupplier,
            final Supplier<ReturnType> returnTypeSupplier) {
        this.innerFutureSupplier = innerFutureSupplier;
        this.returnTypeSupplier = returnTypeSupplier;
    }

    /**
     * Attempts to cancel execution of this task. This attempt will fail if the task has already completed, has already
     * been cancelled, or could not be cancelled for some other reason. If successful, and this task has not started
     * when {@code cancel} is called, this task should never run. If the task has already started, then the
     * {@code mayInterruptIfRunning} parameter determines whether the thread executing this task should be interrupted
     * in an attempt to stop the task.
     *
     * <p>
     * After this method returns, subsequent calls to {@link #isDone} will always return {@code true}. Subsequent calls
     * to {@link #isCancelled} will always return {@code true} if this method returned {@code true}.
     *
     * @param mayInterruptIfRunning
     *        {@code true} if the thread executing this task should be interrupted; otherwise,
     * 		in-progress tasks are allowed to complete
     * @return {@code false} if the task could not be cancelled, typically because it has already completed normally;
     *        {@code true} otherwise
     */
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        initialize();

        return innerFuture.cancel(mayInterruptIfRunning);
    }

    /**
     * Returns {@code true} if this task was cancelled before it completed normally.
     *
     * @return {@code true} if this task was cancelled before it completed
     */
    @Override
    public boolean isCancelled() {
        initialize();

        return (innerFuture != null) && innerFuture.isCancelled();
    }

    /**
     * Returns {@code true} if this task completed.
     * <p>
     * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method
     * will return {@code true}.
     *
     * @return {@code true} if this task completed
     */
    @Override
    public boolean isDone() {
        initialize();

        return (innerFuture != null) && innerFuture.isDone();
    }

    /**
     * Waits if necessary for the computation to complete, and then retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException
     * 		if the computation was cancelled
     * @throws InterruptedException
     * 		if the current thread was interrupted while waiting
     */
    @Override
    public ReturnType get() throws InterruptedException, ExecutionException {
        initialize();
        innerFuture.get();

        return returnTypeSupplier.get();
    }

    /**
     * Waits if necessary for at most the given time for the computation to complete, and then retrieves its result, if
     * available.
     *
     * @param timeout
     * 		the maximum time to wait
     * @param unit
     * 		the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException
     * 		if the computation was cancelled
     * @throws InterruptedException
     * 		if the current thread was interrupted while waiting
     */
    @Override
    public ReturnType get(final long timeout, final TimeUnit unit)
            throws InterruptedException, TimeoutException, ExecutionException {
        initialize();
        innerFuture.get(timeout, unit);

        return returnTypeSupplier.get();
    }

    /**
     * Populates the {@code innerFuture} field with the minimal amount of synchronization required to guarantee thread
     * safety.
     */
    private synchronized void initialize() {
        if (innerFuture == null) {
            innerFuture = innerFutureSupplier.get();
        }
    }
}
