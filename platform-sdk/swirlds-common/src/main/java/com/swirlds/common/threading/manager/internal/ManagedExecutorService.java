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

import com.swirlds.common.threading.interrupt.Uninterruptable;
import com.swirlds.common.utility.Lifecycle;
import com.swirlds.common.utility.LifecyclePhase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps an executor service. Used to manage lifecycle of the executor.
 */
public class ManagedExecutorService implements ExecutorService, Lifecycle {

    private final ExecutorService executorService;

    /**
     * The current lifecycle phase. It is possible that this value may be changed from NOT_STARTED to STARTED
     * while another thread is concurrently reading this variable. This is not a problem, since if the thread
     * incorrectly things the state is NOT_STARTED, the only side effect is that a no-op lambda is created.
     */
    private LifecyclePhase lifecyclePhase = LifecyclePhase.NOT_STARTED;

    private final CountDownLatch startedLatch = new CountDownLatch(1);

    /**
     * Wrap an executor service.
     *
     * @param executorService the executor service to wrap
     */
    public ManagedExecutorService(@NonNull final ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return lifecyclePhase;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Is not thread safe for multiple callers to call into start() and stop() simultaneously.
     * </p>
     */
    @Override
    public void start() {
        throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
        startedLatch.countDown();
        lifecyclePhase = LifecyclePhase.STARTED;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Is not thread safe for multiple callers to call into start() and stop() simultaneously.
     * </p>
     */
    @Override
    public void stop() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        executorService.shutdown();
        lifecyclePhase = LifecyclePhase.STOPPED;
    }

    /**
     * Wrap a runnable so that it will be unable to be fully executed until the executor service is started.
     *
     * @param runnable the runnable to wrap
     * @return a runnable that will block until the service is started, then will execute the provided runnable
     */
    protected @NonNull Runnable wrapRunnable(@NonNull final Runnable runnable) {
        return () -> {
            Uninterruptable.abortAndThrowIfInterrupted(
                    startedLatch::await, "interrupted while waiting for executor service to start");
            runnable.run();
        };
    }

    /**
     * Wrap a callable so that it will be unable to be fully executed until the executor service is started.
     *
     * @param callable the callable to wrap
     * @return a callable that will block until the service is started, then will execute the provided callable
     */
    protected @NonNull <T> Callable<T> wrapCallable(@NonNull final Callable<T> callable) {
        return () -> {
            Uninterruptable.abortAndThrowIfInterrupted(
                    startedLatch::await, "interrupted while waiting for executor service to start");
            return callable.call();
        };
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
        if (lifecyclePhase == LifecyclePhase.NOT_STARTED) {
            return executorService.submit(wrapCallable(task));
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return executorService.submit(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> Future<T> submit(final @NonNull Runnable task, final @NonNull T result) {
        if (lifecyclePhase == LifecyclePhase.NOT_STARTED) {
            return executorService.submit(wrapRunnable(task), result);
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return executorService.submit(task, result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Future<?> submit(final @NonNull Runnable task) {
        if (lifecyclePhase == LifecyclePhase.NOT_STARTED) {
            return executorService.submit(wrapRunnable(task));
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return executorService.submit(task);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> List<Future<T>> invokeAll(final @NonNull Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        if (lifecyclePhase == LifecyclePhase.NOT_STARTED) {
            final List<Callable<T>> wrappedTasks = new ArrayList<>(tasks.size());
            for (final var task : tasks) {
                wrappedTasks.add(wrapCallable(task));
            }
            return executorService.invokeAll(wrappedTasks);
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return executorService.invokeAll(tasks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> List<Future<T>> invokeAll(
            final @NonNull Collection<? extends Callable<T>> tasks, final long timeout, final @NonNull TimeUnit unit)
            throws InterruptedException {
        if (lifecyclePhase == LifecyclePhase.NOT_STARTED) {
            final List<Callable<T>> wrappedTasks = new ArrayList<>(tasks.size());
            for (final var task : tasks) {
                wrappedTasks.add(wrapCallable(task));
            }
            return executorService.invokeAll(wrappedTasks, timeout, unit);
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return executorService.invokeAll(tasks, timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> T invokeAny(final @NonNull Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return executorService.invokeAny(tasks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <T> T invokeAny(
            final @NonNull Collection<? extends Callable<T>> tasks, final long timeout, final @NonNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return executorService.invokeAny(tasks, timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final @NonNull Runnable command) {
        executorService.execute(wrapRunnable(command));
    }
}
