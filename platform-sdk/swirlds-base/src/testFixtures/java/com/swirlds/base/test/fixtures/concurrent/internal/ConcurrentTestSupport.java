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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A utility class for executing and waiting for concurrent tasks using a thread pool.
 */
public class ConcurrentTestSupport implements TestExecutor, AutoCloseable {

    private static final String NAME_PREFIX = ConcurrentTestSupport.class.getSimpleName();
    private static final AtomicInteger ID = new AtomicInteger(0);

    /**
     * It is intended for all tests utilizing {@code ConcurrentTestSupport} to execute sequentially.
     * A potential drawback is that tests may spend a significant portion of execution time waiting to acquire this lock.
     * In tests with external configurations tracking elapsed time and timeout values, this waiting time is accounted
     * for as test time.
     */
    private static final Object LOCK = new Object();

    private final Duration maxWaitTime;
    private final ExecutorService executorService;

    /**
     * Constructs a ConcurrentTestSupport instance with the specified maximum wait time.
     *
     * @param maxWaitTime The maximum time to wait for task completion. Must not be null.
     */
    public ConcurrentTestSupport(@NonNull final Duration maxWaitTime) {
        this.maxWaitTime = Objects.requireNonNull(maxWaitTime, "maxWaitTime must not be null");
        this.executorService = Executors.newCachedThreadPool(r -> {
            final Thread thread = new Thread(r);
            thread.setName(NAME_PREFIX + "-pool-" + this.hashCode() + ID.getAndDecrement());
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
     * Executes a collection of {@code Runnable} concurrently and waits for their completion.
     *
     * @param runnables The collection of {@code Runnable} to execute.
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
     * @param runnables An array of Runnables to execute.
     * @see ConcurrentTestSupport#executeAndWait(Collection)
     */
    public void executeAndWait(@NonNull final Runnable... runnables) {
        executeAndWait(List.of(Objects.requireNonNull(runnables, "runnables must not be null")));
    }

    /**
     * Submits a collection of Callables for execution concurrently and waits for their results.
     *
     * @param callables The collection of Callables to submit.
     * @param <V>       The type of the results returned by the Callables.
     * @return A list of results from the executed Callables. The order of the elements in the list is not guaranteed
     * @see ConcurrentTestSupport#executeAndWait(Collection)
     */
    @NonNull
    public <V> List<V> submitAndWait(@NonNull final Collection<Callable<V>> callables) {
        Objects.requireNonNull(callables, "callables must not be null");
        final Deque<V> result = new ConcurrentLinkedDeque<>();
        executeAndWait(callablesToRunners(callables, result));
        return List.copyOf(result);
    }

    /**
     * Submits a Callable for execution concurrently and waits for its results.
     *
     * @param callable A callable to submit.
     * @param <V>      The type of the results returned by the Callables.
     * @return result from the executed Callables. It can be null.
     * @see ConcurrentTestSupport#executeAndWait(Runnable...)
     */
    @Nullable
    public final <V> V submitAndWait(@NonNull final Callable<V> callable) {
        Objects.requireNonNull(callable, "callable must not be null");
        final Deque<V> result = new ArrayDeque<>();
        executeAndWait(toRunnableInto(callable, result));
        return result.peek();
    }

    /**
     * Closes this the underlying executorService so all pending activity is finished before finishing the test
     */
    @Override
    public void close() {
        executorService.close();
    }

    // Return a collections of runners from a collection of callables.
    // Results are accumulated  in result
    private static <V> Collection<Runnable> callablesToRunners(
            final Collection<Callable<V>> callables, final Deque<V> result) {
        return callables.stream().map(c -> toRunnableInto(c, result)).collect(Collectors.toList());
    }

    // Return a runners from a callable.
    // The result is accumulated  in result
    private static <V> Runnable toRunnableInto(final Callable<V> callable, final Deque<V> result) {
        return () -> {
            try {
                result.addLast(callable.call());
            } catch (Exception e) {
                throw new RuntimeException("Error in submitAndWait", e);
            }
        };
    }
}
