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

package com.swirlds.base.test.internal.fixtures;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.swirlds.base.test.fixtures.concurrent.internal.ConcurrentTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class ConcurrentTestSupportTest {

    private static final long MAX_EXECUTION_WAIT_TIME_IN_SEC = 5;

    @Test
    void testATaskThatRunsShort() {
        // given
        final ConcurrentTestSupport concurrentTestSupport = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final Runnable runnable = () -> sleep(10);

        // then
        assertThatNoException().isThrownBy(() -> concurrentTestSupport.executeAndWait(runnable));
    }

    @Test
    void testMultipleTasksThatRunsShort() {
        // given
        final ConcurrentTestSupport concurrentTestSupport = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final List<Runnable> runnables =
                IntStream.range(10, 20).mapToObj(i -> (Runnable) () -> sleep(i)).toList();

        // then
        assertThatNoException().isThrownBy(() -> concurrentTestSupport.executeAndWait(runnables));
    }

    @Test
    void testATaskThatRunsTooLong() {
        // given
        final ConcurrentTestSupport concurrentTestSupport = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final Runnable runnable = () -> sleep(1_010);

        // then
        assertThatThrownBy(() -> concurrentTestSupport.executeAndWait(runnable)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testMultipleTasksThatRunsTooLong() {
        // given
        final ConcurrentTestSupport concurrentTestSupport = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final List<Runnable> runnables = IntStream.range(500, 2_000)
                .mapToObj(i -> (Runnable) () -> sleep(i))
                .toList();

        // then
        assertThatThrownBy(() -> concurrentTestSupport.executeAndWait(runnables))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testMutlipleCallsInOneConcurrentTestSupport() {
        // given
        final ConcurrentTestSupport concurrentTestSupport = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final Runnable shortRunningTask = () -> sleep(10);
        final Runnable longRunningTask = () -> sleep(2_000);
        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        // when
        final Future<?> longRunningResult =
                executorService.submit(() -> concurrentTestSupport.executeAndWait(longRunningTask));
        final Future<?> shortRunningResult =
                executorService.submit(() -> concurrentTestSupport.executeAndWait(shortRunningTask));

        // then
        assertThatNoException().isThrownBy(() -> waitForDone(shortRunningResult));
        assertThatThrownBy(() -> waitForDone(longRunningResult))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void testMutlipleCallsInMultipleConcurrentTestSupport() {
        // given
        final ConcurrentTestSupport concurrentTestSupport1 = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final ConcurrentTestSupport concurrentTestSupport2 = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final Runnable shortRunningTask = () -> sleep(10);
        final Runnable longRunningTask = () -> sleep(2_000);
        final ExecutorService executorService = Executors.newFixedThreadPool(4);

        // when
        final Future<?> longRunningResult1 = executorService.submit(() -> {
            RuntimeException thrown = catchThrowableOfType(
                    () -> concurrentTestSupport1.executeAndWait(longRunningTask), RuntimeException.class);
            throw thrown;
        });
        final Future<?> longRunningResult2 = executorService.submit(() -> {
            RuntimeException thrown = catchThrowableOfType(
                    () -> concurrentTestSupport2.executeAndWait(longRunningTask), RuntimeException.class);
            throw thrown;
        });
        final Future<?> shortRunningResult1 = executorService.submit(() -> {
            assertThatNoException().isThrownBy(() -> concurrentTestSupport1.executeAndWait(shortRunningTask));
        });
        final Future<?> shortRunningResult2 = executorService.submit(() -> {
            assertThatNoException().isThrownBy(() -> concurrentTestSupport2.executeAndWait(shortRunningTask));
        });

        // then
        assertThatNoException().isThrownBy(() -> waitForDone(shortRunningResult1));
        assertThatNoException().isThrownBy(() -> waitForDone(shortRunningResult2));
        assertThatThrownBy(() -> waitForDone(longRunningResult1))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> waitForDone(longRunningResult2))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private static <T> T waitForDone(final Future<T> future)
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            return future.get(MAX_EXECUTION_WAIT_TIME_IN_SEC, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause != null && cause instanceof AssertionError) {
                throw (AssertionError) cause;
            }
            throw e;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException("Can not sleep", e);
        }
    }
}
