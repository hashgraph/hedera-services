/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.threading.manager.internal.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Thread Pool Tests")
class ThreadPoolTests {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @DisplayName("Basic Threading Test")
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void basicThreadingTest() {

        final AtomicInteger data = new AtomicInteger(0);
        final StandardWorkGroup threadPool = new StandardWorkGroup(getStaticThreadManager(), "test-pool", null);

        threadPool.execute("first", () -> {
            try {
                Thread.sleep(300);
                threadPool.execute("inner", () -> {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    data.getAndIncrement();
                });

                Thread.sleep(500);
                data.getAndIncrement();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        threadPool.execute("second", () -> {
            try {
                Thread.sleep(100);
                data.getAndIncrement();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        threadPool.execute("third", () -> {
            try {
                Thread.sleep(50);
                data.getAndIncrement();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertEquals(0, data.get(), "data does not match what was expected");
        assertDoesNotThrow(threadPool::waitForTermination, "no exceptions expected");
        assertEquals(4, data.get(), "data does not match what was expected");
    }

    @ParameterizedTest
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.THREADING)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Ensure threads are properly disposed")
    @ValueSource(ints = {100})
    void ensureProperThreadDisposal(int count) throws InterruptedException {

        for (int i = 0; i < count; i++) {
            basicThreadingTest();
            Thread.sleep(10);
            System.gc();
        }

        System.gc();
        Thread.sleep(1000);
        System.gc();
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.THREADING)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Catch exception on thread")
    void catchExceptionOnThread() {

        final AtomicInteger data = new AtomicInteger(0);
        final AtomicBoolean aborted = new AtomicBoolean(false);

        final StandardWorkGroup threadPool =
                new StandardWorkGroup(getStaticThreadManager(), "test-pool", () -> aborted.set(true));

        threadPool.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("interrupted");
            }
            System.out.println("throw exception");
            throw new RuntimeException("thread is going down");
        });

        threadPool.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                data.getAndIncrement();
            }
        });

        assertDoesNotThrow(threadPool::waitForTermination, "no exceptions expected");
        assertTrue(threadPool.isShutdown(), "pool should be shut down");
        assertTrue(threadPool.isTerminated(), "pool should be terminated");
        assertTrue(threadPool.hasExceptions(), "pool should have exceptions");
        assertTrue(aborted.get(), "abort method should have been called");
    }
}
