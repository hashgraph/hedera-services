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

package com.swirlds.platform.sync;

import com.swirlds.common.threading.SyncLock;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controls simultaneous syncs:
 * - prevents 2 simultaneous syncs from occurring with the same member
 * - prevents too many inbound syncs occurring at the same time
 */
public class SimultaneousSyncThrottle {
    /** number of listener threads currently in a sync */
    private final AtomicInteger numListenerSyncs = new AtomicInteger(0);
    /** number of syncs currently happening (both caller and listener) */
    private final AtomicInteger numSyncs = new AtomicInteger(0);

    private final int maxListenerSyncs;
    /** lock per each other member, each one is used by all caller threads and the listener thread */
    private final Map<Long, SyncLock> simSyncThrottleLock;

    public SimultaneousSyncThrottle(final int maxListenerSyncs) {
        this.maxListenerSyncs = maxListenerSyncs;
        simSyncThrottleLock = new ConcurrentHashMap<>();
    }

    /**
     * Try to acquire the lock for syncing with the given node. The lock will be acquired if no syncs are ongoing with
     * that node.
     *
     * @param nodeId
     * 		the ID of the node we wish to sync with
     * @param isOutbound
     * 		is the sync initiated by this platform
     * @return an Autocloseable lock that provides information on whether the lock was acquired or not, and unlocks if
     * 		previously locked on close()
     */
    public MaybeLocked trySync(final long nodeId, final boolean isOutbound) {
        // if trying to do an inbound sync, check the max value
        if (!isOutbound && numListenerSyncs.get() > maxListenerSyncs) {
            return MaybeLocked.NOT_ACQUIRED;
        }

        return simSyncThrottleLock.computeIfAbsent(nodeId, this::newSyncLock).tryLock(isOutbound);
    }

    private SyncLock newSyncLock(final long nodeId) {
        return new SyncLock(this::incrementSyncCount, this::decrementSyncCount);
    }

    private void incrementSyncCount(final boolean isOutbound) {
        numSyncs.incrementAndGet();
        if (!isOutbound) {
            numListenerSyncs.incrementAndGet();
        }
    }

    private void decrementSyncCount(final boolean isOutbound) {
        numSyncs.decrementAndGet();
        if (!isOutbound) {
            numListenerSyncs.decrementAndGet();
        }
    }

    /**
     * Waits for all sync locks to be released sequentially. It does not prevent these locks from being acquired again.
     */
    public void waitForAllSyncsToFinish() {
        for (final SyncLock lock : simSyncThrottleLock.values()) {
            lock.getLock().lock();
            lock.getLock().unlock();
        }
    }

    public int getNumListenerSyncs() {
        return numListenerSyncs.get();
    }

    public int getNumSyncs() {
        return numSyncs.get();
    }
}
