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

import static com.google.common.truth.Truth.assertThat;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StandardWorkGroupTest {
    StandardWorkGroup subject;
    AtomicInteger abortCount;

    @BeforeEach
    void setUp() {
        abortCount = new AtomicInteger();
        subject = new StandardWorkGroup(getStaticThreadManager(), "groupName", abortCount::incrementAndGet);
    }

    @AfterEach
    void tearDown() {
        if (!subject.isShutdown()) {
            subject.shutdown();
        }
    }

    @Test
    void initialStateValid() {
        assertThat(subject.hasExceptions()).isFalse();
        assertThat(subject.isShutdown()).isFalse();
        assertThat(subject.isTerminated()).isFalse();
        assertThat(abortCount.get()).isEqualTo(0);
    }

    @Test
    void executesTasks() throws Exception {
        final AtomicInteger executedCount = new AtomicInteger();
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(2);

        subject.execute(() -> {
            executedCount.incrementAndGet();
            try {
                cyclicBarrier.await();
            } catch (final Exception ignored) {
            }
        });
        cyclicBarrier.await(10, TimeUnit.SECONDS);
        assertThat(executedCount.get()).isEqualTo(1);

        // Verify subject's state
        assertThat(subject.hasExceptions()).isFalse();
        assertThat(subject.isTerminated()).isFalse();
        assertThat(subject.isShutdown()).isFalse();
        assertThat(abortCount.get()).isEqualTo(0);
    }

    @Test
    void executesMultipleTasksNoExceptions() throws InterruptedException {
        final AtomicInteger executedCount = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            subject.execute(executedCount::incrementAndGet);
        }
        subject.waitForTermination();
        assertThat(executedCount.get()).isEqualTo(10);

        // Verify subject's state
        assertThat(subject.hasExceptions()).isFalse();
        assertThat(subject.isTerminated()).isTrue();
        assertThat(subject.isShutdown()).isTrue();
        assertThat(abortCount.get()).isEqualTo(0);
    }

    @Test
    void executesMultipleTasksWithException() throws InterruptedException {
        final AtomicInteger executedCount = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            subject.execute(executedCount::incrementAndGet);
        }
        subject.execute(() -> {
            throw new AssertionError();
        });
        subject.waitForTermination();
        assertThat(executedCount.get()).isEqualTo(10);

        // Verify subject's state
        assertThat(subject.hasExceptions()).isTrue();
        assertThat(subject.isTerminated()).isTrue();
        assertThat(subject.isShutdown()).isTrue();
        assertThat(abortCount.get()).isEqualTo(1);
    }

    @Test
    void awaitTerminationWhenNoTasks() throws InterruptedException {
        subject.waitForTermination();
        // Verify subject's state
        assertThat(subject.hasExceptions()).isFalse();
        assertThat(subject.isTerminated()).isTrue();
        assertThat(subject.isShutdown()).isTrue();
        assertThat(abortCount.get()).isEqualTo(0);
    }

    @Test
    void executingTasksAfterShutdown() throws InterruptedException {
        subject.shutdown();
        AtomicInteger executedCount = new AtomicInteger();
        try {
            subject.execute(executedCount::incrementAndGet);
            fail("Failed to throw exception");
        } catch (final RejectedExecutionException e) {
            // Exception expected.
        }
        subject.waitForTermination();
        assertThat(executedCount.get()).isEqualTo(0);
        assertThat(subject.hasExceptions()).isFalse();
        assertThat(subject.isTerminated()).isTrue();
        assertThat(subject.isShutdown()).isTrue();
        assertThat(abortCount.get()).isEqualTo(0);
    }

    @Test
    void executeTasksWithName() throws InterruptedException {
        final AtomicInteger executedCount = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            subject.execute("task-" + i, executedCount::incrementAndGet);
        }
        subject.waitForTermination();
        assertThat(executedCount.get()).isEqualTo(10);

        // Verify subject's state
        assertThat(subject.hasExceptions()).isFalse();
        assertThat(subject.isTerminated()).isTrue();
        assertThat(subject.isShutdown()).isTrue();
        assertThat(abortCount.get()).isEqualTo(0);
    }
}
