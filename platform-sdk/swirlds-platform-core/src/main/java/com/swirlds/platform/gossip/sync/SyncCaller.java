/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync;

import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.SYNC_START;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import com.swirlds.common.threading.locks.locked.MaybeLockedResource;
import com.swirlds.logging.payloads.ReconnectPeerInfoPayload;
import com.swirlds.platform.Settings;
import com.swirlds.platform.components.EventTaskCreator;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.gossip.shadowgraph.SimultaneousSyncThrottle;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.unidirectional.SharedConnectionLocks;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectUtils;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

/**
 * A thread can run this to repeatedly initiate syncs with other members.
 */
public class SyncCaller implements Runnable {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SyncCaller.class);
    /** ID number for this caller thread (0 is the first created by this platform, 1 next, etc) */
    final int callerNumber;
    /** the address book with all member IP addresses and ports, etc. */
    private final AddressBook addressBook;
    /** the member ID for self */
    private final NodeId selfId;

    private final ReconnectHelper reconnectHelper;
    private final SignedStateValidator signedStateValidator;
    private final SimultaneousSyncThrottle simultaneousSyncThrottle;
    private final SyncManagerImpl syncManager;
    private final SharedConnectionLocks sharedConnectionLocks;
    private final EventTaskCreator eventTaskCreator;
    private final Runnable updatePlatformStatus;
    private ShadowGraphSynchronizer syncShadowgraphSynchronizer;

    private static final SpeedometerMetric.Config SLEEP_1_PER_SECOND_CONFIG = new SpeedometerMetric.Config(
                    INTERNAL_CATEGORY, "sleep1/sec")
            .withDescription("sleeps per second because caller thread had too many failed connects");
    private final SpeedometerMetric sleep1perSecond;

    /**
     * The platform instantiates this, and gives it the self ID number, plus other info that will be useful to it. The
     * platform can then create a thread to call run(). It will then repeatedly initiate syncs with others.
     *
     * @param platformContext             the platform context
     * @param addressBook                 the address book listing who can be called
     * @param selfId                      the ID number for self /** ID number for this caller thread (0 is the first
     *                                    created by this platform, 1 next, etc)
     * @param callerNumber                0 for the first caller thread created by this platform, 1 for the next, etc
     * @param reconnectHelper             assists with the reconnect workflow
     * @param signedStateValidator        validates states received via reconnect
     * @param simultaneousSyncThrottle    the throttle to use to limit the number of simultaneous syncs
     * @param syncManager                 the sync manager
     * @param sharedConnectionLocks       the shared connection locks
     * @param eventTaskCreator            responsible for scheduling the creation of events
     * @param updatePlatformStatus        calling this method causes the platform to re-evaluate its status
     * @param syncShadowgraphSynchronizer the shadowgraph synchronizer
     */
    public SyncCaller(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            final int callerNumber,
            @NonNull final ReconnectHelper reconnectHelper,
            @NonNull final SignedStateValidator signedStateValidator,
            @NonNull final SimultaneousSyncThrottle simultaneousSyncThrottle,
            @NonNull final SyncManagerImpl syncManager,
            @NonNull final SharedConnectionLocks sharedConnectionLocks,
            @NonNull final EventTaskCreator eventTaskCreator,
            @NonNull final Runnable updatePlatformStatus,
            @NonNull final ShadowGraphSynchronizer syncShadowgraphSynchronizer) {
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.callerNumber = callerNumber;
        this.reconnectHelper = Objects.requireNonNull(reconnectHelper);
        this.signedStateValidator = Objects.requireNonNull(signedStateValidator);
        this.simultaneousSyncThrottle = Objects.requireNonNull(simultaneousSyncThrottle);
        this.syncManager = Objects.requireNonNull(syncManager);
        this.sharedConnectionLocks = Objects.requireNonNull(sharedConnectionLocks);
        this.eventTaskCreator = Objects.requireNonNull(eventTaskCreator);
        this.updatePlatformStatus = Objects.requireNonNull(updatePlatformStatus);
        this.syncShadowgraphSynchronizer = Objects.requireNonNull(syncShadowgraphSynchronizer);

        sleep1perSecond = platformContext.getMetrics().getOrCreate(SLEEP_1_PER_SECOND_CONFIG);
    }

    /**
     * repeatedly call others and sync with them
     */
    @Override
    public void run() {
        int failedAttempts = 0;
        while (true) { // loop forever until the user quits the browser
            try {
                // choose a member at random, and sync with them
                final NodeId otherId = callRequestSync();
                if (otherId != null) { // successful sync
                    failedAttempts = 0;
                } else {
                    failedAttempts++;
                    if (failedAttempts >= Settings.getInstance().getCallerSkipsBeforeSleep()) {
                        failedAttempts = 0;
                        try {
                            // Necessary to slow down the attempts after N failures
                            Thread.sleep(Settings.getInstance().getSleepCallerSkips());
                            sleep1perSecond.cycle();
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return;
                        } catch (final Exception ex) {
                            // Suppress any exception thrown by the stats update
                        }
                    }
                }
            } catch (final Exception e) {
                failedAttempts++;
                logger.error(EXCEPTION.getMarker(), "SyncCaller.run error", e);
            }
        }
    }

    /**
     * Chose another member at random, request a sync with them, and perform one sync if they accept the request. A null
     * is returned if the sync did not happen for some reason, such as the chosen member wasn't connected, or had a
     * communication error, or was already syncing by calling self, or the lock was still held by the heartbeat thread,
     * or some other reason.
     *
     * @return member ID of the member the attempted sync was with, or null if no sync happened
     */
    @Nullable
    private NodeId callRequestSync() {
        NodeId otherId = null; // the ID of the member that I am syncing with now
        try { // catch any exceptions, log them, and ignore them
            if (syncManager.hasFallenBehind()) {
                // we will not sync if we have fallen behind
                if (callerNumber == 0) {
                    // caller number 0 will do the reconnect, the others will wait
                    final boolean reconnected = doReconnect();
                    // if reconnect failed, we will start over
                    if (!reconnected) {
                        return null;
                    }
                    // if reconnect succeeded, we should continue with a sync
                } else {
                    return null;
                }
                logger.debug(
                        RECONNECT.getMarker(),
                        "`callRequestSync` : node {} fell behind and has reconnected, thread ID callerNumber = {}",
                        selfId,
                        callerNumber);
            }

            // check with sync manager for any reasons not to sync
            if (!syncManager.shouldInitiateSync()) {
                return null;
            }

            if (addressBook.getSize() == 1 && callerNumber == 0) {
                updatePlatformStatus.run();
                // Only one member exists (self), so create an event and add it to the hashgraph.
                // Only one caller is allowed to do this (caller number 0).
                // This is like syncing with self, and then creating an event with otherParent being self.

                // self is the only member, so create an event for just this one transaction,
                // and immediately put it into the hashgraph. No syncing is needed.

                eventTaskCreator.createEvent(
                        selfId /*selfId assumed to be main*/); // otherID (so self will count as the

                // "other")
                Thread.sleep(50);
                // selfId assumed to be main
                return selfId; // say that self just "synced" with self, and created an event for it
            }

            if (addressBook.getSize() <= 1) { // if there is no one to sync with, don't sync
                return null;
            }

            // the sync manager will tell us who we need to call
            final List<NodeId> nodeList = syncManager.getNeighborsToCall();

            // the array is sorted in ascending or from highest to lowest priority, so we go through the array and
            // try to
            // call each node until we are successful
            boolean syncAccepted = false;
            for (final NodeId nodeId : nodeList) {
                otherId = nodeId;
                // otherId is now the member to call

                logger.debug(SYNC_START.getMarker(), "{} about to call {} (connection looks good)", selfId, otherId);

                try (final MaybeLocked lock = simultaneousSyncThrottle.trySync(otherId, true)) {
                    // Try to get both locks. If either is unavailable, then try the next node. Never block.
                    if (!lock.isLockAcquired()) {
                        continue;
                    }

                    try (final MaybeLockedResource<ConnectionManager> resource =
                            sharedConnectionLocks.tryLockConnection(otherId)) {
                        if (!resource.isLockAcquired()) {
                            continue;
                        }
                        // check if we have a connection
                        final Connection conn = resource.getResource().getConnection();
                        if (conn == null || !conn.connected()) {
                            continue;
                        }
                        // try to initiate a sync. If they accept the request, then sync
                        try {
                            syncAccepted = syncShadowgraphSynchronizer.synchronize(conn);
                            if (syncAccepted) {
                                break;
                            }

                        } catch (final IOException e) {
                            String formattedException = NetworkUtils.formatException(e);
                            // IOException covers both SocketTimeoutException and EOFException, plus more
                            logger.error(
                                    SOCKET_EXCEPTIONS.getMarker(),
                                    "SyncCaller.sync SocketException (so incrementing iCSyncPerSec) while {} "
                                            + "listening for {}: {}",
                                    selfId,
                                    otherId,
                                    formattedException);

                            // close the connection, don't reconnect until needed.
                            conn.disconnect();
                        } catch (final Exception e) {
                            // we use a different marker depending on what the root cause is
                            Marker marker = NetworkUtils.determineExceptionMarker(e);
                            if (SOCKET_EXCEPTIONS.getMarker().equals(marker)) {
                                String formattedException = NetworkUtils.formatException(e);
                                logger.error(
                                        marker,
                                        "! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} "
                                                + "listening for {}: {}",
                                        selfId,
                                        otherId,
                                        formattedException);
                            } else {
                                logger.error(
                                        marker,
                                        "! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} "
                                                + "listening for {}:",
                                        selfId,
                                        otherId,
                                        e);
                            }
                            // close the connection, don't reconnect until needed.
                            conn.disconnect();
                        }
                    }
                }
            }

            if (syncAccepted) {
                return otherId;
            } else {
                return null;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn(
                    EXCEPTION.getMarker(),
                    "! SyncCaller.sync Interrupted (so incrementing iCSyncPerSec) while {} listening for {}:",
                    selfId,
                    otherId,
                    e);

            return null;
        } catch (final Exception e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} listening for {}:",
                    selfId,
                    otherId,
                    e);
            return null;
        }
    }

    /**
     * Called to initiate the reconnect attempt by the {@link #callRequestSync()} method.
     *
     * @return true if the reconnect attempt completed successfully; otherwise false
     */
    private boolean doReconnect() {
        logger.info(RECONNECT.getMarker(), "{} has fallen behind, will wait for all syncs to finish", selfId);

        // clear handlers and transaction pool to make sure that the pre-consensus event queue (q1)
        // and consensus round queue (q2) are empty during reconnect
        reconnectHelper.prepareForReconnect();

        final List<NodeId> reconnectNeighbors = syncManager.getNeighborsForReconnect();
        logger.info(
                RECONNECT.getMarker(),
                "{} has fallen behind, will try to reconnect with {}",
                selfId,
                reconnectNeighbors);

        return doReconnect(reconnectNeighbors);
    }

    private boolean doReconnect(final List<NodeId> reconnectNeighbors) {

        final ReconnectPeerInfoPayload peerInfo = new ReconnectPeerInfoPayload();

        for (final NodeId neighborId : reconnectNeighbors) {
            // try to get the lock, it should be available if we have fallen behind
            try (final MaybeLockedResource<ConnectionManager> resource =
                    sharedConnectionLocks.tryLockConnection(neighborId)) {
                if (!resource.isLockAcquired()) {
                    peerInfo.addPeerInfo(neighborId.id(), "failed to acquire lock, blocked by heartbeat thread");
                    continue;
                }
                final Connection conn = resource.getResource().getConnection();
                if (conn == null || !conn.connected()) {
                    // if we're not connected, try someone else
                    peerInfo.addPeerInfo(neighborId.id(), "peer unreachable, unable to establish connection");
                    continue;
                }
                if (!ReconnectUtils.isNodeReadyForReconnect(conn)) {
                    // The other node was unwilling to help this node to reconnect or the connection was broken
                    peerInfo.addPeerInfo(neighborId.id(), "peer declined request to reconnect");
                    continue;
                }
                try (final ReservedSignedState reservedState =
                        reconnectHelper.receiveSignedState(conn, signedStateValidator)) {

                    if (reservedState.isNull()) {
                        peerInfo.addPeerInfo(neighborId.id(), "no signed state received");
                        continue;
                    }
                    return reconnectHelper.loadSignedState(reservedState.get());
                }
            } catch (final Exception e) {
                peerInfo.addPeerInfo(neighborId.id(), "exception occurred: " + e.getMessage());
                // if we failed to receive a state from this node, we will try the next one
            }
        }

        logger.info(
                RECONNECT.getMarker(),
                "{} `doReconnect` : finished, could not reconnect with any peer, reasons:\n{}",
                selfId,
                peerInfo);

        // if no nodes were found to reconnect with, return false
        return false;
    }
}
