// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.threading.locks.internal;

import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.AutoClosableResourceLock;
import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.common.threading.locks.locked.MaybeLockedResource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * An implementation of {@link AutoClosableLock} which holds a resource that needs to be locked before it can be used
 *
 * @param <T>
 * 		the type of resource
 */
public final class ResourceLock<T> implements AutoClosableResourceLock<T> {

    private final Lock lock;

    private final MaybeLockedResource<T> acquired;

    private final MaybeLockedResource<T> notAcquired;

    public ResourceLock(@NonNull final Lock lock, @Nullable final T resource) {
        this.lock = lock;
        acquired = new AcquiredResource<>(lock::unlock, resource);
        notAcquired = new NotAcquiredResource<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public LockedResource<T> lock() {
        lock.lock();
        return acquired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public LockedResource<T> lockInterruptibly() throws InterruptedException {
        lock.lockInterruptibly();
        return acquired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MaybeLockedResource<T> tryLock() {
        if (lock.tryLock()) {
            return acquired;
        }
        return notAcquired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MaybeLockedResource<T> tryLock(final long time, @NonNull final TimeUnit unit) throws InterruptedException {
        if (lock.tryLock(time, unit)) {
            return acquired;
        }
        return notAcquired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Condition newCondition() {
        return lock.newCondition();
    }
}
