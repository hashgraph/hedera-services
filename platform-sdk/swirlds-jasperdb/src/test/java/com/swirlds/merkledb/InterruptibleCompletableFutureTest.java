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

package com.swirlds.merkledb;

import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.merkledb.InterruptibleCompletableFuture.runAsyncInterruptibly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.RandomUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InterruptibleCompletableFutureTest {

    @Test
    void testSuccessfulExecution() throws ExecutionException, InterruptedException {
        final int value = nextInt();

        InterruptibleCompletableFuture<Integer> future = runAsyncInterruptibly(() -> value, ForkJoinPool.commonPool());
        assertEquals(value, future.asCompletableFuture().get());
    }

    @Test
    void testFailedExecution() {
        final RuntimeException exception = new RuntimeException("test");
        InterruptibleCompletableFuture<Integer> future = runAsyncInterruptibly(
                () -> {
                    throw exception;
                },
                ForkJoinPool.commonPool());
        ExecutionException executionException = assertThrows(
                ExecutionException.class, () -> future.asCompletableFuture().get());
        assertEquals(exception, executionException.getCause().getCause());
    }

    @Test
    void testCancel_noInterruption() {
        // execute in the same thread to make sure the completableFuture.cancel returns false
        InterruptibleCompletableFuture<Integer> future = runAsyncInterruptibly(RandomUtils::nextInt, Runnable::run);
        assertFalse(future.cancel());
        assertFalse(future.asCompletableFuture().isCancelled());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void testCancel_threadInterrupted() throws InterruptedException {
        CountDownLatch testLatch = new CountDownLatch(1);
        CountDownLatch taskLatch = new CountDownLatch(1);
        AtomicReference<Thread> threadRef = new AtomicReference<Thread>();

        Executor executor = command -> {
            Thread thread = new Thread(command);
            threadRef.set(thread);
            thread.start();
        };

        InterruptibleCompletableFuture<Integer> future = runAsyncInterruptibly(
                () -> {
                    testLatch.countDown();
                    try {
                        taskLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return nextInt();
                },
                executor);

        testLatch.await();
        assertTrue(future.cancel());
        assertTrue(future.asCompletableFuture().isCancelled());
        assertTrue(threadRef.get().isInterrupted());
    }
}
