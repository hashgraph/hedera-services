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

package com.swirlds.platform.gossip.shadowgraph;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;
import static com.swirlds.common.metrics.Metrics.PLATFORM_CATEGORY;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.SyncLock;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controls simultaneous syncs: - prevents 2 simultaneous syncs from occurring with the same member - prevents too many
 * inbound syncs occurring at the same time
 */
public class SimultaneousSyncThrottle {
    /** number of listener threads currently in a sync */
    private final AtomicInteger numListenerSyncs = new AtomicInteger(0);
    /** number of syncs currently happening (both caller and listener) */
    private final AtomicInteger numSyncs = new AtomicInteger(0);

    private final int maxListenerSyncs;
    /** lock per each other member, each one is used by all caller threads and the listener thread */
    private final Map<NodeId, SyncLock> simSyncThrottleLock;

    private static final RunningAverageMetric.Config AVG_SIM_SYNCS_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "simSyncs")
            .withDescription("avg number of simultaneous syncs happening at any given time")
            .withFormat(FORMAT_9_6);

    private static final RunningAverageMetric.Config AVG_SIM_LISTEN_SYNCS_CONFIG = new RunningAverageMetric.Config(
                    PLATFORM_CATEGORY, "simListenSyncs")
            .withDescription("avg number of simultaneous listening syncs happening at any given time")
            .withFormat(FORMAT_9_6);

    /**
     * Construct a new SimultaneousSyncThrottle
     *
     * @param metrics          the metrics engine
     * @param maxListenerSyncs the maximum number of listener syncs that can happen at the same time
     */
    public SimultaneousSyncThrottle(@NonNull final Metrics metrics, final int maxListenerSyncs) {
        this.maxListenerSyncs = maxListenerSyncs;
        simSyncThrottleLock = new ConcurrentHashMap<>();

        final RunningAverageMetric avgSimSyncs = metrics.getOrCreate(AVG_SIM_SYNCS_CONFIG);
        final RunningAverageMetric avgSimListenSyncs = metrics.getOrCreate(AVG_SIM_LISTEN_SYNCS_CONFIG);
        metrics.addUpdater(() -> {
            avgSimSyncs.update(getNumSyncs());
            avgSimListenSyncs.update(getNumListenerSyncs());
        });
    }

    /**
     * Try to acquire the lock for syncing with the given node. The lock will be acquired if no syncs are ongoing with
     * that node.
     *
     * @param nodeId     the ID of the node we wish to sync with
     * @param isOutbound is the sync initiated by this platform
     * @return an Autocloseable lock that provides information on whether the lock was acquired or not, and unlocks if
     * previously locked on close()
     */
    @NonNull
    public MaybeLocked trySync(@NonNull final NodeId nodeId, final boolean isOutbound) {
        Objects.requireNonNull(nodeId, "nodeId");
        // if trying to do an inbound sync, check the max value
        if (!isOutbound && numListenerSyncs.get() > maxListenerSyncs) {
            return MaybeLocked.NOT_ACQUIRED;
        }

        return simSyncThrottleLock.computeIfAbsent(nodeId, this::newSyncLock).tryLock(isOutbound);
    }

    private SyncLock newSyncLock(@NonNull final NodeId nodeId) {
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
