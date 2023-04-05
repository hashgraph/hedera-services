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

package com.swirlds.common.threading.locks.internal;

import com.swirlds.common.threading.locks.IndexLock;
import com.swirlds.common.threading.locks.locked.Locked;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default implementation of {@link IndexLock}
 */
public class DefaultIndexLock implements IndexLock {

    private final int parallelism;
    private final Lock[] locks;

    /**
     * Create a new lock for index values.
     *
     * @param parallelism
     * 		the number of unique locks. Higher parallelism reduces chances of collision for non-identical
     * 		indexes at the cost of additional memory overhead.
     */
    public DefaultIndexLock(final int parallelism) {

        this.parallelism = parallelism;

        this.locks = new Lock[parallelism];
        for (int lockIndex = 0; lockIndex < parallelism; lockIndex++) {
            locks[lockIndex] = new ReentrantLock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lock(final long index) {
        locks[(int) (Math.abs(index) % parallelism)].lock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lock(final Object object) {
        final int hash = object == null ? 0 : object.hashCode();
        lock(hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlock(final long index) {
        locks[(int) (Math.abs(index) % parallelism)].unlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlock(final Object object) {
        final int hash = object == null ? 0 : object.hashCode();
        unlock(hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locked autoLock(final long index) {
        lock(index);
        return () -> unlock(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locked autoLock(final Object object) {
        final int hash = object == null ? 0 : object.hashCode();
        return autoLock(hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fullyLock() {
        for (int index = 0; index < parallelism; index++) {
            locks[index].lock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fullyUnlock() {
        for (int index = 0; index < parallelism; index++) {
            locks[index].unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locked autoFullLock() {
        fullyLock();
        return this::fullyUnlock;
    }
}
