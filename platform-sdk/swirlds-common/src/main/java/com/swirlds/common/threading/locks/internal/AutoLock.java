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
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A standard lock that provides the {@link AutoCloseable} semantics. Lock is reentrant.
 */
public class AutoLock implements AutoClosableLock {

    private final Lock lock = new ReentrantLock();

    /**
     * {@inheritDoc}
     */
    @Override
    public Locked lock() {
        lock.lock();
        return lock::unlock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locked lockInterruptibly() throws InterruptedException {
        lock.lockInterruptibly();
        return lock::unlock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MaybeLocked tryLock() {
        final boolean locked = lock.tryLock();
        return new MaybeLocked() {
            @Override
            public boolean isLockAcquired() {
                return locked;
            }

            @Override
            public void close() {
                if (locked) {
                    lock.unlock();
                }
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MaybeLocked tryLock(final long time, final TimeUnit unit) throws InterruptedException {
        final boolean locked = lock.tryLock(time, unit);
        return new MaybeLocked() {
            @Override
            public boolean isLockAcquired() {
                return locked;
            }

            @Override
            public void close() {
                if (locked) {
                    lock.unlock();
                }
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Condition newCondition() {
        return lock.newCondition();
    }
}
