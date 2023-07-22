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
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Manages the permits that allow syncs to occur in the protocol paradigm. Syncs should only proceed once a permit is
 * acquired. This class is thread safe.
 */
public class SyncPermitProvider {

    private final AutoClosableLock lock = Locks.createAutoLock();

    private final int totalPermits;
    private int permitsHeld = 0;

    public SyncPermitProvider(@NonNull final PlatformContext platformContext) {

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        totalPermits = syncConfig.syncProtocolPermitCount();
    }

    // TODO remove this
    /**
     * @return the current number of available sync permits
     */
    public int getNumAvailable() {
        return 0; // TODO move metric inside this class
    }

    /**
     * Acquire a permit if one is available.
     *
     * @return true if a permit was acquired, false otherwise
     */
    public boolean tryAcquire() {
        try (final Locked l = lock.lock()) {
            final int availablePermits = totalPermits - permitsHeld - getNumberOfSuspendedPermits();
            if (availablePermits < 1) {
                return false;
            }

            permitsHeld++;
            return true;
        }
    }

    /**
     * Release a permit. Should only be called after {@link #tryAcquire()}, and only if a permit was successfully
     * acquired..
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
     * First acquires all permits uninterruptibly, then releases them again. The effect of this is the caller waiting
     * for all permits to be returned before proceeding
     */
    public void waitForAllSyncsToFinish() throws InterruptedException {
        int permits = 0;
        while (permits < totalPermits) {
            try (final Locked l = lock.lock()) {
                final int availablePermits = totalPermits - permitsHeld;
                permitsHeld += availablePermits;
                permits += availablePermits;
            }

            if (permits < totalPermits) {
                MILLISECONDS.sleep(1);
            }
        }

        try (final Locked l = lock.lock()) {
            permitsHeld = 0;
        }
    }

    /**
     * Get the number of permits not allowed to be issued due to event ingestion pressure.
     */
    private int getNumberOfSuspendedPermits() {

        return 0;
    }
}
