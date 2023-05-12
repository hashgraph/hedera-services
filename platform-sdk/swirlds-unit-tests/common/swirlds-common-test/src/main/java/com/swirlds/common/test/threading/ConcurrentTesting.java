/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.threading;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;

import com.swirlds.common.threading.utility.ThrowingRunnable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A utility class for unit testing where multiple threads are executed at the same time
 */
public class ConcurrentTesting {
    private final List<Future<?>> futures = new ArrayList<>();
    private final List<Runnable> runnables = new ArrayList<>();

    /**
     * Add a runnable to execute in parallel
     *
     * @param runnable
     * 		the runnable to execute
     */
    public void addRunnable(final ThrowingRunnable runnable) {
        runnables.add(printExceptions(runnable));
    }

    /**
     * Run all previously submitted runnables in parallel. Waits at most the time specified for them to finish. Rethrows
     * any exceptions thrown by these runnables.
     *
     * @param timeout
     * 		the amount of time to wait
     * @param unit
     * 		the unit of time used
     */
    public void runFor(final long timeout, final TimeUnit unit) throws ExecutionException, InterruptedException {
        final ExecutorService executor = Executors.newFixedThreadPool(runnables.size());
        for (final Runnable runnable : runnables) {
            futures.add(executor.submit(runnable));
        }
        assertEventuallyTrue(
                () -> futures.stream().allMatch(Future::isDone),
                Duration.of(timeout, unit.toChronoUnit()),
                "operation did not complete on time");

        for (final Future<?> future : futures) {
            future.get(); // in case any exceptions were thrown
        }
    }

    /**
     * Same as {@link #runFor(long, TimeUnit)} where the unit is seconds
     */
    public void runForSeconds(final long timeout) throws ExecutionException, InterruptedException {
        runFor(timeout, TimeUnit.SECONDS);
    }

    private static Runnable printExceptions(final ThrowingRunnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (final Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        };
    }
}
