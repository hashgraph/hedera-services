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

package com.swirlds.platform.sync.protocol;

import static com.swirlds.base.ArgumentUtils.throwArgNull;

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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Executes the sync protocol where events are exchanged with a peer and all events are sent and received in topological
 * order.
 */
public class SyncProtocol implements Protocol {
    /**
     * The id of the peer being synced with in this protocol
     */
    private final NodeId peerId;

    /**
     * The shadow graph synchronizer, responsible for actually doing the sync
     */
    private final ShadowGraphSynchronizer synchronizer;

    /**
     * Manager to determine whether this node has fallen behind
     */
    private final FallenBehindManager fallenBehindManager;

    /**
     * Provides permits to initiate syncs
     */
    private final SyncPermit initiateSyncPermit;

    /**
     * The critical quorum, which determines whether a peer is a good candidate to sync with
     */
    private final CriticalQuorum criticalQuorum;

    /**
     * Peer agnostic checks which are performed to determine whether this node should sync or not
     */
    private final PeerAgnosticSyncChecks peerAgnosticSyncCheck;

    private final LongSupplier sleepAfterSyncSupplier;

    /**
     * Metrics tracking syncing
     */
    private final SyncMetrics syncMetrics;

    /**
     * Contains a permit, if it has been received from {@link #initiateSyncPermit}
     */
    private MaybeLocked maybeAcquiredPermit;

    /**
     * Constructs a new sync protocol
     *
     * @param peerId                the id of the peer being synced with in this protocol
     * @param synchronizer          the shadow graph synchronizer, responsible for actually doing the sync
     * @param fallenBehindManager   manager to determine whether this node has fallen behind
     * @param initiateSyncPermit    provides permits to initiate syncs
     * @param criticalQuorum        determines whether a peer is a good candidate to sync with
     * @param peerAgnosticSyncCheck peer agnostic checks which are performed to determine whether this node should sync
     * @param syncMetrics           metrics tracking syncing
     */
    public SyncProtocol(
            @NonNull final NodeId peerId,
            @NonNull final ShadowGraphSynchronizer synchronizer,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final SyncPermit initiateSyncPermit,
            @NonNull final CriticalQuorum criticalQuorum,
            @NonNull final PeerAgnosticSyncChecks peerAgnosticSyncCheck,
            @NonNull final LongSupplier sleepAfterSyncSupplier,
            @NonNull final SyncMetrics syncMetrics) {

        this.peerId = throwArgNull(peerId, "peerId");
        this.synchronizer = throwArgNull(synchronizer, "synchronizer");
        this.fallenBehindManager = throwArgNull(fallenBehindManager, "fallenBehindManager");
        this.initiateSyncPermit = throwArgNull(initiateSyncPermit, "initiateSyncPermit");
        this.criticalQuorum = throwArgNull(criticalQuorum, "criticalQuorum");
        this.peerAgnosticSyncCheck = throwArgNull(peerAgnosticSyncCheck, "peerAgnosticSyncCheck");
        this.sleepAfterSyncSupplier = throwArgNull(sleepAfterSyncSupplier, "sleepAfterSyncSupplier");
        this.syncMetrics = throwArgNull(syncMetrics, "syncMetrics");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        if (!peerAgnosticSyncCheck.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            return false;
        }

        if (peerNeededForFallenBehind() || criticalQuorum.isInCriticalQuorum(peerId.getId())) {
            return tryAcquirePermit();
        }

        return false;
    }

    /**
     * Checks whether the peer must be contacted to determine if we have fallen behind
     *
     * @return true if the peer is needed for fallen behind, else false
     */
    private boolean peerNeededForFallenBehind() {
        final List<Long> neededForFallenBehind = fallenBehindManager.getNeededForFallenBehind();

        return neededForFallenBehind != null && neededForFallenBehind.contains(peerId.getId());
    }

    /**
     * Attempts to acquire a permit to initiate a sync
     *
     * @return true if the permit was successfully acquired, otherwise false
     */
    private boolean tryAcquirePermit() {
        maybeAcquiredPermit = initiateSyncPermit.tryAcquire();

        return maybeAcquiredPermit.isLockAcquired();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initiateFailed() {
        if (maybeAcquiredPermit != null && maybeAcquiredPermit.isLockAcquired()) {
            maybeAcquiredPermit.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        if (!peerAgnosticSyncCheck.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            syncMetrics.updateRejectedSyncRatio(true);
            return false;
        }
        syncMetrics.updateRejectedSyncRatio(false);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptOnSimultaneousInitiate() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runProtocol(@NonNull final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {

        throwArgNull(connection, "connection");

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
        } finally {
            if (maybeAcquiredPermit.isLockAcquired()) {
                maybeAcquiredPermit.close();
            }

            final long sleepAfterSyncDuration = sleepAfterSyncSupplier.getAsLong();
            if (sleepAfterSyncDuration > 0) {
                Thread.sleep(sleepAfterSyncDuration);
            }
        }
    }
}
