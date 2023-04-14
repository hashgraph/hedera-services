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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.QueueThreadPool;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.ExecutorServiceProfile;
import com.swirlds.common.threading.manager.ThreadManager;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdhocThreadManager Tests")
class AdHocThreadManagerTests {

    @Test
    @DisplayName("Thread Test")
    void threadTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicBoolean didThreadRun = new AtomicBoolean(false);

        final Thread thread = threadManager
                .newThreadConfiguration()
                .setRunnable(() -> didThreadRun.set(true))
                .build();

        thread.start();

        AssertionUtils.assertEventuallyEquals(true, didThreadRun::get, Duration.ofSeconds(1), "thread did not run");
    }

    @Test
    @DisplayName("StoppableThread Test")
    void stoppableThreadTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final StoppableThread thread = threadManager
                .newStoppableThreadConfiguration()
                .setWork(count::getAndIncrement)
                .build();

        thread.start();

        assertEventuallyTrue(() -> count.get() > 100, Duration.ofSeconds(1), "thread did not run");
    }

    @Test
    @DisplayName("QueueThread Test")
    void queueThreadTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final QueueThread<Integer> thread = threadManager
                .newQueueThreadConfiguration(Integer.class)
                .setHandler(count::getAndAdd)
                .build();

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            thread.add(i);
        }

        thread.start();

        final int finalSum = sum;
        assertEventuallyTrue(() -> finalSum == count.get(), Duration.ofSeconds(1), "thread did not properly run");

        thread.stop();
    }

    @Test
    @DisplayName("QueueThreadPool Test")
    void queueThreadPoolTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final QueueThreadPool<Integer> thread = threadManager
                .newQueueThreadPoolConfiguration(Integer.class)
                .setHandler(count::getAndAdd)
                .build();

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            thread.add(i);
        }

        thread.start();

        final int finalSum = sum;
        assertEventuallyTrue(() -> finalSum == count.get(), Duration.ofSeconds(1), "thread did not properly run");

        thread.stop();
    }

    @Test
    @DisplayName("MultiQueueThread Test")
    void multiQueueThreadTest() throws InterruptedException {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final MultiQueueThread thread = threadManager
                .newMultiQueueThreadConfiguration()
                .addHandler(Integer.class, x -> count.getAndAdd(x))
                .build();
        final BlockingQueueInserter<Integer> inserter2 = thread.getInserter(Integer.class);

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            inserter2.add(i);
        }

        thread.start();

        final int finalSum = sum;
        assertEventuallyTrue(() -> finalSum == count.get(), Duration.ofSeconds(1), "thread did not properly run");

        thread.stop();
    }

    @Test
    @DisplayName("Cached Thread Pool Test")
    void cachedThreadPoolTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final ExecutorService executorService = threadManager
                .newExecutorServiceConfiguration("test")
                .setProfile(ExecutorServiceProfile.CACHED_THREAD_POOL)
                .build();

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            final int finalI = i;
            executorService.execute(() -> count.getAndAdd(finalI));
        }

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

        executorService.shutdown();
    }

    @Test
    @DisplayName("Cached Thread Pool Exception Test")
    void cachedThreadPoolExceptionTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();
        final AtomicBoolean exceptionObserved = new AtomicBoolean(false);

        final ExecutorService executorService = threadManager
                .newExecutorServiceConfiguration("test")
                .setUncaughtExceptionHandler((t, e) -> exceptionObserved.set(true))
                .setProfile(ExecutorServiceProfile.CACHED_THREAD_POOL)
                .build();

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

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
        assertTrue(exceptionObserved.get());

        executorService.shutdown();
    }

    @Test
    @DisplayName("Single Thread Executor Test")
    void singleThreadExecutorTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final ExecutorService executorService = threadManager
                .newExecutorServiceConfiguration("test")
                .setProfile(ExecutorServiceProfile.SINGLE_THREAD_EXECUTOR)
                .build();

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            final int finalI = i;
            executorService.execute(() -> count.getAndAdd(finalI));
        }

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

        executorService.shutdown();
    }

    @Test
    @DisplayName("Single Thread Executor Exception Test")
    void singleThreadExecutorExceptionTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();
        final AtomicBoolean exceptionObserved = new AtomicBoolean(false);

        final ExecutorService executorService = threadManager
                .newExecutorServiceConfiguration("test")
                .setProfile(ExecutorServiceProfile.SINGLE_THREAD_EXECUTOR)
                .setUncaughtExceptionHandler((t, e) -> exceptionObserved.set(true))
                .build();

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

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
        assertTrue(exceptionObserved.get());

        executorService.shutdown();
    }

    @Test
    @DisplayName("Fixed Thread Pool Test")
    void fixedThreadPoolTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final ExecutorService executorService = threadManager.createFixedThreadPool("test", 3);

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            final int finalI = i;
            executorService.execute(() -> count.getAndAdd(finalI));
        }

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

        executorService.shutdown();
    }

    @Test
    @DisplayName("Fixed Thread Pool Exception Test")
    void fixedThreadPoolExceptionTest() {
        final ThreadManager threadManager = getStaticThreadManager();

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

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");
        assertTrue(exceptionObserved.get());

        executorService.shutdown();
    }

    @Test
    @DisplayName("Single Thread Scheduled Executor Test")
    void singleThreadScheduledExecutorTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final ScheduledExecutorService executorService = threadManager.createSingleThreadScheduledExecutor("test");

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            final int finalI = i;
            executorService.execute(() -> count.getAndAdd(finalI));
        }

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

        executorService.shutdown();
    }

    @Test
    @DisplayName("Single Thread Scheduled Executor Test")
    void scheduledThreadPoolTest() {
        final ThreadManager threadManager = getStaticThreadManager();

        final AtomicLong count = new AtomicLong();

        final ScheduledExecutorService executorService = threadManager.createScheduledThreadPool("test", 3);

        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i;
            final int finalI = i;
            executorService.execute(() -> count.getAndAdd(finalI));
        }

        final int finalSum = sum;
        assertEventuallyTrue(
                () -> finalSum == count.get(), Duration.ofSeconds(1), "executor service did not properly run");

        executorService.shutdown();
    }
}
