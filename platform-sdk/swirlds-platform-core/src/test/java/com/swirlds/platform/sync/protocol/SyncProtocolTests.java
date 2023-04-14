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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.SyncPermitProvider;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import com.swirlds.platform.Connection;
import com.swirlds.platform.components.CriticalQuorum;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.sync.FallenBehindManager;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link SyncProtocol}
 */
@DisplayName("Sync Protocol Tests")
class SyncProtocolTests {
    private NodeId peerId;
    private ShadowGraphSynchronizer shadowGraphSynchronizer;
    private FallenBehindManager fallenBehindManager;
    private SyncPermitProvider outgoingPermitProvider;
    private SyncPermitProvider incomingPermitProvider;
    private CriticalQuorum criticalQuorum;
    private PeerAgnosticSyncChecks peerAgnosticSyncChecks;
    private Duration sleepAfterSync;
    private SyncMetrics syncMetrics;

    @BeforeEach
    void setup() {
        peerId = new NodeId(false, 1);
        shadowGraphSynchronizer = mock(ShadowGraphSynchronizer.class);
        fallenBehindManager = mock(FallenBehindManager.class);
        outgoingPermitProvider = new SyncPermitProvider(1);
        incomingPermitProvider = new SyncPermitProvider(2);
        criticalQuorum = mock(CriticalQuorum.class);
        sleepAfterSync = Duration.ofMillis(0);
        syncMetrics = mock(SyncMetrics.class);

        // Set reasonable defaults. Special cases to be configured in individual tests

        // node is not fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(false);
        // only peer with ID 1 is needed for fallen behind
        Mockito.when(fallenBehindManager.getNeededForFallenBehind()).thenReturn(List.of(1L));
        // all nodes are in critical quorum
        Mockito.when(criticalQuorum.isInCriticalQuorum(Mockito.anyLong())).thenReturn(true);
        // peer agnostic sync checks pass
        peerAgnosticSyncChecks = new PeerAgnosticSyncChecks(List.of(() -> true));
    }

    @Test
    @DisplayName("Protocol should initiate connection")
    void shouldInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(0, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate without a permit")
    void noPermitAvailableToInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        // obtain the only existing permit, so it isn't available to the protocol
        final MaybeLocked wastedPermit = outgoingPermitProvider.tryAcquire();
        assertTrue(wastedPermit.isLockAcquired());
        assertEquals(0, outgoingPermitProvider.getNumAvailable());

        assertFalse(protocol.shouldInitiate());
        assertEquals(0, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate if peer agnostic checks fail")
    void peerAgnosticChecksFailAtInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                // peer agnostic checks fail
                new PeerAgnosticSyncChecks(List.of(() -> false)),
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        assertFalse(protocol.shouldInitiate());
        assertEquals(1, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate if it has fallen behind")
    void fallenBehindAtInitiate() {
        // node is fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(true);

        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        assertFalse(protocol.shouldInitiate());
        assertEquals(1, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate if there is no reason to, even if there isn't a reason not to")
    void noReasonToInitiate() {
        // peer isn't in critical quorum
        Mockito.when(criticalQuorum.isInCriticalQuorum(Mockito.anyLong())).thenReturn(false);

        // peer 6 isn't needed for fallen behind
        final SyncProtocol protocol = new SyncProtocol(
                new NodeId(false, 6),
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        assertFalse(protocol.shouldInitiate());
        assertEquals(1, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol initiates if peer is needed for fallen behind")
    void initiateForFallenBehind() {
        // peer isn't in critical quorum
        Mockito.when(criticalQuorum.isInCriticalQuorum(Mockito.anyLong())).thenReturn(false);

        // peer *is* needed for fallen behind (by default)
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(0, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol initiates if peer is part of critical quorum")
    void initiateForCriticalQuorum() {
        // peer 6 isn't needed for fallen behind, but it *is* in critical quorum (by default)
        final SyncProtocol protocol = new SyncProtocol(
                new NodeId(false, 6),
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(0, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol should accept connection")
    void shouldAccept() {
        assertEquals(2, incomingPermitProvider.getNumAvailable());
        // obtain the 1 of the permits, but 1 will still be available to accept
        final MaybeLocked wastedPermit = incomingPermitProvider.tryAcquire();
        assertTrue(wastedPermit.isLockAcquired());
        assertEquals(1, incomingPermitProvider.getNumAvailable());

        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertTrue(protocol.shouldAccept());
        assertEquals(0, incomingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate without a permit")
    void noPermitAvailableToAccept() {
        assertEquals(2, incomingPermitProvider.getNumAvailable());

        // waste both available permits
        final MaybeLocked wastedPermit1 = incomingPermitProvider.tryAcquire();
        assertTrue(wastedPermit1.isLockAcquired());
        final MaybeLocked wastedPermit2 = incomingPermitProvider.tryAcquire();
        assertTrue(wastedPermit2.isLockAcquired());

        assertEquals(0, incomingPermitProvider.getNumAvailable());

        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertFalse(protocol.shouldAccept());
        assertEquals(0, incomingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't accept if peer agnostic checks fail")
    void peerAgnosticChecksFailAtAccept() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                // peer agnostic checks fail
                new PeerAgnosticSyncChecks(List.of(() -> false)),
                sleepAfterSync,
                syncMetrics);

        assertEquals(2, incomingPermitProvider.getNumAvailable());
        assertFalse(protocol.shouldAccept());
        assertEquals(2, incomingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't accept if it has fallen behind")
    void fallenBehindAtAccept() {
        // node is fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(true);

        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(2, incomingPermitProvider.getNumAvailable());
        assertFalse(protocol.shouldAccept());
        assertEquals(2, incomingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Permit closes after failed initiate")
    void permitClosesAfterFailedInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(0, outgoingPermitProvider.getNumAvailable());
        protocol.initiateFailed();
        assertEquals(1, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol runs successfully when initiating")
    void successfulInitiatedProtocol() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(1, outgoingPermitProvider.getNumAvailable());
        protocol.shouldInitiate();
        assertEquals(0, outgoingPermitProvider.getNumAvailable());
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(1, outgoingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol runs successfully when accepting")
    void successfulAcceptedProtocol() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertEquals(2, incomingPermitProvider.getNumAvailable());
        protocol.shouldAccept();
        assertEquals(1, incomingPermitProvider.getNumAvailable());
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, incomingPermitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't run without first obtaining a permit")
    void failedProtocol() {
        final SyncProtocol protocol = new SyncProtocol(
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                outgoingPermitProvider,
                incomingPermitProvider,
                criticalQuorum,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics);

        assertThrows(NetworkProtocolException.class, () -> protocol.runProtocol(mock(Connection.class)));
    }
}
