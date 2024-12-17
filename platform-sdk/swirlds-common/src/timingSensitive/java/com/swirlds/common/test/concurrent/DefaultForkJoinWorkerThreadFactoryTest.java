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

package com.swirlds.common.test.concurrent;

import com.swirlds.common.concurrent.internal.DefaultForkJoinWorkerThreadFactory;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultForkJoinWorkerThreadFactoryTest {

    @Test
    void testInstantiation() {
        // given
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = () -> "test";
        final Runnable onStartup = () -> System.out.println("test");

        // then
        Assertions.assertThatNoException()
                .isThrownBy(() -> new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup));
    }

    @Test
    void testInstantiationWithNullThreadGroup() {
        // given
        final ThreadGroup threadGroup = null;
        final Supplier<String> threadNameFactory = () -> "test";
        final Runnable onStartup = () -> System.out.println("test");

        // then
        Assertions.assertThatThrownBy(
                        () -> new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInstantiationWithNullThreadNameFactory() {
        // given
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = null;
        final Runnable onStartup = () -> System.out.println("test");

        // then
        Assertions.assertThatThrownBy(
                        () -> new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInstantiationWithNullStartup() {
        // given
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = () -> "test";
        final Runnable onStartup = null;

        // then
        Assertions.assertThatNoException()
                .isThrownBy(() -> new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup));
    }

    @Test
    void testThreadCreationWithNullPool() {
        // given
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = () -> "test-Name-For-Test";
        final Runnable onStartup = null;
        final ForkJoinWorkerThreadFactory factory =
                new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup);
        final ForkJoinPool pool = null;

        // then
        Assertions.assertThatThrownBy(() -> factory.newThread(pool)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testThreadCreation() {
        // given
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = () -> "test-Name-For-Test";
        final Runnable onStartup = null;
        final ForkJoinWorkerThreadFactory factory =
                new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup);
        final ForkJoinPool pool = new ForkJoinPool();

        // then
        final ForkJoinWorkerThread thread = factory.newThread(pool);

        Assertions.assertThat(thread).isNotNull();
        Assertions.assertThat(thread.getThreadGroup()).isEqualTo(threadGroup);
        Assertions.assertThat(thread.getName()).contains("test-Name-For-Test");
    }

    @Test
    void testCorrectInternalThreadConfiguration() {
        // given
        final AtomicReference<String> threadNameHolder = new AtomicReference<>();
        final AtomicReference<ThreadGroup> threadGroupHolder = new AtomicReference<>();
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = () -> "test-Name-For-Test";
        final Runnable onStartup = null;
        final ForkJoinWorkerThreadFactory factory =
                new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup);
        final UncaughtExceptionHandler handler = (t, e) -> System.out.println("error: " + e.getMessage());
        final ForkJoinPool pool = new ForkJoinPool(1, factory, handler, true);

        // when
        final ForkJoinTask<?> task = pool.submit(() -> {
            threadNameHolder.set(Thread.currentThread().getName());
            threadGroupHolder.set(Thread.currentThread().getThreadGroup());
        });
        try {
            task.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Assertions.fail("task failed", e);
        }

        // then
        Assertions.assertThat(threadNameHolder.get()).contains("test-Name-For-Test");
        Assertions.assertThat(threadGroupHolder.get()).isEqualTo(threadGroup);
    }

    @Test
    void testStartupCall() {
        // given
        final AtomicBoolean startupCallHolder = new AtomicBoolean(false);
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = () -> "test-Name-For-Test";
        final Runnable onStartup = () -> startupCallHolder.set(true);
        final ForkJoinWorkerThreadFactory factory =
                new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup);
        final UncaughtExceptionHandler handler = (t, e) -> System.out.println("error: " + e.getMessage());
        final ForkJoinPool pool = new ForkJoinPool(1, factory, handler, true);

        // when
        final ForkJoinTask<?> task = pool.submit(() -> System.out.println("test"));
        try {
            task.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Assertions.fail("task failed", e);
        }

        // then
        Assertions.assertThat(startupCallHolder.get()).isTrue();
    }

    @Test
    void testStartupCalledOnlyOnce() {
        // given
        final AtomicInteger startupCallHolder = new AtomicInteger(0);
        final ThreadGroup threadGroup = new ThreadGroup("test-group");
        final Supplier<String> threadNameFactory = () -> "test-Name-For-Test";
        final Runnable onStartup = () -> startupCallHolder.incrementAndGet();
        final ForkJoinWorkerThreadFactory factory =
                new DefaultForkJoinWorkerThreadFactory(threadGroup, threadNameFactory, onStartup);
        final UncaughtExceptionHandler handler = (t, e) -> System.out.println("error: " + e.getMessage());
        final ForkJoinPool pool = new ForkJoinPool(1, factory, handler, true);

        // when
        final ForkJoinTask<?> task = pool.submit(() -> System.out.println("test"));
        try {
            task.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Assertions.fail("task failed", e);
        }
        final ForkJoinTask<?> task2 = pool.submit(() -> System.out.println("test"));
        try {
            task2.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Assertions.fail("task failed", e);
        }

        // then
        Assertions.assertThat(startupCallHolder.get()).isEqualTo(1);
    }
}
