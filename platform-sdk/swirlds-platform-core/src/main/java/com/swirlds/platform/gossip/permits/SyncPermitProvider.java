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

package com.swirlds.platform.gossip.permits;

import static com.swirlds.common.units.TimeUnit.UNIT_NANOSECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_SECONDS;
import static com.swirlds.common.utility.CompareTo.isLessThan;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;

// TODO unit test

/**
 * Manages sync permits.
 */
public class SyncPermitProvider {

    /**
     * The total number of available permits.
     */
    private final int totalPermits;

    /**
     * The number of permits that are currently held by a sync operation.
     */
    private int usedPermits;

    /**
     * Provides wall clock time.
     */
    private final Time time;

    /**
     * If true, the system is currently in a healthy state.
     */
    private boolean healthy;

    /**
     * If {@link #healthy} is true, this is the time when the system became healthy. If {@link #healthy} is false, this
     * is the time when the system became unhealthy.
     */
    private Instant statusStartTime;

    /**
     * The current number of permits that have been revoked due to system health.
     */
    private int revokedPermits;

    /**
     * When the system is in an unhealthy state, revoked permits accumulate here. When the status changes back to
     * healthy, these permits are summed into {@link #revokedPermits}.
     */
    private double revokedPermitDelta;

    /**
     * When the system is in a healthy state, permits that have been revoked are returned here. When the status changes
     * back to unhealthy, these permits are summed into {@link #revokedPermits}.
     */
    private double returnedPermitDelta;

    /**
     * The number of permits that are revoked per second when the system is unhealthy.
     */
    private final double permitsRevokedPerSecond = 1.0; // TODO

    /**
     * The number of revoked permits that are returned per second when the system is healthy.
     */
    private final double permitsReturnedPerSecond = 1.0; // TODO

    /**
     * The grace period that the system is given to become healthy before permits begin to be revoked.
     */
    private final Duration unhealthyGracePeriod = Duration.ofSeconds(5); // TODO

    /**
     * The metrics for this class.
     */
    private final SyncPermitMetrics metrics;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param totalPermits    the total number of available permits
     */
    public SyncPermitProvider(@NonNull final PlatformContext platformContext, final int totalPermits) {
        this.metrics = new SyncPermitMetrics(platformContext);

        this.totalPermits = totalPermits;
        this.time = platformContext.getTime();

        // We are healthy at startup time
        healthy = true;
        statusStartTime = time.now();
    }

    /**
     * Attempt to a quire a permit to sync with a peer.
     *
     * @return true if a permit was acquired, false otherwise
     */
    public synchronized boolean acquire() {
        if (getAvailablePermits() > 0) {
            usedPermits++;
            updateMetrics();
            return true;
        }
        return false;
    }

    /**
     * Report that the system is healthy.
     *
     * @param duration the duration for which the system has been healthy, or {@link Duration#ZERO} if the system has
     *                 become healthy again
     */
    public synchronized void reportUnhealthyDuration(@NonNull final Duration duration) {
        if (isLessThan(duration, unhealthyGracePeriod)) {
            if (!healthy) {
                computeRevokedPermits();
                healthy = true;
                statusStartTime = time.now();
                revokedPermits += (int) revokedPermitDelta;
                revokedPermitDelta = 0;
            }
        } else {
            if (healthy) {
                computeRevokedPermits();
                healthy = false;
                statusStartTime = time.now();
                revokedPermits -= (int) returnedPermitDelta;
                returnedPermitDelta = 0;
            }
        }
    }

    /**
     * Release a permit. Should be called exactly once for each call to {@link #acquire()} that returns true.
     */
    public synchronized void release() {
        if (usedPermits == 0) {
            throw new IllegalStateException("No permits to release");
        }
        usedPermits--;
        updateMetrics();
    }

    /**
     * Blocks until all permits have been released.
     */
    public void waitForAllPermitsToBeReleased() {
        while (true) {
            synchronized (this) {
                if (usedPermits == 0) {
                    return;
                }
                try {
                    MILLISECONDS.sleep(10);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("interrupted while waiting for all permits to be released", e);
                }
            }
        }
    }

    /**
     * Get the number of permits that are currently available.
     *
     * @return the number of permits that are currently available
     */
    private int getAvailablePermits() {
        computeRevokedPermits();
        final int revokedCount = revokedPermits + (int) revokedPermitDelta - (int) returnedPermitDelta;
        return Math.max(0, totalPermits - usedPermits - revokedCount);
    }

    /**
     * Compute the number of permits that are currently unhealthy.
     */
    private void computeRevokedPermits() {
        if (healthy) {
            // When healthy, permits are gradually returned

            if (revokedPermits == 0) {
                // No permits are currently revoked.
                return;
            }

            final Duration healthyDuration = Duration.between(statusStartTime, time.now());
            final double healthySeconds = UNIT_NANOSECONDS.convertTo(healthyDuration.toNanos(), UNIT_SECONDS);
            returnedPermitDelta = Math.min(revokedPermits, healthySeconds * permitsReturnedPerSecond);

            if (returnedPermitDelta == revokedPermits) {
                // We have returned all permits that were revoked.
                revokedPermits = 0;
                returnedPermitDelta = 0;
            }
        } else {
            // We mark more permits as unusable the longer the system has been unhealthy

            if (revokedPermits == totalPermits) {
                // All permits are already revoked.
                return;
            }

            final Duration unhealthyDuration = Duration.between(statusStartTime, time.now());
            final double unhealthySeconds = UNIT_NANOSECONDS.convertTo(unhealthyDuration.toNanos(), UNIT_SECONDS);
            revokedPermitDelta = Math.min(totalPermits, unhealthySeconds * permitsRevokedPerSecond);

            if (revokedPermitDelta == totalPermits) {
                // We have revoked all permits.
                revokedPermits = totalPermits;
                revokedPermitDelta = 0;
            }
        }
    }

    /**
     * Update the metrics with the current permit counts.
     */
    private void updateMetrics() {
        metrics.reportPermits(
                getAvailablePermits(),
                revokedPermits + (int) revokedPermitDelta - (int) returnedPermitDelta,
                usedPermits);
    }
}
