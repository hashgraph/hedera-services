package com.swirlds.platform.sync.protocol;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.common.threading.SyncPermitProvider;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the sync permits for an individual sync protocol
 * <p>
 * Ensures that only 1 sync can occur at a time with the protocol peer. Also ensures that limits for concurrent
 * incoming/outgoing syncs aren't exceeded
 */
public class SyncProtocolPermitManager {
    private final SyncPermitProvider outgoingPermitProvider;

    private final SyncPermitProvider incomingPermitProvider;

    private MaybeLocked maybeAcquiredPermit = MaybeLocked.NOT_ACQUIRED;

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    private final AutoClosableLock lock = Locks.createAutoLock();

    public SyncProtocolPermitManager(
            final @NonNull SyncPermitProvider outgoingPermitProvider,
            final @NonNull SyncPermitProvider incomingPermitProvider) {

        this.outgoingPermitProvider = ArgumentUtils.throwArgNull(outgoingPermitProvider, "outgoingPermitProvider");
        this.incomingPermitProvider = ArgumentUtils.throwArgNull(incomingPermitProvider, "incomingPermitProvider");
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
            if (syncing.get()) {
                if (!maybeAcquiredPermit.isLockAcquired()) {
                    throw new IllegalStateException("Lock should be acquired if protocol is syncing");
                }

                return false;
            }

            if (outgoing) {
                maybeAcquiredPermit = outgoingPermitProvider.tryAcquire();

                final boolean isLockAcquired = maybeAcquiredPermit.isLockAcquired();

                if (isLockAcquired) {
                    syncing.set(true);
                }

                return isLockAcquired;
            } else {
                maybeAcquiredPermit = incomingPermitProvider.tryAcquire();

                final boolean isLockAcquired = maybeAcquiredPermit.isLockAcquired();

                if (isLockAcquired) {
                    syncing.set(true);
                }

                return isLockAcquired;
            }
        }
    }

    /**
     * Closes any active permits
     */
    public void closePermits() {
        try (final Locked locked = lock.lock()) {
            if (!syncing.get()) {
                throw new IllegalStateException("Attempted to close permit when not syncing");
//                return;
            }

            if (!maybeAcquiredPermit.isLockAcquired()) {
                throw new IllegalStateException("Permit should be acquired if syncing");
            }

            maybeAcquiredPermit.close();

            syncing.set(false);
        }
    }

    /**
     * @return true if a sync permit has been acquired, otherwise false
     */
    public boolean isAcquired() {
        return syncing.get();
    }
}
