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

package com.swirlds.platform.sync.protocol;

import static com.swirlds.base.ArgumentUtils.throwArgNull;

import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages sync permits for an individual sync protocol
 * <p>
 * Ensures that only 1 sync can occur at a time with the protocol peer. Also ensures that limits for concurrent
 * incoming/outgoing syncs aren't exceeded
 */
public class SyncProtocolPermitProvider {
    /**
     * The semaphore for outgoing syncs
     */
    private final Semaphore outgoingSyncSemaphore;

    /**
     * The semaphore for incoming syncs
     */
    private final Semaphore incomingSyncSemaphore;

    /**
     * The current status of the sync protocol
     */
    private final AtomicReference<SyncProtocolPermitStatus> status =
            new AtomicReference<>(SyncProtocolPermitStatus.NOT_SYNCING);

    /**
     * A lock for safely acquiring and releasing permits
     */
    private final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * Constructor
     *
     * @param outgoingSyncSemaphore the semaphore for outgoing syncs, common across all instances of the sync protocol
     * @param incomingSyncSemaphore the semaphore for incoming syncs, common across all instances of the sync protocol
     */
    public SyncProtocolPermitProvider(
            final @NonNull Semaphore outgoingSyncSemaphore, final @NonNull Semaphore incomingSyncSemaphore) {

        this.outgoingSyncSemaphore = throwArgNull(outgoingSyncSemaphore, "outgoingSyncSemaphore");
        this.incomingSyncSemaphore = throwArgNull(incomingSyncSemaphore, "incomingSyncSemaphore");
    }

    /**
     * Tries to acquire a permit for a sync with the peer
     * <p>
     * First checks whether a sync is already in progress with the peer. If so, returns false immediately. Otherwise,
     * attempts to obtain a permit
     *
     * @param outgoing true indicates a permit is being requested for an outgoing sync, false indicates incoming
     * @return true if a permit was acquired, otherwise false
     */
    public boolean tryAcquirePermit(final boolean outgoing) {
        try (final Locked locked = lock.lock()) {
            if (status.get() != SyncProtocolPermitStatus.NOT_SYNCING) {
                return false;
            }

            final boolean isLockAcquired;
            if (outgoing) {
                isLockAcquired = outgoingSyncSemaphore.tryAcquire();
                if (isLockAcquired) {
                    status.set(SyncProtocolPermitStatus.OUTGOING);
                }
            } else {
                isLockAcquired = incomingSyncSemaphore.tryAcquire();
                if (isLockAcquired) {
                    status.set(SyncProtocolPermitStatus.INCOMING);
                }
            }
            return isLockAcquired;
        }
    }

    /**
     * Closes any active permits
     */
    public void closePermit() {
        try (final Locked locked = lock.lock()) {
            switch (status.get()) {
                case OUTGOING -> outgoingSyncSemaphore.release();
                case INCOMING -> incomingSyncSemaphore.release();
                case NOT_SYNCING -> throw new IllegalStateException("Attempted to close permit when not syncing");
                default -> throw new IllegalStateException("Unknown sync protocol permit status: " + status.get());
            }

            status.set(SyncProtocolPermitStatus.NOT_SYNCING);
        }
    }

    /**
     * @return true if a permit is currently held, otherwise false
     */
    public boolean isAcquired() {
        return status.get() != SyncProtocolPermitStatus.NOT_SYNCING;
    }

    /**
     * The status of the permit provider
     */
    private enum SyncProtocolPermitStatus {
        /**
         * No sync is currently in progress
         */
        NOT_SYNCING,
        /**
         * An outgoing sync is currently in progress
         */
        OUTGOING,
        /**
         * An incoming sync is currently in progress
         */
        INCOMING
    }
}
