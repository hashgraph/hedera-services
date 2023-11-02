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

package com.swirlds.base.test.fixtures.concurrent.internal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.base.test.fixtures.concurrent.TestExecutor;
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

    public ConcurrentTestSupport(Duration maxWaitTime) {
        this.maxWaitTime = Objects.requireNonNull(maxWaitTime, "maxWaitTime must not be null");
    }

    public ConcurrentTestSupport() {
        this(Duration.ofMinutes(1));
    }

    public void executeAndWait(Collection<Runnable> runnables) {
        final List<Callable<Void>> callables = runnables.stream()
                .map(r -> (Callable<Void>) () -> {
                    r.run();
                    return null;
                })
                .toList();
        submitAndWait(callables);
    }

    public void executeAndWait(Runnable... runnable) {
        executeAndWait(List.of(runnable));
    }

    public <V> List<V> submitAndWait(Collection<Callable<V>> callables) {
        return submitAndWait(callables.toArray(new Callable[callables.size()]));
    }

    public <V> List<V> submitAndWait(Callable<V>... callables) {
        final Lock callLock = new ReentrantLock();
        final Condition allPassedToExecutor = callLock.newCondition();
        callLock.lock();
        try {
            final Future<List<V>> futureForAll = SINGLE_EXECUTOR.submit(() -> {
                final List<Future<V>> futures = new ArrayList<>();
                callLock.lock();
                try {
                    Arrays.stream(callables).map(c -> poolExecutor.submit(c)).forEach(futures::add);
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

    private <T> T waitForDone(Future<T> future) throws InterruptedException, ExecutionException, TimeoutException {
        return waitForAllDone(List.of(future)).get(0);
    }

    private <T> List<T> waitForAllDone(List<Future<T>> futures)
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
