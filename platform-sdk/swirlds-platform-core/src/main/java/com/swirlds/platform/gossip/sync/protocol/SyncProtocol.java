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

package com.swirlds.platform.gossip.sync.protocol;

import static com.swirlds.common.utility.CompareTo.isGreaterThanOrEqualTo;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.SyncPermitProvider;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
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

    private final PlatformContext platformContext;

    /**
     * Constructs a new sync protocol
     *
     * @param platformContext        the platform context
     * @param peerId                 the id of the peer being synced with in this protocol
     * @param synchronizer           the shadow graph synchronizer, responsible for actually doing the sync
     * @param fallenBehindManager    manager to determine whether this node has fallen behind
     * @param permitProvider         provides permits to sync
     * @param peerAgnosticSyncChecks peer agnostic checks to determine whether this node should sync
     * @param sleepAfterSync         the amount of time to sleep after a sync
     * @param syncMetrics            metrics tracking syncing
     * @param time                   a source of time
     */
    public SyncProtocol(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId peerId,
            @NonNull final ShadowGraphSynchronizer synchronizer,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final SyncPermitProvider permitProvider,
            @NonNull final PeerAgnosticSyncChecks peerAgnosticSyncChecks,
            @NonNull final Duration sleepAfterSync,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final Time time) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.peerId = Objects.requireNonNull(peerId);
        this.synchronizer = Objects.requireNonNull(synchronizer);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.permitProvider = Objects.requireNonNull(permitProvider);
        this.peerAgnosticSyncChecks = Objects.requireNonNull(peerAgnosticSyncChecks);
        this.sleepAfterSync = Objects.requireNonNull(sleepAfterSync);
        this.syncMetrics = Objects.requireNonNull(syncMetrics);
        this.time = Objects.requireNonNull(time);
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

        final boolean isLockAcquired = permitProvider.tryAcquire(peerId);

        if (isLockAcquired) {
            syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
            syncMetrics.outgoingSyncRequestSent();
        }

        return isLockAcquired;
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

        final boolean isLockAcquired = permitProvider.tryAcquire(peerId);

        if (isLockAcquired) {
            syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
            syncMetrics.acceptedSyncRequest();
        }

        return isLockAcquired;
    }

    /**
     * Return the existing permit
     */
    private void returnPermit() {
        permitProvider.returnPermit();

        syncMetrics.updateSyncPermitsAvailable(permitProvider.getNumAvailable());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initiateFailed() {
        returnPermit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acceptFailed() {
        returnPermit();
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

        try {
            synchronizer.synchronize(platformContext, connection);
        } catch (final ParallelExecutionException | SyncException e) {
            if (Utilities.isRootCauseSuppliedType(e, IOException.class)) {
                throw new IOException(e);
            }

            throw new NetworkProtocolException(e);
        } finally {
            returnPermit();

            lastSyncTime = time.now();
        }
    }
}
