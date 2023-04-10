/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.utility.LifecyclePhase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wraps an executor service. Used to manage lifecycle of the executor.
 */
public class ManagedScheduledExecutorService extends ManagedExecutorService implements ScheduledExecutorService {

    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * Wrap an executor service.
     *
     * @param executorService   the executor service to wrap
     */
    public ManagedScheduledExecutorService(@NonNull ScheduledExecutorService executorService) {
        super(executorService);
        this.scheduledExecutorService = executorService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ScheduledFuture<?> schedule(
            final @NonNull Runnable command, final long delay, final @NonNull TimeUnit unit) {
        if (getLifecyclePhase() == LifecyclePhase.NOT_STARTED) {
            return scheduledExecutorService.schedule(wrapRunnable(command), delay, unit);
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return scheduledExecutorService.schedule(command, delay, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull <V> ScheduledFuture<V> schedule(
            final @NonNull Callable<V> callable, final long delay, final @NonNull TimeUnit unit) {
        if (getLifecyclePhase() == LifecyclePhase.NOT_STARTED) {
            return scheduledExecutorService.schedule(wrapCallable(callable), delay, unit);
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return scheduledExecutorService.schedule(callable, delay, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ScheduledFuture<?> scheduleAtFixedRate(
            final @NonNull Runnable command, final long initialDelay, final long period, final @NonNull TimeUnit unit) {
        if (getLifecyclePhase() == LifecyclePhase.NOT_STARTED) {
            return scheduledExecutorService.scheduleAtFixedRate(wrapRunnable(command), initialDelay, period, unit);
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ScheduledFuture<?> scheduleWithFixedDelay(
            final @NonNull Runnable command, final long initialDelay, final long delay, final @NonNull TimeUnit unit) {
        if (getLifecyclePhase() == LifecyclePhase.NOT_STARTED) {
            return scheduledExecutorService.scheduleWithFixedDelay(wrapRunnable(command), initialDelay, delay, unit);
        }
        throwIfNotInPhase(LifecyclePhase.STARTED);
        return scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
}
