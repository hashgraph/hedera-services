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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.SyncPermitProvider;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphSynchronizer;
import com.swirlds.platform.gossip.sync.protocol.PeerAgnosticSyncChecks;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.io.IOException;
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
    private SyncPermitProvider permitProvider;
    private PeerAgnosticSyncChecks peerAgnosticSyncChecks;
    private Duration sleepAfterSync;
    private SyncMetrics syncMetrics;
    private FakeTime time;
    private PlatformContext platformContext;

    @BeforeEach
    void setUp() {
        peerId = new NodeId(1);
        shadowGraphSynchronizer = mock(ShadowGraphSynchronizer.class);
        fallenBehindManager = mock(FallenBehindManager.class);
        permitProvider = new SyncPermitProvider(2, mock(IntakeEventCounter.class));
        sleepAfterSync = Duration.ofMillis(0);
        syncMetrics = mock(SyncMetrics.class);
        time = new FakeTime();

        // Set reasonable defaults. Special cases to be configured in individual tests

        // node is not fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(false);
        // only peer with ID 1 is needed for fallen behind
        Mockito.when(fallenBehindManager.getNeededForFallenBehind()).thenReturn(List.of(new NodeId(1L)));
        // peer agnostic sync checks pass
        peerAgnosticSyncChecks = new PeerAgnosticSyncChecks(List.of(() -> true));

        platformContext = TestPlatformContextBuilder.create().build();
    }

    @Test
    @DisplayName("Protocol should initiate connection")
    void shouldInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol won't initiate connection if cooldown isn't complete")
    void initiateCooldown() {
        assertEquals(2, permitProvider.getNumAvailable());

        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                Duration.ofMillis(100),
                syncMetrics,
                time);

        // do an initial sync, so we can verify that the resulting cooldown period is respected
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, permitProvider.getNumAvailable());
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, permitProvider.getNumAvailable());

        // no time has passed since the previous protocol
        assertFalse(protocol.shouldInitiate());
        assertEquals(2, permitProvider.getNumAvailable());

        // tick part of the way through the cooldown period
        time.tick(Duration.ofMillis(55));

        assertFalse(protocol.shouldInitiate());
        assertEquals(2, permitProvider.getNumAvailable());

        // tick past the end of the cooldown period
        time.tick(Duration.ofMillis(55));

        assertTrue(protocol.shouldInitiate());
        assertEquals(1, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate without a permit")
    void noPermitAvailableToInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        // obtain the only existing permits, so none are available to the protocol
        assertTrue(permitProvider.tryAcquire(peerId));
        assertTrue(permitProvider.tryAcquire(peerId));
        assertEquals(0, permitProvider.getNumAvailable());

        assertFalse(protocol.shouldInitiate());
        assertEquals(0, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate if peer agnostic checks fail")
    void peerAgnosticChecksFailAtInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                // peer agnostic checks fail
                new PeerAgnosticSyncChecks(List.of(() -> false)),
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertFalse(protocol.shouldInitiate());
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate if it has fallen behind")
    void fallenBehindAtInitiate() {
        // node is fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(true);

        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertFalse(protocol.shouldInitiate());
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol initiates if peer is needed for fallen behind")
    void initiateForFallenBehind() {

        // peer *is* needed for fallen behind (by default)
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol initiates if peer is part of critical quorum")
    void initiateForCriticalQuorum() {
        // peer 6 isn't needed for fallen behind, but it *is* in critical quorum (by default)
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                new NodeId(6),
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol should accept connection")
    void shouldAccept() {
        assertEquals(2, permitProvider.getNumAvailable());
        // obtain 1 of the permits, but 1 will still be available to accept
        assertTrue(permitProvider.tryAcquire(peerId));
        assertEquals(1, permitProvider.getNumAvailable());

        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertTrue(protocol.shouldAccept());
        assertEquals(0, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol won't accept connection if cooldown isn't complete")
    void acceptCooldown() {
        assertEquals(2, permitProvider.getNumAvailable());

        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                Duration.ofMillis(100),
                syncMetrics,
                time);

        // do an initial sync, so we can verify that the resulting cooldown period is respected
        assertTrue(protocol.shouldAccept());
        assertEquals(1, permitProvider.getNumAvailable());
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, permitProvider.getNumAvailable());

        // no time has passed since the previous protocol
        assertFalse(protocol.shouldAccept());
        assertEquals(2, permitProvider.getNumAvailable());

        // tick part of the way through the cooldown period
        time.tick(Duration.ofMillis(55));

        assertFalse(protocol.shouldAccept());
        assertEquals(2, permitProvider.getNumAvailable());

        // tick past the end of the cooldown period
        time.tick(Duration.ofMillis(55));

        assertTrue(protocol.shouldAccept());
        assertEquals(1, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't initiate without a permit")
    void noPermitAvailableToAccept() {
        assertEquals(2, permitProvider.getNumAvailable());

        // waste both available permits
        assertTrue(permitProvider.tryAcquire(peerId));
        assertTrue(permitProvider.tryAcquire(peerId));

        assertEquals(0, permitProvider.getNumAvailable());

        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertFalse(protocol.shouldAccept());
        assertEquals(0, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't accept if peer agnostic checks fail")
    void peerAgnosticChecksFailAtAccept() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                // peer agnostic checks fail
                new PeerAgnosticSyncChecks(List.of(() -> false)),
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertFalse(protocol.shouldAccept());
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol doesn't accept if it has fallen behind")
    void fallenBehindAtAccept() {
        // node is fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(true);

        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertFalse(protocol.shouldAccept());
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Permit closes after failed accept")
    void permitClosesAfterFailedAccept() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertTrue(protocol.shouldAccept());
        assertEquals(1, permitProvider.getNumAvailable());
        protocol.acceptFailed();
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Permit closes after failed initiate")
    void permitClosesAfterFailedInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, permitProvider.getNumAvailable());
        protocol.initiateFailed();
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol runs successfully when initiating")
    void successfulInitiatedProtocol() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        protocol.shouldInitiate();
        assertEquals(1, permitProvider.getNumAvailable());
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Protocol runs successfully when accepting")
    void successfulAcceptedProtocol() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertEquals(2, permitProvider.getNumAvailable());
        protocol.shouldAccept();
        assertEquals(1, permitProvider.getNumAvailable());
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("ParallelExecutionException is caught and rethrown as NetworkProtocolException")
    void rethrowParallelExecutionException()
            throws ParallelExecutionException, IOException, SyncException, InterruptedException {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        // mock synchronize to throw a ParallelExecutionException
        Mockito.when(shadowGraphSynchronizer.synchronize(any(), any()))
                .thenThrow(new ParallelExecutionException(mock(Throwable.class)));

        assertEquals(2, permitProvider.getNumAvailable());
        protocol.shouldAccept();
        assertEquals(1, permitProvider.getNumAvailable());

        assertThrows(NetworkProtocolException.class, () -> protocol.runProtocol(mock(Connection.class)));

        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("Exception with IOException as root cause is caught and rethrown as IOException")
    void rethrowRootCauseIOException()
            throws ParallelExecutionException, IOException, SyncException, InterruptedException {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        // mock synchronize to throw a ParallelExecutionException with root cause being an IOException
        Mockito.when(shadowGraphSynchronizer.synchronize(any(), any()))
                .thenThrow(new ParallelExecutionException(new IOException()));

        assertEquals(2, permitProvider.getNumAvailable());
        protocol.shouldAccept();
        assertEquals(1, permitProvider.getNumAvailable());

        assertThrows(IOException.class, () -> protocol.runProtocol(mock(Connection.class)));

        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("SyncException is caught and rethrown as NetworkProtocolException")
    void rethrowSyncException() throws ParallelExecutionException, IOException, SyncException, InterruptedException {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        // mock synchronize to throw a SyncException
        Mockito.when(shadowGraphSynchronizer.synchronize(any(), any())).thenThrow(new SyncException(""));

        assertEquals(2, permitProvider.getNumAvailable());
        protocol.shouldAccept();
        assertEquals(1, permitProvider.getNumAvailable());

        assertThrows(NetworkProtocolException.class, () -> protocol.runProtocol(mock(Connection.class)));

        assertEquals(2, permitProvider.getNumAvailable());
    }

    @Test
    @DisplayName("acceptOnSimultaneousInitiate should return true")
    void acceptOnSimultaneousInitiate() {
        final SyncProtocol protocol = new SyncProtocol(
                platformContext,
                peerId,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                peerAgnosticSyncChecks,
                sleepAfterSync,
                syncMetrics,
                time);

        assertTrue(protocol.acceptOnSimultaneousInitiate());
    }
}
