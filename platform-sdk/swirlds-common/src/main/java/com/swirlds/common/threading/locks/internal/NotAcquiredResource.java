/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.threading.locks.locked.MaybeLockedResource;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Return an instance of this when a {@link ResourceLock} has not been acquired
 */
public final class NotAcquiredResource<T> implements MaybeLockedResource<T> {

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public T getResource() {
        throw new IllegalStateException("Cannot get resource if the lock is not obtained");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setResource(@Nullable T resource) {
        throw new IllegalStateException("Cannot set resource if the lock is not obtained");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLockAcquired() {
        return false;
    }
}
