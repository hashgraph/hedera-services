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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadPoolConfiguration;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.manager.StartableThreadManager;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.LifecyclePhase;
import com.swirlds.common.utility.Startable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A standard implementation of a {@link ThreadManager}. Will not permit work to be done on threads until started.
 */
public final class StandardThreadManager implements StartableThreadManager, ThreadBuilder {

    private static final Logger logger = LogManager.getLogger();

    private LifecyclePhase phase = LifecyclePhase.NOT_STARTED;
    private final List<Startable> thingsToBeStarted = new ArrayList<>();

    private final Runnable throwIfInWrongPhase = () -> this.throwIfNotInPhase(LifecyclePhase.STARTED);

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return phase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() {
        throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
        phase = LifecyclePhase.STARTED;
        thingsToBeStarted.forEach(Startable::start);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void stop() {
        throwIfNotInPhase(LifecyclePhase.STARTED);
        phase = LifecyclePhase.STOPPED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized @NonNull Thread buildThread(@NonNull final Runnable runnable) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedThread(runnable, throwIfInWrongPhase);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized ExecutorService createCachedThreadPool(@NonNull final String name) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        final ManagedExecutorService service =
                new ManagedExecutorService(Executors.newCachedThreadPool(buildThreadFactory(name)));
        if (phase == LifecyclePhase.NOT_STARTED) {
            thingsToBeStarted.add(service);
        }
        return service;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized ExecutorService createSingleThreadExecutor(@NonNull final String name) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        return new ManagedExecutorService(Executors.newSingleThreadExecutor(buildThreadFactory(name)));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized ExecutorService createFixedThreadPool(@NonNull final String name, int threadCount) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        final ManagedExecutorService service =
                new ManagedExecutorService(Executors.newFixedThreadPool(threadCount, buildThreadFactory(name)));
        if (phase == LifecyclePhase.NOT_STARTED) {
            thingsToBeStarted.add(service);
        }
        return service;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized ScheduledExecutorService createSingleThreadScheduledExecutor(final @NonNull String name) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        final ManagedScheduledExecutorService service = new ManagedScheduledExecutorService(
                Executors.newSingleThreadScheduledExecutor(buildThreadFactory(name)));
        if (phase == LifecyclePhase.NOT_STARTED) {
            thingsToBeStarted.add(service);
        }
        return service;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized ScheduledExecutorService createScheduledThreadPool(
            @NonNull final String name, int threadCount) {
        throwIfAfterPhase(LifecyclePhase.STARTED);
        final ManagedScheduledExecutorService service = new ManagedScheduledExecutorService(
                Executors.newScheduledThreadPool(threadCount, buildThreadFactory(name)));
        if (phase == LifecyclePhase.NOT_STARTED) {
            thingsToBeStarted.add(service);
        }
        return service;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized ThreadConfiguration newThreadConfiguration() {
        return new ThreadConfiguration(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized <T extends InterruptableRunnable>
            StoppableThreadConfiguration<T> newStoppableThreadConfiguration() {
        return new StoppableThreadConfiguration<>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized <T> QueueThreadConfiguration<T> newQueueThreadConfiguration() {
        return new QueueThreadConfiguration<T>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized <T> QueueThreadPoolConfiguration<T> newQueueThreadPoolConfiguration() {
        return new QueueThreadPoolConfiguration<T>(this);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized MultiQueueThreadConfiguration newMultiQueueThreadConfiguration() {
        return new MultiQueueThreadConfiguration(this);
    }

    // TODO this duplicates a lot of logic!

    /**
     * An default exception handler for executor services.
     */
    private static final Thread.UncaughtExceptionHandler exceptionHandler = (final Thread t, final Throwable e) ->
            logger.error(EXCEPTION.getMarker(), "exception on thread {}", t.getName(), e);

    /**
     * Builds a thread factory for an executor service.
     *
     * @param baseName the name of the executor service
     * @return a thread factory
     */
    private @NonNull ThreadFactory buildThreadFactory(@NonNull final String baseName) {
        final AtomicInteger threadNumber = new AtomicInteger(0);
        return (final Runnable runnable) -> {
            final String name = "<" + baseName + " #" + threadNumber.getAndIncrement() + ">";
            final Thread thread = new Thread(runnable, name);
            thread.setUncaughtExceptionHandler(exceptionHandler);
            return thread;
        };
    }
}
