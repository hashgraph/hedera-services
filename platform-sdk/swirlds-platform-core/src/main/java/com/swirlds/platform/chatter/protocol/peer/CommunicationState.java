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

package com.swirlds.platform.chatter.protocol.peer;

import static com.swirlds.platform.chatter.protocol.peer.CommunicationState.CommState.CHATTER_RUNNING;
import static com.swirlds.platform.chatter.protocol.peer.CommunicationState.CommState.NO_PROTOCOL;
import static com.swirlds.platform.chatter.protocol.peer.CommunicationState.CommState.SYNC_RUNNING;
import static com.swirlds.platform.chatter.protocol.peer.CommunicationState.SyncState.IN_SYNC;
import static com.swirlds.platform.chatter.protocol.peer.CommunicationState.SyncState.OUT_OF_SYNC;
import static com.swirlds.platform.chatter.protocol.peer.CommunicationState.SyncState.SUSPENDED;

import com.swirlds.logging.LogMarker;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tracks the state of communication with a chatter peer
 */
public class CommunicationState {
    private static final Logger logger = LogManager.getLogger(CommunicationState.class);

    /** the state of synchronization with the peer */
    protected final AtomicReference<SyncState> syncState;
    /** the state of chatter communication with this peer */
    protected final AtomicReference<CommState> commState;

    public CommunicationState() {
        syncState = new AtomicReference<>(OUT_OF_SYNC);
        commState = new AtomicReference<>(NO_PROTOCOL);
    }

    /**
     * @return true if we are out of sync with this peer
     */
    public boolean isOutOfSync() {
        return isSyncState(OUT_OF_SYNC);
    }

    /**
     * @return true if chatter is suspended
     */
    public boolean isSuspended() {
        return isSyncState(SUSPENDED);
    }

    /**
     * notifies the state that chatter sync has started
     */
    public void chatterSyncStarted() {
        // if we are in sync as we begin syncing, switch to OUT_OF_SYNC and start again
        syncState.compareAndSet(IN_SYNC, OUT_OF_SYNC);
        setAndCheck(commState, SYNC_RUNNING, NO_PROTOCOL);
    }

    /**
     * notifies the state that chatter sync is starting phase 3
     */
    public void chatterSyncStartingPhase3() {
        // chatter sync can only transition from OUT_OF_SYNC to IN_SYNC
        syncState.compareAndSet(OUT_OF_SYNC, IN_SYNC);
    }

    /**
     * notifies the state that chatter sync has ended successfully
     */
    public void chatterSyncSucceeded() {
        setAndCheck(commState, NO_PROTOCOL, SYNC_RUNNING);
    }

    /**
     * notifies the state that chatter sync has failed
     */
    public void chatterSyncFailed() {
        syncState.compareAndSet(IN_SYNC, OUT_OF_SYNC);
        setAndCheck(commState, NO_PROTOCOL, SYNC_RUNNING);
    }

    /**
     * @return true if we should be chattering with this peer
     */
    public boolean shouldChatter() {
        return isSyncState(IN_SYNC);
    }

    /**
     * Notifies the state that chatter has started
     */
    public void chatterStarted() {
        setAndCheck(commState, CHATTER_RUNNING, NO_PROTOCOL);
    }

    /**
     * notifies the state that chatter has ended
     */
    public void chatterEnded() {
        syncState.compareAndSet(IN_SYNC, OUT_OF_SYNC);
        // this can be called twice in case there is a socket exception, so we don't call setAndCheck() for it
        commState.set(NO_PROTOCOL);
    }

    /**
     * notifies the state that peer has ended chatter
     */
    public void receivedEnd() {
        syncState.compareAndSet(IN_SYNC, OUT_OF_SYNC);
    }

    /**
     * notifies the state this peer's queue has overflown
     */
    public void queueOverFlow() {
        syncState.compareAndSet(IN_SYNC, OUT_OF_SYNC);
    }

    /**
     * Suspends chatter communication
     */
    public void suspend() {
        syncState.set(SUSPENDED);
    }

    /**
     * Unsuspends chatter communication
     */
    public void unsuspend() {
        syncState.compareAndSet(SUSPENDED, OUT_OF_SYNC);
    }

    /**
     * Resets the state to its initial value
     */
    public void reset() {
        syncState.set(OUT_OF_SYNC);
        commState.set(NO_PROTOCOL);
    }

    /**
     * @return true if either chatter or chatter sync are currently running with this peer
     */
    public boolean isAnyProtocolRunning() {
        return commState.get() != NO_PROTOCOL;
    }

    /**
     * @return true if chatter sync is in progress
     */
    public boolean isChatterSyncing() {
        return commState.get() == SYNC_RUNNING;
    }

    /**
     * @return true if chattering is in progress
     */
    public boolean isChattering() {
        return commState.get() == CHATTER_RUNNING;
    }

    private boolean isSyncState(final SyncState syncState) {
        return this.syncState.get() == syncState;
    }

    private static <T> void setAndCheck(final AtomicReference<T> ref, final T setTo, final T expectedPrevious) {
        final T prev = ref.getAndSet(setTo);
        if (!prev.equals(expectedPrevious)) {
            logger.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "ChatterState: Unexpected state {} when setting to {}",
                    prev,
                    setTo,
                    new Exception("for stack trace"));
        }
    }

    protected enum SyncState {
        /** chatter is not ongoing and cannot start until the nodes sync */
        OUT_OF_SYNC,
        /** the nodes are in sync and chatter can run */
        IN_SYNC,
        /** communication is suspended, we will not try to establish it */
        SUSPENDED
    }

    /**
     * The state of communication with a peer
     */
    protected enum CommState {
        /** No protocol is currently running */
        NO_PROTOCOL,
        /** Chatter sync is currently running */
        SYNC_RUNNING,
        /** Chatter is currently running */
        CHATTER_RUNNING
    }
}
