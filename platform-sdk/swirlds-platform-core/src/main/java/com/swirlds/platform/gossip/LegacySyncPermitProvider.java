/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Manages the permits that allow syncs to occur in the protocol paradigm. Syncs should only proceed once a permit is
 * acquired. This class is thread safe.
 */
public class LegacySyncPermitProvider { // TODO delete
    /**
     * A semaphore that is used to manage the number of concurrent syncs
     */
    private final Semaphore syncPermits;

    /**
     * The number of permits this provider has available to distribute
     */
    private final int numPermits;

    /**
     * Keeps track of how many events have been received from each peer, but haven't yet made it
     * through the intake pipeline
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * Creates a new instance with a maximum number of permits
     *
     * @param numPermits         the number of concurrent syncs this provider will allow
     * @param intakeEventCounter keeps track of how many events have been received from each peer
     */
    public LegacySyncPermitProvider(final int numPermits, @NonNull final IntakeEventCounter intakeEventCounter) {
        this.numPermits = numPermits;
        this.syncPermits = new Semaphore(numPermits);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
    }

    /**
     * @return the current number of available sync permits
     */
    public int getNumAvailable() {
        return syncPermits.availablePermits();
    }

    /**
     * The result of attempting to acquire a permit.
     */
    public enum PermitRequestResult {
        /**
         * A permit was successfully acquired.
         */
        PERMIT_ACQUIRED,
        /**
         * A permit was not acquired because there are unprocessed events from the peer.
         */
        UNPROCESSED_EVENTS,
        /**
         * A permit was not acquired because there are no permits available.
         */
        NO_PERMIT_AVAILABLE
    }

    /**
     * Attempts to acquire a sync permit. This method returns immediately and never blocks, even if no permit is
     * available.
     * <p>
     * If this method returns true, then the caller must call {@link #returnPermit()} when it is done with the permit.
     *
     * @return true if a permit was successfully acquired, otherwise false.
     */
    @NonNull
    public PermitRequestResult tryAcquire(@NonNull final NodeId peerId) {
        if (intakeEventCounter.hasUnprocessedEvents(peerId)) {
            return PermitRequestResult.UNPROCESSED_EVENTS;
        }
        return syncPermits.tryAcquire() ? PermitRequestResult.PERMIT_ACQUIRED : PermitRequestResult.NO_PERMIT_AVAILABLE;
    }

    /**
     * Returns a permit
     */
    public void returnPermit() {
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
