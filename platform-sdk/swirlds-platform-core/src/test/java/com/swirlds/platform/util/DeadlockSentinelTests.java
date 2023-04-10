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

package com.swirlds.platform.util;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndLogIfInterrupted;
import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static com.swirlds.platform.DispatchBuilderUtils.getDefaultDispatchConfiguration;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.error.DeadlockTrigger;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeadlockSentinel Tests")
class DeadlockSentinelTests {

    /**
     * Start deadlocked threads. Returns an auto-closeable object that stopps the deadlocked threads.
     */
    private AutoCloseable startDeadlock() throws InterruptedException {
        final Lock lock1 = new ReentrantLock();
        final Lock lock2 = new ReentrantLock();

        final CountDownLatch waitForThreads = new CountDownLatch(2);
        final CountDownLatch waitForDeadlock = new CountDownLatch(1);

        final Thread thread1 = getStaticThreadManager()
                .newThreadConfiguration()
                .setThreadName("thread1")
                .setRunnable(() -> {
                    lock1.lock();
                    waitForThreads.countDown();
                    abortAndLogIfInterrupted(waitForDeadlock::await, "test thread interrupted");
                    try {
                        lock2.lockInterruptibly();
                    } catch (InterruptedException e) {
                        // ignored
                    }
                })
                .build(true);
        final Thread thread2 = getStaticThreadManager()
                .newThreadConfiguration()
                .setThreadName("thread2")
                .setRunnable(() -> {
                    lock2.lock();
                    waitForThreads.countDown();
                    abortAndLogIfInterrupted(waitForDeadlock::await, "test thread interrupted");
                    try {
                        lock1.lockInterruptibly();
                    } catch (InterruptedException e) {
                        // ignored
                    }
                })
                .build(true);

        waitForThreads.await();
        waitForDeadlock.countDown();

        return () -> {
            thread1.interrupt();
            thread2.interrupt();
        };
    }

    @Test
    @DisplayName("Basic Deadlock Test")
    void basicDeadlockTest() throws InterruptedException {

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        try (final DeadlockSentinel sentinel =
                new DeadlockSentinel(getStaticThreadManager(), dispatchBuilder, Duration.ofMillis(50))) {

            final AtomicInteger deadlockCount = new AtomicInteger(0);
            dispatchBuilder.registerObserver(this, DeadlockTrigger.class, deadlockCount::getAndIncrement);

            dispatchBuilder.start();
            sentinel.start();

            // Wait a little while. Sentinel should not detect any deadlocks yet.
            MILLISECONDS.sleep(200);
            assertEquals(0, deadlockCount.get(), "no deadlocks should have been collected");

            try (final AutoCloseable deadlock = startDeadlock()) {

                assertEventuallyTrue(
                        () -> deadlockCount.get() > 0, Duration.ofSeconds(1), "should have detected deadlock by now");

            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
