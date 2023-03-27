/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.platform.sync.protocol;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.SyncPermit;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.Connection;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.sync.FallenBehindManager;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SyncException;
import java.io.IOException;
import java.util.List;

/**
 * Executes the sync protocol where events are exchanged with a peer and all events are sent and received in topological
 * order.
 */
public class SyncProtocol implements Protocol {
    private final NodeId peerId;
    private final ShadowGraphSynchronizer synchronizer;
    private final FallenBehindManager fallenBehindManager;
    private final SyncPermit initiateSyncPermit;
    private final CriticalQuorum criticalQuorum;
    /** A boolean indicating that, based on peer agnostic checks, whether this node should sync or not */
    private final PeerAgnosticSyncChecks peerAgnosticSyncCheck;
    private final SyncMetrics syncMetrics;
    private MaybeLocked maybeAcquiredPermit;

    // TODO implement single node operation outside of SyncProtocol (dont even start the negotiator threads)

    public SyncProtocol(
            final NodeId peerId,
            final ShadowGraphSynchronizer synchronizer,
            final FallenBehindManager fallenBehindManager,
            final SyncPermit initiateSyncPermit,
            final CriticalQuorum criticalQuorum,
            final PeerAgnosticSyncChecks peerAgnosticSyncCheck,
            final SyncMetrics syncMetrics) {
        this.peerId = peerId;
        this.synchronizer = synchronizer;
        this.fallenBehindManager = fallenBehindManager;
        this.initiateSyncPermit = initiateSyncPermit;
        this.criticalQuorum = criticalQuorum;
        this.peerAgnosticSyncCheck = peerAgnosticSyncCheck;
        this.syncMetrics = syncMetrics;
    }

    @Override
    public boolean shouldInitiate() {
        if (!peerAgnosticSyncCheck.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            return false;
        }

        if (peerNeededForFallenBehind() || peerInCriticalQuorum()) {
            return tryAcquirePermit();
        }

        return false;
    }

    private boolean peerInCriticalQuorum() {
        return criticalQuorum.isInCriticalQuorum(peerId.getId());
    }

    private boolean peerNeededForFallenBehind() {
        final List<Long> neededForFallenBehind = fallenBehindManager.getNeededForFallenBehind();
        return neededForFallenBehind != null && neededForFallenBehind.contains(peerId.getId());
    }

    private boolean tryAcquirePermit() {
        maybeAcquiredPermit = initiateSyncPermit.tryAcquire();
        return maybeAcquiredPermit.isLockAcquired();
    }


    @Override
    public void initiateFailed() {
        if (maybeAcquiredPermit != null && maybeAcquiredPermit.isLockAcquired()) {
            maybeAcquiredPermit.close();
        }
    }

    @Override
    public boolean shouldAccept() {
        if (!peerAgnosticSyncCheck.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            syncMetrics.updateRejectedSyncRatio(true);
            return false;
        }
        syncMetrics.updateRejectedSyncRatio(false);
        return true;
    }

    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return true;
    }

    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        if (maybeAcquiredPermit == null || !maybeAcquiredPermit.isLockAcquired()) {
            throw new NetworkProtocolException("sync permit not acquired prior to executing sync protocol");
        }
        try {
            synchronizer.synchronize(connection);
        } catch (final ParallelExecutionException | SyncException e) {
            if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
                throw new IOException(e);
            }
            throw new NetworkProtocolException(e);
        }
    }
}
