/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.benchmark;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MutableStateManager<S extends MerkleNode> implements StateManager<S> {

    private S state;
    private final Lock lock;

    public MutableStateManager(final S initialState) {
        this.state = initialState;
        lock = new ReentrantLock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AutoCloseableWrapper<S> getState() {
        lock.lock();
        return new AutoCloseableWrapper<>(state, lock::unlock);
    }

    /**
     * Lock the mutex.
     */
    public void lock() {
        lock.lock();
    }

    /**
     * Unlock the mutex.
     */
    public void unlock() {
        lock.unlock();
    }

    /**
     * Set the current mutable state. It is expected that this be called after {@link #lock} and before the
     * corresponding {@link #unlock()}.
     */
    public void setState(final S state) {
        this.state = state;
    }
}
