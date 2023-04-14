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
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.threading.manager.ExecutorServiceRegistry;
import com.swirlds.common.threading.manager.StartableThreadManager;
import com.swirlds.common.threading.manager.ThreadBuilder;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.LifecyclePhase;
import com.swirlds.common.utility.Startable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A standard implementation of a {@link ThreadManager}. Will not permit work to be done on threads until started.
 */
public final class StandardThreadManager extends AbstractThreadManager
        implements StartableThreadManager, ThreadBuilder, ExecutorServiceRegistry {

    private LifecyclePhase phase = LifecyclePhase.NOT_STARTED;
    private final List<Startable> thingsToBeStarted = new ArrayList<>();

    private final Runnable throwIfInWrongPhase = () -> this.throwIfNotInPhase(LifecyclePhase.STARTED);

    private final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull LifecyclePhase getLifecyclePhase() {
        return phase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        try (final Locked l = lock.lock()) {
            throwIfNotInPhase(LifecyclePhase.NOT_STARTED);
            phase = LifecyclePhase.STARTED;
            thingsToBeStarted.forEach(Startable::start);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        try (final Locked l = lock.lock()) {
            throwIfNotInPhase(LifecyclePhase.STARTED);
            phase = LifecyclePhase.STOPPED;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Thread buildThread(@NonNull final Runnable runnable) {
        try (final Locked l = lock.lock()) {
            throwIfAfterPhase(LifecyclePhase.STARTED);
            return new ManagedThread(runnable, throwIfInWrongPhase);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerExecutorService(@NonNull ManagedExecutorService executorService) {
        try (final Locked l = lock.lock()) {
            throwIfAfterPhase(LifecyclePhase.STARTED);

            if (phase == LifecyclePhase.NOT_STARTED) {
                thingsToBeStarted.add(executorService);
            } else {
                executorService.start();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration newExecutorServiceConfiguration(final @NonNull String name) {
        return new ExecutorServiceConfiguration(this, name);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createSingleThreadScheduledExecutor(final @NonNull String name) {
        Objects.requireNonNull(name);
        try (final Locked l = lock.lock()) {
            throwIfAfterPhase(LifecyclePhase.STARTED);
            final ManagedScheduledExecutorService service = new ManagedScheduledExecutorService(
                    Executors.newSingleThreadScheduledExecutor(buildThreadFactory(name, null)));
            if (phase == LifecyclePhase.NOT_STARTED) {
                thingsToBeStarted.add(service);
            }
            return service;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ScheduledExecutorService createScheduledThreadPool(@NonNull final String name, int threadCount) {
        Objects.requireNonNull(name);
        try (final Locked l = lock.lock()) {
            throwIfAfterPhase(LifecyclePhase.STARTED);
            final ManagedScheduledExecutorService service = new ManagedScheduledExecutorService(
                    Executors.newScheduledThreadPool(threadCount, buildThreadFactory(name, null)));
            if (phase == LifecyclePhase.NOT_STARTED) {
                thingsToBeStarted.add(service);
            }
            return service;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ThreadConfiguration newThreadConfiguration() {
        try (final Locked l = lock.lock()) {
            return new ThreadConfiguration(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T extends InterruptableRunnable> StoppableThreadConfiguration<T> newStoppableThreadConfiguration() {
        try (final Locked l = lock.lock()) {
            return new StoppableThreadConfiguration<>(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T> QueueThreadConfiguration<T> newQueueThreadConfiguration() {
        try (final Locked l = lock.lock()) {
            return new QueueThreadConfiguration<>(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <T> QueueThreadPoolConfiguration<T> newQueueThreadPoolConfiguration() {
        try (final Locked l = lock.lock()) {
            return new QueueThreadPoolConfiguration<>(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MultiQueueThreadConfiguration newMultiQueueThreadConfiguration() {
        try (final Locked l = lock.lock()) {
            return new MultiQueueThreadConfiguration(this);
        }
    }
}
