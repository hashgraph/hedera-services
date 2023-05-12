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

package com.swirlds.common.threading.locks.internal;

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.threading.locks.locked.MaybeLockedResource;

/**
 * An instance which is returned by the {@link ResourceLock} when the lock is acquired. Provides access to the locked
 * resource.
 *
 * @param <T>
 * 		the type of resource
 */
public class AcquiredResource<T> implements MaybeLockedResource<T> {
    private final AutoCloseableNonThrowing unlock;
    private T resource;

    public AcquiredResource(final AutoCloseableNonThrowing unlock, final T resource) {
        this.unlock = unlock;
        this.resource = resource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T getResource() {
        return resource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setResource(final T resource) {
        this.resource = resource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLockAcquired() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        unlock.close();
    }
}
