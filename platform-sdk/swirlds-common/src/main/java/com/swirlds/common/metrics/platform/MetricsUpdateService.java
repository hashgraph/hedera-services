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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.platform.DefaultMetrics.EXCEPTION_RATE_THRESHOLD;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.base.state.Startable;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.ThresholdLimitingHandler;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Maintains a list of updaters, that will be updated in regular intervals. The length of the interval is configurable.
 */
class MetricsUpdateService implements Startable {

    private static final Logger logger = LogManager.getLogger(MetricsUpdateService.class);

    private enum State {
        INIT,
        RUNNING,
        SHUTDOWN
    }

    private static final long NO_DELAY = 0L;

    private final ScheduledExecutorService executor;
    private final long period;
    private final TimeUnit unit;
    private final Queue<Runnable> updaters = new ConcurrentLinkedQueue<>();
    private final ThresholdLimitingHandler<Throwable> exceptionRateLimiter =
            new ThresholdLimitingHandler<>(EXCEPTION_RATE_THRESHOLD);
    private final AtomicReference<State> state;

    private ScheduledFuture<?> future;

    MetricsUpdateService(final ScheduledExecutorService executor, final long period, final TimeUnit unit) {
        this.executor = CommonUtils.throwArgNull(executor, "executor");
        this.period = period;
        this.unit = unit;
        state = new AtomicReference<>(State.INIT);
    }

    /**
     * Add an updater that will be called periodically. An updater should only be used to update metrics regularly.
     *
     * @param updater
     * 		the updater
     * @throws IllegalArgumentException
     * 		if {@code updater} is {@code null}
     */
    public void addUpdater(final Runnable updater) {
        CommonUtils.throwArgNull(updater, "updater");
        updaters.add(updater);
    }

    /**
     * Remove an updater that was previously added.
     *
     * @param updater
     * 		the updater
     * @throws IllegalArgumentException
     * 		if {@code updater} is {@code null}
     */
    public void removeUpdater(final Runnable updater) {
        CommonUtils.throwArgNull(updater, "updater");
        updaters.remove(updater);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (state.compareAndSet(State.INIT, State.RUNNING)) {
            future = executor.scheduleAtFixedRate(this::runUpdaters, NO_DELAY, period, unit);
        }
    }

    /**
     * Shuts down the service and waits if necessary at most 1 sec. to complete
     *
     * @return {@code true} if the shutdown finished on time, {@code false} if the call ran into a timeout
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    public boolean shutdown() throws InterruptedException {
        if (future != null && state.compareAndSet(State.RUNNING, State.SHUTDOWN)) {
            future.cancel(true);

            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (final TimeoutException e) {
                return false;
            } catch (final ExecutionException e) {
                // this should not happen
                logger.error("MetricsUpdateService.runUpdaters threw an unexpected exception", e);
            } catch (final CancellationException e) {
                // ignore, this is expected behavior
            }
        }
        return true;
    }

    private void runUpdaters() {
        for (final Runnable updater : updaters) {
            try {
                updater.run();
            } catch (final RuntimeException e) {
                exceptionRateLimiter.handle(
                        e, error -> logger.error(EXCEPTION.getMarker(), "Exception while updating metrics.", error));
            }
        }
    }
}
