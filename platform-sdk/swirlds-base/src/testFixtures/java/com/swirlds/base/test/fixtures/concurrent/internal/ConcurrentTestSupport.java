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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A utility class for executing and waiting for concurrent tasks using a thread pool.
 */
public class ConcurrentTestSupport implements TestExecutor {

    private static final ExecutorService SINGLE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r);
        thread.setName("ParallelStressTester-SingleExecutor");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicLong poolThreadCounter = new AtomicLong(0);

    private final ExecutorService poolExecutor = Executors.newCachedThreadPool(r -> {
        final Thread thread = new Thread(r);
        thread.setName("ParallelStressTester-Pool-" + poolThreadCounter.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    private final Duration maxWaitTime;

    /**
     * Constructs a ConcurrentTestSupport instance with the specified maximum wait time.
     *
     * @param maxWaitTime The maximum time to wait for task completion.
     *                    Must not be null.
     */
    public ConcurrentTestSupport(@NonNull final Duration maxWaitTime) {
        this.maxWaitTime = Objects.requireNonNull(maxWaitTime, "maxWaitTime must not be null");
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
        final List<Callable<Void>> callables = runnables.stream()
                .map(r -> (Callable<Void>) () -> {
                    r.run();
                    return null;
                })
                .toList();
        submitAndWait(callables);
    }

    /**
     * Executes an array of Runnables concurrently and waits for their completion.
     *
     * @param runnable An array of Runnables to execute.
     */
    public void executeAndWait(@NonNull final Runnable... runnable) {
        executeAndWait(List.of(runnable));
    }

    /**
     * Submits a collection of Callables for execution concurrently and waits for their results.
     *
     * @param callables The collection of Callables to submit.
     * @param <V>       The type of the results returned by the Callables.
     * @return A list of results from the executed Callables.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <V> List<V> submitAndWait(@NonNull final Collection<Callable<V>> callables) {
        Objects.requireNonNull(callables, "callables must not be null");
        return submitAndWait(callables.toArray(new Callable[0]));
    }

    /**
     * Submits an array of Callables for execution concurrently and waits for their results.
     *
     * @param callables An array of Callables to submit.
     * @param <V>       The type of the results returned by the Callables.
     * @return A list of results from the executed Callables.
     */
    @SafeVarargs
    @NonNull
    public final <V> List<V> submitAndWait(@NonNull final Callable<V>... callables) {
        final Lock callLock = new ReentrantLock();
        final Condition allPassedToExecutor = callLock.newCondition();
        callLock.lock();
        try {
            final Future<List<V>> futureForAll = SINGLE_EXECUTOR.submit(() -> {
                final List<Future<V>> futures = new ArrayList<>();
                callLock.lock();
                try {
                    Arrays.stream(callables).map(poolExecutor::submit).forEach(futures::add);
                    allPassedToExecutor.signal(); // now all futures in results and the original method can return
                    // In the single executor singleton we will wait until all tasks are done.
                    // By doing so we ensure that only 1 call to this utils is executed in parallel. All other calls
                    // will be queued.
                    try {
                        return waitForAllDone(futures);
                    } catch (Exception e) {
                        futures.forEach(f -> f.cancel(true));
                        throw new RuntimeException("Error in wait", e);
                    }
                } finally {
                    callLock.unlock();
                }
            });
            allPassedToExecutor.await();
            try {
                return waitForDone(futureForAll);
            } catch (Exception e) {
                throw new RuntimeException("Error in wait", e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error in parallel execution", e);
        } finally {
            callLock.unlock();
        }
    }

    /**
     * Waits for the completion of a Future and retrieves its result.
     *
     * @param future The Future to wait for.
     * @param <T>    The type of the result.
     * @return The result of the Future.
     * @throws InterruptedException If the waiting thread is interrupted.
     * @throws ExecutionException   If an exception occurs while executing the Future.
     * @throws TimeoutException     If the waiting time exceeds the maximum wait time.
     */
    @NonNull
    private <T> T waitForDone(@NonNull final Future<T> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return waitForAllDone(List.of(future)).get(0);
    }

    /**
     * Waits for the completion of a list of Futures and retrieves their results.
     *
     * @param futures The list of Futures to wait for.
     * @param <T>     The type of the results.
     * @return A list of results from the Futures.
     * @throws InterruptedException If the waiting thread is interrupted.
     * @throws ExecutionException   If an exception occurs while executing any of the Futures.
     * @throws TimeoutException     If the waiting time exceeds the maximum wait time.
     */
    @NonNull
    private <T> List<T> waitForAllDone(@NonNull final List<Future<T>> futures)
            throws InterruptedException, ExecutionException, TimeoutException {
        final List<T> results = new ArrayList<>();
        final long startTime = System.currentTimeMillis();
        if (futures.isEmpty()) {
            return List.of();
        }
        for (final Future<T> future : futures) {
            final long maxWaitTimeMs = maxWaitTime.toMillis() - (System.currentTimeMillis() - startTime);
            final T val = future.get(maxWaitTimeMs, MILLISECONDS);
            results.add(val);
        }
        return results;
    }
}
