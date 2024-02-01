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

package com.swirlds.base.test.fixtures.concurrent.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.base.test.fixtures.concurrent.TestExecutor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A utility class for executing and waiting for concurrent tasks using a thread pool.
 */
public class ConcurrentTestSupport implements TestExecutor, Closeable {

    public static final String NAME_PREFIX = ConcurrentTestSupport.class.getSimpleName();

    private static final Object LOCK = new Object();
    private static final AtomicInteger ID = new AtomicInteger(0);

    private final Duration maxWaitTime;
    private final ExecutorService executorService;

    /**
     * Constructs a ConcurrentTestSupport instance with the specified maximum wait time.
     *
     * @param maxWaitTime The maximum time to wait for task completion. Must not be null.
     */
    public ConcurrentTestSupport(@NonNull final Duration maxWaitTime) {
        this.maxWaitTime = Objects.requireNonNull(maxWaitTime, "maxWaitTime must not be null");
        executorService = Executors.newCachedThreadPool(r -> {
            final Thread thread = new Thread(r);
            thread.setName(NAME_PREFIX + "-" + ConcurrentTestSupport.this.hashCode() + ID.getAndDecrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Constructs a ConcurrentTestSupport instance with a default maximum wait time of 1 minute.
     */
    public ConcurrentTestSupport() {
        this(Duration.ofMinutes(1));
    }

    /**
     * Executes a collection of Runnables concurrently and waits for their completion.
     *
     * @param runnables The collection of Runnables to execute.
     */
    public void executeAndWait(@NonNull final Collection<Runnable> runnables) {
        Objects.requireNonNull(runnables, "runnables must not be null");
        synchronized (LOCK) {
            try {
                CompletableFuture.allOf(runnables.stream()
                                .map(r -> CompletableFuture.runAsync(r, executorService))
                                .toArray(CompletableFuture[]::new))
                        .get(maxWaitTime.toMillis(), MILLISECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                throw new RuntimeException(e.getClass().getSimpleName() + " in submitAndWait", e);
            }
        }
    }

    /**
     * Executes an array of Runnables concurrently and waits for their completion.
     *
     * @param runnable An array of Runnables to execute.
     */
    public void executeAndWait(@NonNull final Runnable... runnable) {
        executeAndWait(List.of(Objects.requireNonNull(runnable, "runnables must not be null")));
    }

    /**
     * Submits a collection of Callables for execution concurrently and waits for their results.
     *
     * @param callables The collection of Callables to submit.
     * @param <V>       The type of the results returned by the Callables.
     * @return A list of results from the executed Callables.
     */
    @NonNull
    public <V> List<V> submitAndWait(@NonNull final Collection<Callable<V>> callables) {
        Objects.requireNonNull(callables, "callables must not be null");

        List<V> result = new ArrayList<>();
        executeAndWait(callablesToRunners(callables, result));
        return result;
    }

    /**
     * Submits an array of Callables for execution concurrently and waits for their results.
     *
     * @param callable A callable to submit.
     * @param <V>      The type of the results returned by the Callables.
     * @return A list of results from the executed Callables.
     */
    @NonNull
    public final <V> List<V> submitAndWait(@NonNull final Callable<V> callable) {
        Objects.requireNonNull(callable, "callables must not be null");
        return submitAndWait(List.of(callable));
    }

    private static <V> Collection<Runnable> callablesToRunners(
            final Collection<Callable<V>> callables, final List<V> result) {
        return callables.stream().map(c -> toRunnableInto(c, result)).collect(Collectors.toList());
    }

    private static <V> Runnable toRunnableInto(final Callable<V> c, final List<V> result) {
        return () -> {
            try {
                result.add(c.call());
            } catch (Exception e) {
                throw new RuntimeException("Error in submitAndWait", e);
            }
        };
    }

    /**
     * Closes this stream and releases any system resources associated with it. If the stream is already closed then
     * invoking this method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised to relinquish the underlying resources and to
     * internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     */
    @Override
    public void close() {
        executorService.close();
    }
}
