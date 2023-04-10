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

package com.swirlds.common.threading.manager.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps an executor service. Used to manage lifecycle of the executor.
 */
public class ManagedExecutorService implements ExecutorService {

    private final ExecutorService executorService;
    protected final Runnable throwIfInWrongPhase;

    /**
     * Wrap an executor service.
     *
     * @param executorService   the executor service to wrap
     * @param throwIfInWrongPhase a runnable that will throw an exception if the thread manager has not started or has been stopped
     */
    public ManagedExecutorService(
            @NonNull final ExecutorService executorService, @NonNull final Runnable throwIfInWrongPhase) {
        this.executorService = executorService;
        this.throwIfInWrongPhase = throwIfInWrongPhase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(final long timeout, final @NonNull TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> Future<T> submit(final @NonNull Callable<T> task) {
        throwIfInWrongPhase.run();
        return executorService.submit(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> Future<T> submit(final @NonNull Runnable task, final @NonNull T result) {
        throwIfInWrongPhase.run();
        return executorService.submit(task, result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Future<?> submit(final @NonNull Runnable task) {
        throwIfInWrongPhase.run();
        return executorService.submit(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> List<Future<T>> invokeAll(final @NonNull Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        throwIfInWrongPhase.run();
        return executorService.invokeAll(tasks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> List<Future<T>> invokeAll(
            final @NonNull Collection<? extends Callable<T>> tasks, final long timeout, final @NonNull TimeUnit unit)
            throws InterruptedException {
        throwIfInWrongPhase.run();
        return executorService.invokeAll(tasks, timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> T invokeAny(final @NonNull Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        throwIfInWrongPhase.run();
        return executorService.invokeAny(tasks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> T invokeAny(
            final @NonNull Collection<? extends Callable<T>> tasks, final long timeout, final @NonNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        throwIfInWrongPhase.run();
        return executorService.invokeAny(tasks, timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final @NonNull Runnable command) {
        throwIfInWrongPhase.run();
        executorService.execute(command);
    }
}
