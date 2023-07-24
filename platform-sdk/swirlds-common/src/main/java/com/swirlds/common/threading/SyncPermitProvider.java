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
     * The number of permits this provider has available to distribute
     */
    private final int numPermits;

    /**
     * Creates a new instance with a maximum number of permits
     *
     * @param numPermits the number of concurrent syncs this provider will allow
     */
    public SyncPermitProvider(final int numPermits) {
        this.numPermits = numPermits;
        this.syncPermits = new Semaphore(numPermits);
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
     * @return true if the permit was acquired, otherwise false
     */
    public boolean tryAcquire() {
        return syncPermits.tryAcquire();
    }

    /**
     * Release a previously acquired permit. Should only be called if {@link #tryAcquire()} was called and returned
     * true.
     */
    public void release() {
        syncPermits.release();
    }

    /**
     * First acquires all permits uninterruptibly, then releases them again. The effect of this is the caller waiting
     * for all permits to be returned before proceeding
     */
    public void waitForAllSyncsToFinish() {
        syncPermits.acquireUninterruptibly(numPermits);
        syncPermits.release(numPermits);
    }
}
