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
import com.swirlds.common.threading.SyncPermitProvider;
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
 * <p>
 * This object will be instantiated once per set of peers, and is bidirectional
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
     * The critical quorum, which determines whether a peer is a good candidate to sync with
     */
    private final CriticalQuorum criticalQuorum;

    /**
     * Peer agnostic checks which are performed to determine whether this node should sync or not
     */
    private final PeerAgnosticSyncChecks peerAgnosticSyncChecks;

    /**
     * Supplier for the amount of time to sleep after a sync
     */
    private final LongSupplier sleepAfterSyncSupplier;

    /**
     * Metrics tracking syncing
     */
    private final SyncMetrics syncMetrics;

    /**
     * Manages the permits for this protocol. Ensures that only one sync is in progress at a time, and that the node
     * doesn't exceed concurrent incoming/outgoing sync maximums
     */
    private final SyncProtocolPermitManager permitManager;

    /**
     * Constructs a new sync protocol
     *
     * @param peerId                     the id of the peer being synced with in this protocol
     * @param synchronizer               the shadow graph synchronizer, responsible for actually doing the sync
     * @param fallenBehindManager        manager to determine whether this node has fallen behind
     * @param outgoingSyncPermitProvider provides permits to initiate syncs
     * @param incomingSyncPermitProvider provides permits to receive syncs
     * @param criticalQuorum             determines whether a peer is a good candidate to sync with
     * @param peerAgnosticSyncChecks     peer agnostic checks which are performed to determine whether this node should
     *                                   sync
     * @param syncMetrics                metrics tracking syncing
     */
    public SyncProtocol(
            @NonNull final NodeId peerId,
            @NonNull final ShadowGraphSynchronizer synchronizer,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final SyncPermitProvider outgoingSyncPermitProvider,
            @NonNull final SyncPermitProvider incomingSyncPermitProvider,
            @NonNull final CriticalQuorum criticalQuorum,
            @NonNull final PeerAgnosticSyncChecks peerAgnosticSyncChecks,
            @NonNull final LongSupplier sleepAfterSyncSupplier,
            @NonNull final SyncMetrics syncMetrics) {

        this.peerId = throwArgNull(peerId, "peerId");
        this.synchronizer = throwArgNull(synchronizer, "synchronizer");
        this.fallenBehindManager = throwArgNull(fallenBehindManager, "fallenBehindManager");
        this.criticalQuorum = throwArgNull(criticalQuorum, "criticalQuorum");
        this.peerAgnosticSyncChecks = throwArgNull(peerAgnosticSyncChecks, "peerAgnosticSyncCheck");
        this.sleepAfterSyncSupplier = throwArgNull(sleepAfterSyncSupplier, "sleepAfterSyncSupplier");
        this.syncMetrics = throwArgNull(syncMetrics, "syncMetrics");

        this.permitManager = new SyncProtocolPermitManager(outgoingSyncPermitProvider, incomingSyncPermitProvider);
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
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        if (!peerAgnosticSyncChecks.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            return false;
        }

        if (peerNeededForFallenBehind() || criticalQuorum.isInCriticalQuorum(peerId.getId())) {
            return permitManager.tryAcquirePermit(true);
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        if (!peerAgnosticSyncChecks.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            syncMetrics.updateRejectedSyncRatio(true);
            return false;
        }

        final boolean permitAcquired = permitManager.tryAcquirePermit(false);
        syncMetrics.updateRejectedSyncRatio(!permitAcquired);
        return permitAcquired;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initiateFailed() {
        permitManager.closePermits();
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

        if (!permitManager.isAcquired()) {
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
            permitManager.closePermits();

            final long sleepAfterSyncDuration = sleepAfterSyncSupplier.getAsLong();
            if (sleepAfterSyncDuration > 0) {
                Thread.sleep(sleepAfterSyncDuration);
            }
        }
    }
}
