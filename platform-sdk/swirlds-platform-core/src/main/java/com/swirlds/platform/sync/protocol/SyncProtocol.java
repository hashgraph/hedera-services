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
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Executes the sync protocol where events are exchanged with a peer and all events are sent and received in topological
 * order.
 * <p>
 * This object will be instantiated once per pair of peers, and is bidirectional
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
     * Metrics tracking syncing
     */
    private final SyncMetrics syncMetrics;

    /**
     * The provider for sync permits
     */
    private final SyncPermitProvider permitProvider;

    /**
     * A permit to sync, which may or may not be acquired
     */
    private MaybeLocked permit = MaybeLocked.NOT_ACQUIRED;

    private Instant lastSyncTime = Instant.MIN;

    private final Duration sleepAfterSync;

    /**
     * Constructs a new sync protocol
     *
     * @param peerId                 the id of the peer being synced with in this protocol
     * @param synchronizer           the shadow graph synchronizer, responsible for actually doing the sync
     * @param fallenBehindManager    manager to determine whether this node has fallen behind
     * @param permitProvider         provides permits to sync
     * @param criticalQuorum         determines whether a peer is a good candidate to sync with
     * @param peerAgnosticSyncChecks peer agnostic checks to determine whether this node should sync
     * @param syncMetrics            metrics tracking syncing
     */
    public SyncProtocol(
            @NonNull final NodeId peerId,
            @NonNull final ShadowGraphSynchronizer synchronizer,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final SyncPermitProvider permitProvider,
            @NonNull final CriticalQuorum criticalQuorum,
            @NonNull final PeerAgnosticSyncChecks peerAgnosticSyncChecks,
            @NonNull final Duration sleepAfterSync,
            @NonNull final SyncMetrics syncMetrics) {

        this.peerId = throwArgNull(peerId, "peerId");
        this.synchronizer = throwArgNull(synchronizer, "synchronizer");
        this.fallenBehindManager = throwArgNull(fallenBehindManager, "fallenBehindManager");
        this.permitProvider = throwArgNull(permitProvider, "permitProvider");
        this.criticalQuorum = throwArgNull(criticalQuorum, "criticalQuorum");
        this.peerAgnosticSyncChecks = throwArgNull(peerAgnosticSyncChecks, "peerAgnosticSyncCheck");
        this.sleepAfterSync = throwArgNull(sleepAfterSync, "sleepAfterSync");
        this.syncMetrics = throwArgNull(syncMetrics, "syncMetrics");
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

    private boolean syncCooldownComplete() {
        return Duration.between(lastSyncTime, Instant.now()).compareTo(sleepAfterSync) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        syncMetrics.opportunityToInitiateSync();

        // are there any reasons not to initiate?
        if (!syncCooldownComplete()
                || permit.isLockAcquired()
                || !peerAgnosticSyncChecks.shouldSync()
                || fallenBehindManager.hasFallenBehind()) {
            syncMetrics.updateDeclinedToInitiateSyncRatio(true);
            return false;
        }

        // is there a reason to initiate?
        if (peerNeededForFallenBehind() || criticalQuorum.isInCriticalQuorum(peerId.getId())) {
            permit = permitProvider.tryAcquire();
            final boolean isLockAcquired = permit.isLockAcquired();

            if (isLockAcquired) {
                syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
                syncMetrics.outgoingSyncRequestSent();
            }

            syncMetrics.updateDeclinedToInitiateSyncRatio(!isLockAcquired);

            return isLockAcquired;
        } else {
            syncMetrics.updateDeclinedToInitiateSyncRatio(true);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldAccept() {
        syncMetrics.incomingSyncRequestReceived();

        // are there any reasons not to accept?
        if (!syncCooldownComplete()
                || permit.isLockAcquired()
                || !peerAgnosticSyncChecks.shouldSync()
                || fallenBehindManager.hasFallenBehind()) {
            syncMetrics.updateRejectedSyncRatio(true);
            return false;
        }

        permit = permitProvider.tryAcquire();
        final boolean isLockAcquired = permit.isLockAcquired();

        if (isLockAcquired) {
            syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
            syncMetrics.acceptedSyncRequest();
        }

        syncMetrics.updateRejectedSyncRatio(!isLockAcquired);

        return isLockAcquired;
    }

    /**
     * Closes the existing permit, and resets the {@link #permit} member variable
     */
    private void closePermit() {
        permit.close();
        permit = MaybeLocked.NOT_ACQUIRED;

        syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initiateFailed() {
        closePermit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acceptFailed() {
        closePermit();
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

        if (!permit.isLockAcquired()) {
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
            closePermit();
            syncMetrics.syncCompleted();

            lastSyncTime = Instant.now();
        }
    }
}
