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

package com.swirlds.platform.gossip.sync.protocol;

import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;

import com.swirlds.base.time.Time;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.SyncPermitProvider;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Executes the sync protocol where events are exchanged with a peer and all events are sent and received in topological
 * order.
 * <p>
 * This object will be instantiated once per peer, and is bidirectional
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

    /**
     * The last time this protocol executed
     */
    private Instant lastSyncTime = Instant.MIN;

    /**
     * The amount of time to sleep after a sync
     */
    private final Duration sleepAfterSync;

    /**
     * A source of time
     */
    private final Time time;

    /**
     * Constructs a new sync protocol
     *
     * @param peerId                 the id of the peer being synced with in this protocol
     * @param synchronizer           the shadow graph synchronizer, responsible for actually doing the sync
     * @param fallenBehindManager    manager to determine whether this node has fallen behind
     * @param permitProvider         provides permits to sync
     * @param criticalQuorum         determines whether a peer is a good candidate to sync with
     * @param peerAgnosticSyncChecks peer agnostic checks to determine whether this node should sync
     * @param sleepAfterSync         the amount of time to sleep after a sync
     * @param syncMetrics            metrics tracking syncing
     * @param time                   a source of time
     */
    public SyncProtocol(
            @NonNull final NodeId peerId,
            @NonNull final ShadowGraphSynchronizer synchronizer,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final SyncPermitProvider permitProvider,
            @NonNull final CriticalQuorum criticalQuorum,
            @NonNull final PeerAgnosticSyncChecks peerAgnosticSyncChecks,
            @NonNull final Duration sleepAfterSync,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final Time time) {

        this.peerId = Objects.requireNonNull(peerId);
        this.synchronizer = Objects.requireNonNull(synchronizer);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.permitProvider = Objects.requireNonNull(permitProvider);
        this.criticalQuorum = Objects.requireNonNull(criticalQuorum);
        this.peerAgnosticSyncChecks = Objects.requireNonNull(peerAgnosticSyncChecks);
        this.sleepAfterSync = Objects.requireNonNull(sleepAfterSync);
        this.syncMetrics = Objects.requireNonNull(syncMetrics);
        this.time = Objects.requireNonNull(time);
    }

    /**
     * Checks whether the peer must be contacted to determine if we have fallen behind
     *
     * @return true if the peer is needed for fallen behind, else false
     */
    private boolean peerNeededForFallenBehind() {
        final List<NodeId> neededForFallenBehind = fallenBehindManager.getNeededForFallenBehind();

        return neededForFallenBehind != null && neededForFallenBehind.contains(peerId);
    }

    /**
     * @return true if the cooldown period after a sync has elapsed, else false
     */
    private boolean syncCooldownComplete() {
        final Duration elapsed = Duration.between(lastSyncTime, time.now());

        return isGreaterThanOrEqualTo(elapsed, sleepAfterSync);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldInitiate() {
        syncMetrics.opportunityToInitiateSync();

        // are there any reasons not to initiate?
        if (!syncCooldownComplete() || !peerAgnosticSyncChecks.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            return false;
        }

        // is there a reason to initiate?
        if (peerNeededForFallenBehind() || criticalQuorum.isInCriticalQuorum(peerId)) {
            permit = permitProvider.tryAcquire();
            final boolean isLockAcquired = permit.isLockAcquired();

            if (isLockAcquired) {
                syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
                syncMetrics.outgoingSyncRequestSent();
            }

            return isLockAcquired;
        } else {
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
        if (!syncCooldownComplete() || !peerAgnosticSyncChecks.shouldSync() || fallenBehindManager.hasFallenBehind()) {
            return false;
        }

        permit = permitProvider.tryAcquire();
        final boolean isLockAcquired = permit.isLockAcquired();

        if (isLockAcquired) {
            syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
            syncMetrics.acceptedSyncRequest();
        }

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

            lastSyncTime = time.now();
        }
    }
}
