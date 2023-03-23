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

package com.swirlds.common.threading;

import com.swirlds.common.threading.locks.internal.AcquiredOnTry;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import java.util.concurrent.Semaphore;

/**
 * Manages the permits that allow syncs to occur in the protocol paradigm. Syncs should only proceed once a permit is
 * acquired.
 */
public class SyncPermit {

    private final Semaphore syncPermits;
    private final AcquiredOnTry acquired;

    /**
     * Creates a new instance with a maximum number of permits
     *
     * @param numPermits the number of concurrent outgoing syncs to allow
     */
    public SyncPermit(final int numPermits) {
        this.syncPermits = new Semaphore(numPermits);
        this.acquired = new AcquiredOnTry(syncPermits::release);
    }

    /**
     * @return the current number of available sync permits
     */
    public int getNumAvailable() {
        return syncPermits.availablePermits();
    }

    /**
     * Attempts to acquire a sync permit. This method returns immediately if no permit is not available.
     *
     * @return an autocloseable instance that tells the caller if the permit has been acquired and will automatically
     * release the permit when used in a try-with-resources block
     */
    public MaybeLocked tryAcquire() {
        if (syncPermits.tryAcquire()) {
            return acquired;
        }
        return MaybeLocked.NOT_ACQUIRED;
    }
}
