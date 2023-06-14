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
