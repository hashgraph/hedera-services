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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.manager.StartableThreadManager;
import com.swirlds.common.threading.manager.ThreadManagerFactory;
import com.swirlds.common.utility.LifecycleException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ThreadManager Tests")
class ThreadManagerTests {

    // TODO
    //    ExecutorService createCachedThreadPool(@NonNull final String name);
    //    ExecutorService createSingleThreadExecutor(@NonNull final String name);
    //    ExecutorService createFixedThreadPool(@NonNull final String name, final int threadCount);
    //    ScheduledExecutorService createSingleThreadScheduledExecutor(@NonNull final String name);
    //    ScheduledExecutorService createScheduledThreadPool(@NonNull final String name, final int threadCount);
    //    <T> QueueThreadConfiguration<T> newQueueThreadConfiguration();
    //    <T> QueueThreadPoolConfiguration<T> newQueueThreadPoolConfiguration();
    //    MultiQueueThreadConfiguration newMultiQueueThreadConfiguration();

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

            AssertionUtils.assertEventuallyTrue(() -> count2.get() > 100, Duration.ofSeconds(1), "thread did not run");
        }
    }
}
