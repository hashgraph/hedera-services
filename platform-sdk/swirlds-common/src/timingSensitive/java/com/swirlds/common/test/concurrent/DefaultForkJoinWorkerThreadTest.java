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

import com.swirlds.common.concurrent.internal.DefaultForkJoinWorkerThread;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultForkJoinWorkerThreadTest {

    @Test
    void testInstantiationWithNullName() {
        // given
        final String name = null;
        final ThreadGroup group = new ThreadGroup("test-group");
        final ForkJoinPool pool = new ForkJoinPool();
        final boolean preserveThreadLocals = true;
        final Runnable onStartup = () -> System.out.println("test");

        // then
        Assertions.assertThatThrownBy(
                        () -> new DefaultForkJoinWorkerThread(name, group, pool, preserveThreadLocals, onStartup))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInstantiationWithNullGroup() {
        // given
        final String name = "test";
        final ThreadGroup group = null;
        final ForkJoinPool pool = new ForkJoinPool();
        final boolean preserveThreadLocals = true;
        final Runnable onStartup = () -> System.out.println("test");

        // then
        Assertions.assertThatThrownBy(
                        () -> new DefaultForkJoinWorkerThread(name, group, pool, preserveThreadLocals, onStartup))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInstantiationWithNullPool() {
        // given
        final String name = "test";
        final ThreadGroup group = new ThreadGroup("test-group");
        final ForkJoinPool pool = null;
        final boolean preserveThreadLocals = true;
        final Runnable onStartup = () -> System.out.println("test");

        // then
        Assertions.assertThatThrownBy(
                        () -> new DefaultForkJoinWorkerThread(name, group, pool, preserveThreadLocals, onStartup))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInstantiationWithNullRunnable() {
        // given
        final String name = "test";
        final ThreadGroup group = new ThreadGroup("test-group");
        final ForkJoinPool pool = new ForkJoinPool();
        final boolean preserveThreadLocals = true;
        final Runnable onStartup = () -> System.out.println("test");

        // then
        Assertions.assertThatNoException()
                .isThrownBy(() -> new DefaultForkJoinWorkerThread(name, group, pool, preserveThreadLocals, onStartup));
    }

    @Test
    void testCorrectInternalThreadConfiguration() {
        // given
        final AtomicReference<String> threadNameHolder = new AtomicReference<>();
        final AtomicReference<ThreadGroup> threadGroupHolder = new AtomicReference<>();
        final String name = "test";
        final ThreadGroup group = new ThreadGroup("test-group");
        final ForkJoinWorkerThreadFactory factory =
                pool -> new DefaultForkJoinWorkerThread(name, group, pool, true, null);
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
        Assertions.assertThat(threadNameHolder.get()).isEqualTo(name);
        Assertions.assertThat(threadGroupHolder.get()).isEqualTo(group);
    }

    @Test
    void testStartupCall() {
        // given
        final AtomicBoolean startupCallHolder = new AtomicBoolean(false);
        final Runnable onStartup = () -> startupCallHolder.set(true);
        final ForkJoinWorkerThreadFactory factory =
                pool -> new DefaultForkJoinWorkerThread("test", new ThreadGroup("test-group"), pool, true, onStartup);
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
        final Runnable onStartup = () -> startupCallHolder.incrementAndGet();
        final ForkJoinWorkerThreadFactory factory =
                pool -> new DefaultForkJoinWorkerThread("test", new ThreadGroup("test-group"), pool, true, onStartup);
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
