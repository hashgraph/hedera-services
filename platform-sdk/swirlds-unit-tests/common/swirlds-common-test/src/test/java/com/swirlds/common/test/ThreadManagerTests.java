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
import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.ExecutorServiceProfile;
import com.swirlds.common.threading.manager.StartableThreadManager;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.manager.ThreadManagerFactory;
import com.swirlds.common.utility.LifecycleException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The purpose of this suite of classes is to make sure that all APIs on
 * {@link com.swirlds.common.threading.manager.ThreadManager} return threads or executor services that can perform work
 * with appropriate lifecycles. It does not attempt to deeply probe the behavior of the threads or executor services
 * (this is validated in other tests).
 */
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

    /**
     * Basic sanity check on an executor service with a lifecycle.
     *
     * @param threadManager            the thread manager to use
     * @param buildExecutor            A function that builds an executor service.
     * @param supportsExceptionHandler Whether the executor service supports an uncaught exception handler.
     */
    private static void testExecutorService(
            @NonNull final ThreadManager threadManager,
            @NonNull final BiFunction<ThreadManager, UncaughtExceptionHandler, ExecutorService> buildExecutor,
            final boolean supportsExceptionHandler)
            throws InterruptedException {

        final AtomicLong count = new AtomicLong();
        final AtomicBoolean exceptionObserved = new AtomicBoolean(false);

        final ExecutorService executorService =
                buildExecutor.apply(threadManager, (t, e) -> exceptionObserved.set(true));

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            final int finalI = i;
            executorService.execute(() -> count.getAndAdd(finalI));
            if (supportsExceptionHandler && i == 50) {
                executorService.execute(() -> {
                    throw new RuntimeException("intentional");
                });
            }
        }
        final int finalSum = sum;

        if (threadManager instanceof StartableThreadManager) {
            // The executor service should not be running in the background.
            // But pause briefly to allow it to do bad things if it wants to do bad things.
            MILLISECONDS.sleep(10);

            assertEquals(0, count.get());
        } else {
            assertEventuallyTrue(
                    () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
        }

        if (threadManager instanceof final StartableThreadManager startableThreadManager) {
            startableThreadManager.start();
        }

        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
        if (supportsExceptionHandler) {
            assertEventuallyTrue(exceptionObserved::get, Duration.ofSeconds(1), "exception handler was not called");
        }

        executorService.shutdown();
    }

    @Test
    @DisplayName("Cached Thread Pool Test")
    void cachedThreadPoolTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {
            testExecutorService(
                    threadManager,
                    (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                            manager.newExecutorServiceConfiguration("test")
                                    .setProfile(ExecutorServiceProfile.CACHED_THREAD_POOL)
                                    .setUncaughtExceptionHandler(handler)
                                    .build(),
                    true);
        }
    }

    @Test
    @DisplayName("Single Thread Executor Test")
    void singleThreadExecutorTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {
            testExecutorService(
                    threadManager,
                    (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                            manager.newExecutorServiceConfiguration("test")
                                    .setProfile(ExecutorServiceProfile.SINGLE_THREAD_EXECUTOR)
                                    .setUncaughtExceptionHandler(handler)
                                    .build(),
                    true);
        }
    }

    @Test
    @DisplayName("Fixed Thread Pool Test")
    void fixedThreadPoolTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {
            testExecutorService(
                    threadManager,
                    (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                            manager.newExecutorServiceConfiguration("test")
                                    .setProfile(ExecutorServiceProfile.FIXED_THREAD_POOL)
                                    .setCorePoolSize(3)
                                    .setUncaughtExceptionHandler(handler)
                                    .build(),
                    true);
        }
    }

    @Test
    @DisplayName("Single Thread Scheduled Executor Test")
    void singleThreadScheduledExecutorTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {
            testExecutorService(
                    threadManager,
                    (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                            manager.newScheduledExecutorServiceConfiguration("test")
                                    .build(),
                    false);
        }
    }

    @Test
    @DisplayName("Scheduled Thread Pool Test")
    void scheduledThreadPoolTest() throws InterruptedException {
        try (final StartableThreadManager threadManager = ThreadManagerFactory.buildThreadManager()) {
            testExecutorService(
                    threadManager,
                    (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                            manager.newScheduledExecutorServiceConfiguration("test")
                                    .setCorePoolSize(3)
                                    .build(),
                    false);
        }
    }

    @Test
    @DisplayName("Ad Hoc Cached Thread Pool Test")
    void adHocCachedThreadPoolTest() throws InterruptedException {
        testExecutorService(
                getStaticThreadManager(),
                (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                        manager.newExecutorServiceConfiguration("test")
                                .setProfile(ExecutorServiceProfile.CACHED_THREAD_POOL)
                                .setUncaughtExceptionHandler(handler)
                                .build(),
                true);
    }

    @Test
    @DisplayName("Ad Hoc Single Thread Executor Test")
    void adHocSingleThreadExecutorTest() throws InterruptedException {
        testExecutorService(
                getStaticThreadManager(),
                (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                        manager.newExecutorServiceConfiguration("test")
                                .setProfile(ExecutorServiceProfile.SINGLE_THREAD_EXECUTOR)
                                .setUncaughtExceptionHandler(handler)
                                .build(),
                true);
    }

    @Test
    @DisplayName("Ad Hoc Fixed Thread Pool Test")
    void adHocFixedThreadPoolTest() throws InterruptedException {
        testExecutorService(
                getStaticThreadManager(),
                (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                        manager.newExecutorServiceConfiguration("test")
                                .setProfile(ExecutorServiceProfile.FIXED_THREAD_POOL)
                                .setCorePoolSize(3)
                                .setUncaughtExceptionHandler(handler)
                                .build(),
                true);
    }

    @Test
    @DisplayName("Ad Hoc Single Thread Scheduled Executor Test")
    void adHocSingleThreadScheduledExecutorTest() throws InterruptedException {
        testExecutorService(
                getStaticThreadManager(),
                (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                        manager.newScheduledExecutorServiceConfiguration("test").build(),
                false);
    }

    @Test
    @DisplayName("Ad Hoc Scheduled Thread Pool Test")
    void adHocScheduledThreadPoolTest() throws InterruptedException {
        testExecutorService(
                getStaticThreadManager(),
                (final ThreadManager manager, final UncaughtExceptionHandler handler) ->
                        manager.newScheduledExecutorServiceConfiguration("test")
                                .setCorePoolSize(3)
                                .build(),
                false);
    }
}
