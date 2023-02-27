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

package com.swirlds.platform;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;
import static com.swirlds.logging.LogMarker.SOCKET_EXCEPTIONS;
import static com.swirlds.logging.LogMarker.SYNC_START;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import com.swirlds.common.threading.locks.locked.MaybeLockedResource;
import com.swirlds.logging.payloads.ReconnectPeerInfoPayload;
import com.swirlds.platform.network.ConnectionManager;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectUtils;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import java.io.IOException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

/**
 * A thread can run this to repeatedly initiate syncs with other members.
 */
class SyncCaller implements Runnable {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(SyncCaller.class);
    /** ID number for this caller thread (0 is the first created by this platform, 1 next, etc) */
    final int callerNumber;
    /** the Platform object that is using this to call other members */
    private final SwirldsPlatform platform;
    /** the address book with all member IP addresses and ports, etc. */
    private final AddressBook addressBook;
    /** the member ID for self */
    private final NodeId selfId;

    private final ReconnectHelper reconnectHelper;
    private final SignedStateValidator signedStateValidator;
    private final PlatformMetrics platformMetrics;

    /**
     * The platform instantiates this, and gives it the self ID number, plus other info that will be useful to it. The
     * platform can then create a thread to call run(). It will then repeatedly initiate syncs with others.
     *
     * @param platform     the platform using this caller
     * @param addressBook  the address book listing who can be called
     * @param selfId       the ID number for self /** ID number for this caller thread (0 is the first created by this
     *                     platform, 1 next, etc)
     * @param callerNumber 0 for the first caller thread created by this platform, 1 for the next, etc
     */
    public SyncCaller(
            final SwirldsPlatform platform,
            final AddressBook addressBook,
            final NodeId selfId,
            final int callerNumber,
            final ReconnectHelper reconnectHelper,
            final SignedStateValidator signedStateValidator,
            final PlatformMetrics platformMetrics) {
        this.platform = platform;
        this.addressBook = addressBook;
        this.selfId = selfId;
        this.callerNumber = callerNumber;
        this.reconnectHelper = reconnectHelper;
        this.signedStateValidator = signedStateValidator;
        this.platformMetrics = platformMetrics;
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
                final long otherId = callRequestSync();
                if (otherId >= 0) { // successful sync
                    failedAttempts = 0;
                    sleepAfterSync();
                } else {
                    failedAttempts++;
                    if (failedAttempts >= Settings.getInstance().getCallerSkipsBeforeSleep()) {
                        failedAttempts = 0;
                        try {
                            // Necessary to slow down the attempts after N failures
                            Thread.sleep(Settings.getInstance().getSleepCallerSkips());
                            platformMetrics.incrementSleep1perSecond();
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
     * If configured, sleeps a defined amount of time after a successful sync
     *
     * @throws InterruptedException if the thread is interrupted
     */
    private void sleepAfterSync() throws InterruptedException {
        if (platform.getSleepAfterSync() > 0) {
            Thread.sleep(platform.getSleepAfterSync());
        }
    }

    /**
     * Chose another member at random, request a sync with them, and perform one sync if they accept the request. A -1
     * is returned if the sync did not happen for some reason, such as the chosen member wasn't connected, or had a
     * communication error, or was already syncing by calling self, or the lock was still held by the heartbeat thread,
     * or some other reason.
     *
     * @return member ID of the member the attempted sync was with, or -1 if no sync happened
     */
    private int callRequestSync() {
        int otherId = -1; // the ID of the member that I am syncing with now
        try { // catch any exceptions, log them, and ignore them
            if (platform.getSyncManager().hasFallenBehind()) {
                // we will not sync if we have fallen behind
                if (callerNumber == 0) {
                    // caller number 0 will do the reconnect, the others will wait
                    final boolean reconnected = doReconnect();
                    // if reconnect failed, we will start over
                    if (!reconnected) {
                        return -1;
                    }
                    // if reconnect succeeded, we should continue with a sync
                } else {
                    return -1;
                }
                logger.debug(
                        RECONNECT.getMarker(),
                        "`callRequestSync` : node {} fell behind and has reconnected, thread ID callerNumber = {}",
                        selfId,
                        callerNumber);
            }

            // check transThrottle, if there is no reason to call, don't call anyone
            if (!platform.getSyncManager().transThrottle()) {
                return -1;
            }

            // check with sync manager for any reasons not to sync
            if (!platform.getSyncManager().shouldInitiateSync()) {
                return -1;
            }

            if (addressBook.getSize() == 1 && callerNumber == 0) {
                platform.checkPlatformStatus();
                // Only one member exists (self), so create an event and add it to the hashgraph.
                // Only one caller is allowed to do this (caller number 0).
                // This is like syncing with self, and then creating an event with otherParent being self.

                // self is the only member, so create an event for just this one transaction,
                // and immediately put it into the hashgraph. No syncing is needed.
                platform.getEventTaskCreator()
                        .createEvent(
                                selfId.getId() /*selfId assumed to be main*/); // otherID (so self will count as the
                // "other")
                Thread.sleep(50);
                platform.getSyncManager().successfulSync();
                // selfId assumed to be main
                return selfId.getIdAsInt(); // say that self just "synced" with self, and created an event for it
            }

            if (addressBook.getSize() <= 1) { // if there is no one to sync with, don't sync
                return -1;
            }

            // the sync manager will tell us who we need to call
            final List<Long> nodeList = platform.getSyncManager().getNeighborsToCall();

            // the array is sorted in ascending or from highest to lowest priority, so we go through the array and
            // try to
            // call each node until we are successful
            boolean syncAccepted = false;
            for (final Long aLong : nodeList) {
                otherId = aLong.intValue();
                // otherId is now the member to call

                logger.debug(SYNC_START.getMarker(), "{} about to call {} (connection looks good)", selfId, otherId);

                try (final MaybeLocked lock =
                        platform.getSimultaneousSyncThrottle().trySync(otherId, true)) {
                    // Try to get both locks. If either is unavailable, then try the next node. Never block.
                    if (!lock.isLockAcquired()) {
                        continue;
                    }

                    try (final MaybeLockedResource<ConnectionManager> resource =
                            platform.getSharedConnectionLocks().tryLockConnection(NodeId.createMain(otherId))) {
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
                            syncAccepted = platform.getShadowGraphSynchronizer().synchronize(conn);
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
                                    platform.getSelfId(),
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
                                        platform.getSelfId(),
                                        otherId,
                                        formattedException);
                            } else {
                                logger.error(
                                        marker,
                                        "! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} "
                                                + "listening for {}:",
                                        platform.getSelfId(),
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
                platform.getSyncManager().successfulSync();
                return otherId;
            } else {
                return -1;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn(
                    EXCEPTION.getMarker(),
                    "! SyncCaller.sync Interrupted (so incrementing iCSyncPerSec) while {} listening for {}:",
                    platform.getSelfId(),
                    otherId,
                    e);

            return -1;
        } catch (final Exception e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "! SyncCaller.sync Exception (so incrementing iCSyncPerSec) while {} listening for {}:",
                    platform.getSelfId(),
                    otherId,
                    e);
            return -1;
        }
    }

    /**
     * Called to initiate the reconnect attempt by the {@link #callRequestSync()} method.
     *
     * @return true if the reconnect attempt completed successfully; otherwise false
     */
    private boolean doReconnect() {
        logger.info(
                RECONNECT.getMarker(), "{} has fallen behind, will wait for all syncs to finish", platform.getSelfId());

        // clear handlers and transaction pool to make sure that the pre-consensus event queue (q1)
        // and consensus round queue (q2) are empty during reconnect
        reconnectHelper.prepareForReconnect();

        final List<Long> reconnectNeighbors = platform.getSyncManager().getNeighborsForReconnect();
        logger.info(
                RECONNECT.getMarker(),
                "{} has fallen behind, will try to reconnect with {}",
                platform::getSelfId,
                reconnectNeighbors::toString);

        return doReconnect(reconnectNeighbors);
    }

    private boolean doReconnect(final List<Long> reconnectNeighbors) {

        final ReconnectPeerInfoPayload peerInfo = new ReconnectPeerInfoPayload();

        for (final Long neighborId : reconnectNeighbors) {
            final SignedState signedState;
            // try to get the lock, it should be available if we have fallen behind
            try (final MaybeLockedResource<ConnectionManager> resource =
                    platform.getSharedConnectionLocks().tryLockConnection(NodeId.createMain(neighborId))) {
                if (!resource.isLockAcquired()) {
                    peerInfo.addPeerInfo(neighborId, "failed to acquire lock, blocked by heartbeat thread");
                    continue;
                }
                final Connection conn = resource.getResource().getConnection();
                if (conn == null || !conn.connected()) {
                    // if we're not connected, try someone else
                    peerInfo.addPeerInfo(neighborId, "peer unreachable, unable to establish connection");
                    continue;
                }
                if (!ReconnectUtils.isNodeReadyForReconnect(conn)) {
                    // The other node was unwilling to help this node to reconnect or the connection was broken
                    peerInfo.addPeerInfo(neighborId, "peer declined request to reconnect");
                    continue;
                }
                signedState = reconnectHelper.receiveSignedState(conn, signedStateValidator);
                if (signedState == null) {
                    peerInfo.addPeerInfo(neighborId, "no signed state received");
                    continue;
                }
            } catch (final Exception e) {
                peerInfo.addPeerInfo(neighborId, "exception occurred: " + e.getMessage());
                // if we failed to receive a state from this node, we will try the next one
                continue;
            }
            return reconnectHelper.loadSignedState(signedState);
        }

        logger.info(
                RECONNECT.getMarker(),
                "{} `doReconnect` : finished, could not reconnect with any peer, reasons:\n{}",
                platform.getSelfId(),
                peerInfo);

        // if no nodes were found to reconnect with, return false
        return false;
    }
}
