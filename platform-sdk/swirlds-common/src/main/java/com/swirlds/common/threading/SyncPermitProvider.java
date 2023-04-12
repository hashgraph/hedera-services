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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Semaphore;

/**
 * Manages the permits that allow syncs to occur in the protocol paradigm. Syncs should only proceed once a permit is
 * acquired. This class is thread safe.
 */
public class SyncPermitProvider {
    /**
     * A semaphore that is used to manage the number of concurrent syncs
     */
    private final Semaphore syncPermits;

    /**
     * The object returned when a permit is successfully obtained
     */
    private final AcquiredOnTry acquired;

    private final int numPermits;

    /**
     * Creates a new instance with a maximum number of permits
     *
     * @param numPermits the number of concurrent syncs this provider will allow
     */
    public SyncPermitProvider(final int numPermits) {
        this.numPermits = numPermits;
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
     * Attempts to acquire a sync permit. This method returns immediately and never blocks, even if no permit is
     * available.
     *
     * @return an autocloseable instance that tells the caller if the permit has been acquired and will automatically
     * release the permit when used in a try-with-resources block
     */
    public @NonNull MaybeLocked tryAcquire() {
        if (syncPermits.tryAcquire()) {
            return acquired;
        }
        return MaybeLocked.NOT_ACQUIRED;
    }

    public void join() {
        syncPermits.acquireUninterruptibly(numPermits);
        syncPermits.release(numPermits);
    }
}
