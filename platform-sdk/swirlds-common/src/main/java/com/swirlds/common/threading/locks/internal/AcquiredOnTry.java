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

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.threading.locks.locked.MaybeLocked;

/**
 * Returned when a lock has been acquired on a try
 */
public class AcquiredOnTry implements MaybeLocked {
    private final AutoCloseableNonThrowing close;

    public AcquiredOnTry(final AutoCloseableNonThrowing close) {
        this.close = close;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        close.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLockAcquired() {
        return true;
    }
}
