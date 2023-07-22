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

package com.swirlds.platform.gossip.sync;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Manages the permits that allow syncs to occur in the protocol paradigm. Syncs should only proceed once a permit is
 * acquired. This class is thread safe.
 */
public class SyncPermitProvider {

    private final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * The total number of permits.
     */
    private final int totalPermits;

    /**
     * The number of permits currently held.
     */
    private int permitsHeld = 0;

    /**
     * Provides the current size of the intake queue.
     */
    private final IntSupplier intakeQueueSizeSupplier;

    /**
     * The lower threshold for the intake queue. A queue smaller than this is considered healthy.
     */
    private final int lowerIntakeQueueThreshold;

    /**
     * Is permit suspension enabled?
     */
    private final boolean permitSuspensionEnabled;

    /**
     * The upper threshold for the intake queue. A queue larger than this is considered extremely unhealthy. A queue
     * halfway between the lower and upper thresholds is considered 50% unhealthy, and so on.
     */
    private final int upperIntakeQueueThreshold;

    /**
     * The range between the lower and upper intake queue thresholds.
     */
    private final int intakeQueueDesiredRange;

    /**
     * The number of permits that are allowed to be suspended due to event ingestion pressure.
     */
    private final int suspendablePermitCount;

    public SyncPermitProvider(
            @NonNull final PlatformContext platformContext, @NonNull final IntSupplier intakeQueueSizeSupplier) {

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        this.intakeQueueSizeSupplier = Objects.requireNonNull(intakeQueueSizeSupplier);

        totalPermits = syncConfig.syncProtocolPermitCount();
        permitSuspensionEnabled = syncConfig.permitSuspensionEnabled();
        lowerIntakeQueueThreshold = syncConfig.lowerIntakeQueueThreshold();
        upperIntakeQueueThreshold = syncConfig.upperIntakeQueueThreshold();
        intakeQueueDesiredRange = upperIntakeQueueThreshold - lowerIntakeQueueThreshold;
        suspendablePermitCount = totalPermits - syncConfig.unsuspendablePermitCount();

        buildMetrics(platformContext.getMetrics());
    }

    /**
     * Acquire a permit if one is available.
     *
     * @return true if a permit was acquired, false otherwise
     */
    public boolean tryAcquire() {
        try (final Locked l = lock.lock()) {
            final int availablePermits = totalPermits - permitsHeld - getSuspendedPermitCountInternal();
            if (availablePermits < 1) {
                return false;
            }

            permitsHeld++;
            return true;
        }
    }

    /**
     * Release a permit. Should only be called after {@link #tryAcquire()}, and only if a permit was successfully
     * acquired.
     */
    public void release() {
        try (final Locked l = lock.lock()) {
            if (permitsHeld < 1) {
                throw new IllegalStateException("too many permits released");
            }
            permitsHeld--;
        }
    }

    /**
     * Get the current number of available sync permits. May be negative if permits are suspended after being acquired.
     */
    public int getAvailablePermitCount() {
        try (final Locked l = lock.lock()) {
            return totalPermits - permitsHeld - getSuspendedPermitCountInternal();
        }
    }

    /**
     * Get the number of sync permits currently held.
     */
    public int getHeldPermitCount() {
        try (final Locked l = lock.lock()) {
            return permitsHeld;
        }
    }

    /**
     * Get the number of permits that are currently suspended due to event ingestion pressure.
     */
    public int getSuspendedPermitCount() {
        try (final Locked l = lock.lock()) {
            return getSuspendedPermitCountInternal();
        }
    }

    /**
     * Acquires all permits uninterruptibly, then releases them again. Ignores permit suspensions.
     */
    public void acquireAndReleaseAll() throws InterruptedException {
        int permits = 0;
        while (permits < totalPermits) {
            try (final Locked l = lock.lock()) {
                final int availablePermits = totalPermits - permitsHeld;
                permitsHeld += availablePermits;
                permits += availablePermits;
                if (permits == totalPermits) {
                    permitsHeld = 0;
                    return;
                }
            }
            MILLISECONDS.sleep(1);
        }
    }

    /**
     * Get the number of permits not allowed to be issued due to event ingestion pressure.
     */
    private int getSuspendedPermitCountInternal() {
        if (!permitSuspensionEnabled) {
            return 0;
        }

        final int currentIntakeQueueSize = intakeQueueSizeSupplier.getAsInt();

        if (currentIntakeQueueSize < lowerIntakeQueueThreshold) {
            // Intake queue is healthy, don't suspend any permits.
            return 0;
        }
        if (currentIntakeQueueSize > upperIntakeQueueThreshold) {
            // Intake queue is unhealthy, suspend maximum allowable number of permits.
            return suspendablePermitCount;
        }

        final int excessQueueSize = currentIntakeQueueSize - lowerIntakeQueueThreshold;
        final double fraction = ((double) excessQueueSize) / intakeQueueDesiredRange;

        return (int) (fraction * suspendablePermitCount);
    }

    /**
     * Build metrics to track permits.
     */
    private void buildMetrics(@NonNull final Metrics metrics) {
        final FunctionGauge.Config<Integer> permitsAvailableConfig = new FunctionGauge.Config<>(
                        "platform", "syncPermitsAvailable", Integer.class, this::getAvailablePermitCount)
                .withDescription("The number of sync permits currently available.");
        metrics.getOrCreate(permitsAvailableConfig);

        final FunctionGauge.Config<Integer> permitsHeldConfig = new FunctionGauge.Config<>(
                        "platform", "syncPermitsHeld", Integer.class, this::getHeldPermitCount)
                .withDescription("The number of sync permits currently held.");
        metrics.getOrCreate(permitsHeldConfig);

        final FunctionGauge.Config<Integer> permitsSuspendedConfig = new FunctionGauge.Config<>(
                        "platform", "syncPermitsSuspended", Integer.class, this::getSuspendedPermitCount)
                .withDescription("The number of sync permits currently suspended due to event ingestion pressure.");
        metrics.getOrCreate(permitsSuspendedConfig);
    }
}
