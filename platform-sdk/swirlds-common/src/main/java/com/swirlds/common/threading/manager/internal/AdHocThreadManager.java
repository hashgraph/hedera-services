/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.manager.internal;

import com.swirlds.common.threading.framework.config.ExecutorServiceConfiguration;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadPoolConfiguration;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.manager.ExecutorServiceRegistry;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A simple thread manager. The goal of this implementation is to create threads without complaining about lifecycle.
 * Eventually, this implementation should not be used in production code.
 */
public final class AdHocThreadManager extends AbstractThreadManager
        implements ExecutorServiceRegistry, ThreadBuilder, ThreadManager {

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Thread buildThread(@NonNull final Runnable runnable) {
        return new Thread(runnable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerExecutorService(final @NonNull ManagedExecutorService executorService) {
        executorService.start();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration newExecutorServiceConfiguration(@NonNull String name) {
        return new ExecutorServiceConfiguration(this, name);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createSingleThreadExecutor(
            @NonNull final String name, @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(uncaughtExceptionHandler);
        return Executors.newSingleThreadExecutor(buildThreadFactory(name, uncaughtExceptionHandler));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorService createFixedThreadPool(
            @NonNull final String name,
            final int threadCount,
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(uncaughtExceptionHandler);
        return Executors.newFixedThreadPool(threadCount, buildThreadFactory(name, uncaughtExceptionHandler));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createSingleThreadScheduledExecutor(@NonNull String name) {
        Objects.requireNonNull(name);
        return Executors.newSingleThreadScheduledExecutor(buildThreadFactory(name, null));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createScheduledThreadPool(@NonNull final String name, final int threadCount) {
        Objects.requireNonNull(name);
        return Executors.newScheduledThreadPool(threadCount, buildThreadFactory(name, null));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ThreadConfiguration newThreadConfiguration() {
        return new ThreadConfiguration(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T extends InterruptableRunnable> StoppableThreadConfiguration<T> newStoppableThreadConfiguration() {
        return new StoppableThreadConfiguration<>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T> QueueThreadConfiguration<T> newQueueThreadConfiguration() {
        return new QueueThreadConfiguration<>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T> QueueThreadPoolConfiguration<T> newQueueThreadPoolConfiguration() {
        return new QueueThreadPoolConfiguration<>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MultiQueueThreadConfiguration newMultiQueueThreadConfiguration() {
        return new MultiQueueThreadConfiguration(this);
    }
}
