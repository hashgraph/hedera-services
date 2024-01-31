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
import static org.assertj.core.api.Assertions.fail;

import com.swirlds.base.test.fixtures.concurrent.internal.ConcurrentTestSupport;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    void testMultipleCallsInOneConcurrentTestSupport() {
        // given
        final ConcurrentTestSupport concurrentTestSupport = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final Runnable shortRunningTask = () -> sleep(10);
        final Runnable longRunningTask = () -> sleep(2_000);
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        checkAllAsync(
                doAsync(
                        () -> assertThatThrownBy(() -> concurrentTestSupport.executeAndWait(longRunningTask))
                                .isInstanceOf(RuntimeException.class)
                                .hasCauseInstanceOf(TimeoutException.class),
                        executor)
                ,
                doAsync(
                        () -> assertThatNoException()
                                .isThrownBy(() -> concurrentTestSupport.executeAndWait(shortRunningTask)),
                        executor)
        );
    }

    @Test
    void testMultipleCallsInMultipleConcurrentTestSupport() {
        // given
        final ConcurrentTestSupport concurrentTestSupport1 = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final ConcurrentTestSupport concurrentTestSupport2 = new ConcurrentTestSupport(Duration.ofSeconds(1));
        final Runnable shortRunningTask = () -> sleep(10);
        final Runnable longRunningTask = () -> sleep(2_000);
        final ExecutorService executorService = Executors.newFixedThreadPool(4);

        // then
        checkAllAsync(
                doAsync(
                        () -> {//A long-running task that will block concurrentTestSupport1 for one minute
                            assertThatThrownBy(() -> concurrentTestSupport1.executeAndWait(longRunningTask))
                                    .isInstanceOf(RuntimeException.class)
                                    .hasCauseInstanceOf(TimeoutException.class);
                        },
                        executorService),
                doAsync(
                        () -> {
                            assertThatThrownBy(() -> concurrentTestSupport2.executeAndWait(longRunningTask))
                                    .isInstanceOf(RuntimeException.class)
                                    .hasCauseInstanceOf(TimeoutException.class);
                        },
                        executorService),
                doAsync(
                        () -> {
                            assertThatNoException()
                                    .isThrownBy(() -> concurrentTestSupport1.executeAndWait(longRunningTask));
                        },
                        executorService),
                doAsync(
                        () -> {
                            assertThatNoException()
                                    .isThrownBy(() -> concurrentTestSupport2.executeAndWait(shortRunningTask));
                        },
                        executorService));
    }

    private static CompletableFuture<Void> doAsync(final Runnable future, final Executor executor) {
        return CompletableFuture.runAsync(future, executor);
    }

    private static void checkAllAsync(CompletableFuture<?>... cf) {
        try {
            CompletableFuture.allOf(cf).get();
        } catch (InterruptedException | ExecutionException e) {
            fail("No throwable expected", e);
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
