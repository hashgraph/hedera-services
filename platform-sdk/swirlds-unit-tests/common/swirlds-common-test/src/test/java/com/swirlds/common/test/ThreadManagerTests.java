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

package com.swirlds.common.test;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.manager.StartableThreadManager;
import com.swirlds.common.threading.manager.ThreadManagerFactory;
import com.swirlds.common.utility.LifecycleException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThreadManager Tests")
class ThreadManagerTests {

    @Test
    @DisplayName("Thread Test")
    void threadTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicBoolean didThreadRun = new AtomicBoolean(false);

            final Thread thread = threadManager
                    .newThreadConfiguration()
                    .setRunnable(() -> didThreadRun.set(true))
                    .build();

            assertThrows(LifecycleException.class, thread::start);
            assertFalse(thread.isAlive());

            // The thread should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertFalse(didThreadRun.get());

            threadManager.start();
            thread.start();

            AssertionUtils.assertEventuallyEquals(true, didThreadRun::get, Duration.ofSeconds(1), "thread did not run");
        }
    }

    @Test
    @DisplayName("StoppableThread Test")
    void stoppableThreadTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count1 = new AtomicLong();
            final AtomicLong count2 = new AtomicLong();

            final StoppableThread thread1 = threadManager
                    .newStoppableThreadConfiguration()
                    .setWork(count1::getAndIncrement)
                    .build();

            final StoppableThread thread2 = threadManager
                    .newStoppableThreadConfiguration()
                    .setWork(count2::getAndIncrement)
                    .build();

            assertThrows(LifecycleException.class, thread1::start);
            assertFalse(thread1.isAlive());

            // The thread should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count1.get());

            threadManager.start();

            // Don't bother trying to start thread1,
            // attempting to start this thread in the wrong part of the lifecycle leaves it in a broken state
            thread2.start();

            assertEventuallyTrue(() -> count2.get() > 100, Duration.ofSeconds(1), "thread did not run");
        }
    }

    @Test
    @DisplayName("QueueThread Test")
    void queueThreadTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count1 = new AtomicLong();
            final AtomicLong count2 = new AtomicLong();

            final QueueThread<Integer> thread1 = threadManager
                    .newQueueThreadConfiguration(Integer.class)
                    .setHandler(count1::getAndAdd)
                    .build();

            final QueueThread<Integer> thread2 = threadManager
                    .newQueueThreadConfiguration(Integer.class)
                    .setHandler(count2::getAndAdd)
                    .build();

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                thread1.add(i);
                thread2.add(i);
            }

            assertThrows(LifecycleException.class, thread1::start);
            assertFalse(thread1.isAlive());

            // The thread should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count1.get());

            threadManager.start();

            // Don't bother trying to start thread1,
            // attempting to start this thread in the wrong part of the lifecycle leaves it in a broken state
            thread2.start();

            final int finalSum = sum;
            assertEventuallyTrue(() -> finalSum == count2.get(), Duration.ofSeconds(1), "thread did not properly run");

            thread2.stop();
        }
    }

    @Test
    @DisplayName("QueueThreadPool Test")
    void queueThreadPoolTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count1 = new AtomicLong();
            final AtomicLong count2 = new AtomicLong();

            final QueueThreadPool<Integer> thread1 = threadManager
                    .newQueueThreadPoolConfiguration(Integer.class)
                    .setHandler(count1::getAndAdd)
                    .build();

            final QueueThreadPool<Integer> thread2 = threadManager
                    .newQueueThreadPoolConfiguration(Integer.class)
                    .setHandler(count2::getAndAdd)
                    .build();

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                thread1.add(i);
                thread2.add(i);
            }

            assertThrows(LifecycleException.class, thread1::start);

            // The thread should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count1.get());

            threadManager.start();

            // Don't bother trying to start thread1,
            // attempting to start this thread in the wrong part of the lifecycle leaves it in a broken state
            thread2.start();

            final int finalSum = sum;
            assertEventuallyTrue(() -> finalSum == count2.get(), Duration.ofSeconds(1), "thread did not properly run");

            thread2.stop();
        }
    }

    @Test
    @DisplayName("MultiQueueThread Test")
    void multiQueueThreadTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count1 = new AtomicLong();
            final AtomicLong count2 = new AtomicLong();

            final MultiQueueThread thread1 = threadManager
                    .newMultiQueueThreadConfiguration()
                    .addHandler(Integer.class, x -> count1.getAndAdd(x))
                    .build();
            final BlockingQueueInserter<Integer> inserter1 = thread1.getInserter(Integer.class);

            final MultiQueueThread thread2 = threadManager
                    .newMultiQueueThreadConfiguration()
                    .addHandler(Integer.class, x -> count2.getAndAdd(x))
                    .build();
            final BlockingQueueInserter<Integer> inserter2 = thread2.getInserter(Integer.class);

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                inserter1.add(i);
                inserter2.add(i);
            }

            assertThrows(LifecycleException.class, thread1::start);
            assertFalse(thread1.isAlive());

            // The thread should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count1.get());

            threadManager.start();

            // Don't bother trying to start thread1,
            // attempting to start this thread in the wrong part of the lifecycle leaves it in a broken state
            thread2.start();

            final int finalSum = sum;
            assertEventuallyTrue(() -> finalSum == count2.get(), Duration.ofSeconds(1), "thread did not properly run");

            thread2.stop();
        }
    }

    @Test
    @DisplayName("Cached Thread Pool Test")
    void cachedThreadPoolTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();

            final ExecutorService executorService = threadManager.createCachedThreadPool("test");

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Cached Thread Pool Exception Test")
    void cachedThreadPoolExceptionTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();
            final AtomicBoolean exceptionObserved = new AtomicBoolean(false);

            final ExecutorService executorService =
                    threadManager.createCachedThreadPool("test", (t, e) -> exceptionObserved.set(true));

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
                if (i == 50) {
                    executorService.execute(() -> {
                        throw new RuntimeException("intentional");
                    });
                }
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
            assertTrue(exceptionObserved.get());

            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Single Thread Executor Test")
    void singleThreadExecutorTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();

            final ExecutorService executorService = threadManager.createSingleThreadExecutor("test");

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Single Thread Executor Exception Test")
    void singleThreadExecutorExceptionTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();
            final AtomicBoolean exceptionObserved = new AtomicBoolean(false);

            final ExecutorService executorService =
                    threadManager.createSingleThreadExecutor("test", (t, e) -> exceptionObserved.set(true));

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
                if (i == 50) {
                    executorService.execute(() -> {
                        throw new RuntimeException("intentional");
                    });
                }
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
            assertTrue(exceptionObserved.get());

            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Fixed Thread Pool Test")
    void fixedThreadPoolTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();

            final ExecutorService executorService = threadManager.createFixedThreadPool("test", 3);

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Fixed Thread Pool Exception Test")
    void fixedThreadPoolExceptionTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();
            final AtomicBoolean exceptionObserved = new AtomicBoolean(false);

            final ExecutorService executorService =
                    threadManager.createFixedThreadPool("test", 3, (t, e) -> exceptionObserved.set(true));

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
                if (i == 50) {
                    executorService.execute(() -> {
                        throw new RuntimeException("intentional");
                    });
                }
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
            assertTrue(exceptionObserved.get());

            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Single Thread Scheduled Executor Test")
    void singleThreadScheduledExecutorTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();

            final ScheduledExecutorService executorService = threadManager.createSingleThreadScheduledExecutor("test");

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("Single Thread Scheduled Executor Test")
    void scheduledThreadPoolTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {

            final AtomicLong count = new AtomicLong();

            final ScheduledExecutorService executorService = threadManager.createScheduledThreadPool("test", 3);

            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
                final int finalI = i;
                executorService.execute(() -> count.getAndAdd(finalI));
            }

            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());

            threadManager.start();

            final int finalSum = sum;
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

            executorService.shutdown();
        }
    }
}
