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

package com.swirlds.common.concurrent.internal;

import com.swirlds.common.concurrent.ExecutorFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

public class DefaultExecutorFactory implements ExecutorFactory {

    private final ThreadFactory executorThreadFactory;

    private final ThreadFactory scheduledExecutorThreadFactory;

    private final ThreadFactory singleThreadFactory;

    private final ForkJoinWorkerThreadFactory forkJoinWorkerThreadFactory;

    private final UncaughtExceptionHandler handler;

    public DefaultExecutorFactory(
            @NonNull final ThreadFactory singleThreadFactory,
            @NonNull final ThreadFactory executorThreadFactory,
            @NonNull final ThreadFactory scheduledExecutorThreadFactory,
            @NonNull final ForkJoinWorkerThreadFactory forkJoinWorkerThreadFactory,
            @NonNull final UncaughtExceptionHandler handler) {
        this.singleThreadFactory = Objects.requireNonNull(singleThreadFactory, "singleThreadFactory must not be null");
        this.executorThreadFactory =
                Objects.requireNonNull(executorThreadFactory, "executorThreadFactory must not be null");
        this.scheduledExecutorThreadFactory = Objects.requireNonNull(
                scheduledExecutorThreadFactory, "scheduledExecutorThreadFactory must not be null");
        this.forkJoinWorkerThreadFactory =
                Objects.requireNonNull(forkJoinWorkerThreadFactory, "forkJoinWorkerThreadFactory must not be null");
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
    }

    @Override
    public ForkJoinPool createForkJoinPool(int parallelism) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be greater than 0");
        }
        return new ForkJoinPool(parallelism, forkJoinWorkerThreadFactory, handler, false);
    }

    @Override
    public ExecutorService createExecutorService(int threadCount) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be greater than 0");
        }
        return Executors.newFixedThreadPool(threadCount, executorThreadFactory);
    }

    @Override
    public ScheduledExecutorService createScheduledExecutorService(int threadCount) {
        return Executors.newScheduledThreadPool(threadCount, scheduledExecutorThreadFactory);
    }

    @NonNull
    @Override
    public Thread createThread(@NonNull Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return singleThreadFactory.newThread(runnable);
    }

    @NonNull
    public static DefaultExecutorFactory create(
            @NonNull final String groupName,
            @Nullable final Runnable onStartup,
            @NonNull final UncaughtExceptionHandler exceptionHandler) {
        Objects.requireNonNull(groupName, "groupName must not be null");
        final ThreadGroup group = new ThreadGroup(groupName);

        final Supplier<String> singleThreadNameFactory = DefaultThreadFactory.createThreadNameFactory("SingleThread");
        final ThreadFactory singleThreadFactory =
                new DefaultThreadFactory(group, singleThreadNameFactory, exceptionHandler, onStartup);

        final Supplier<String> executorThreadNameFactory =
                DefaultThreadFactory.createThreadNameFactory(groupName + "PoolThread");
        final ThreadFactory executorThreadFactory =
                new DefaultThreadFactory(group, executorThreadNameFactory, exceptionHandler, onStartup);

        final Supplier<String> scheduledExecutorThreadNameFactory =
                DefaultThreadFactory.createThreadNameFactory(groupName + "ScheduledPoolThread");
        final ThreadFactory scheduledExecutorThreadFactory =
                new DefaultThreadFactory(group, scheduledExecutorThreadNameFactory, exceptionHandler, onStartup);

        final Supplier<String> forkJoinThreadNameFactory =
                DefaultForkJoinWorkerThreadFactory.createThreadNameFactory(groupName + "ForkJoinThread");
        final ForkJoinWorkerThreadFactory forkJoinThreadFactory =
                new DefaultForkJoinWorkerThreadFactory(group, forkJoinThreadNameFactory, onStartup);
        return new DefaultExecutorFactory(
                singleThreadFactory,
                executorThreadFactory,
                scheduledExecutorThreadFactory,
                forkJoinThreadFactory,
                exceptionHandler);
    }
}
