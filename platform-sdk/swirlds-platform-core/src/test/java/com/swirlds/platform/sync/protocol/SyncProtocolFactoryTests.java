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

package com.swirlds.platform.sync.protocol;

import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.platform.system.status.PlatformStatus.BEHIND;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.permits.SyncPermitProvider;
import com.swirlds.platform.gossip.shadowgraph.ShadowgraphSynchronizer;
import com.swirlds.platform.gossip.sync.protocol.SyncProtocol;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.network.protocol.ProtocolFactory;
import com.swirlds.platform.network.protocol.SyncProtocolFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.hiero.consensus.gossip.FallenBehindManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link SyncProtocol}
 */
@DisplayName("Sync Protocol Tests")
class SyncProtocolFactoryTests {
    private NodeId peerId;
    private ShadowgraphSynchronizer shadowGraphSynchronizer;
    private FallenBehindManager fallenBehindManager;
    private SyncPermitProvider permitProvider;
    private Duration sleepAfterSync;
    private SyncMetrics syncMetrics;
    private FakeTime time;
    private PlatformContext platformContext;

    /**
     * Counts the number of currently available sync permits in the permit provider.
     *
     * @param permitProvider the permit provider to measure
     * @return the number of available permits
     */
    private static int countAvailablePermits(@NonNull final SyncPermitProvider permitProvider) {
        int count = 0;
        while (permitProvider.acquire()) {
            count++;
        }
        for (int i = 0; i < count; i++) {
            permitProvider.release();
        }
        return count;
    }

    @BeforeEach
    void setup() {
        peerId = NodeId.of(1);
        shadowGraphSynchronizer = mock(ShadowgraphSynchronizer.class);
        fallenBehindManager = mock(FallenBehindManager.class);

        time = new FakeTime();
        platformContext = TestPlatformContextBuilder.create().withTime(time).build();

        permitProvider = new SyncPermitProvider(platformContext, 2);
        sleepAfterSync = Duration.ofMillis(0);
        syncMetrics = mock(SyncMetrics.class);

        // Set reasonable defaults. Special cases to be configured in individual tests

        // node is not fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(false);
        // only peer with ID 1 is needed for fallen behind
        Mockito.when(fallenBehindManager.getNeededForFallenBehind()).thenReturn(List.of(NodeId.of(1L)));
    }

    @Test
    @DisplayName("Protocol should initiate connection")
    void shouldInitiate() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertTrue(syncProtocolFactory.build(peerId).shouldInitiate());
        assertEquals(1, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol won't initiate connection if cooldown isn't complete")
    void initiateCooldown() {
        assertEquals(2, countAvailablePermits(permitProvider));

        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                Duration.ofMillis(100),
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);
        // do an initial sync, so we can verify that the resulting cooldown period is respected
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, countAvailablePermits(permitProvider));
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, countAvailablePermits(permitProvider));

        // no time has passed since the previous protocol
        assertFalse(protocol.shouldInitiate());
        assertEquals(2, countAvailablePermits(permitProvider));

        // tick part of the way through the cooldown period
        time.tick(Duration.ofMillis(55));

        assertFalse(protocol.shouldInitiate());
        assertEquals(2, countAvailablePermits(permitProvider));

        // tick past the end of the cooldown period
        time.tick(Duration.ofMillis(55));

        assertTrue(protocol.shouldInitiate());
        assertEquals(1, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't initiate if platform has the wrong status")
    void incorrectStatusToInitiate() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> BEHIND);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertFalse(protocol.shouldInitiate());
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't initiate without a permit")
    void noPermitAvailableToInitiate() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        // obtain the only existing permits, so none are available to the protocol
        permitProvider.acquire();
        permitProvider.acquire();
        assertEquals(0, countAvailablePermits(permitProvider));

        assertFalse(protocol.shouldInitiate());
        assertEquals(0, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't initiate if peer agnostic checks fail")
    void peerAgnosticChecksFailAtInitiate() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> true,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertFalse(protocol.shouldInitiate());
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't initiate if it has fallen behind")
    void fallenBehindAtInitiate() {
        // node is fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(true);

        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertFalse(protocol.shouldInitiate());
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol initiates if peer is needed for fallen behind")
    void initiateForFallenBehind() {

        // peer *is* needed for fallen behind (by default)
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol initiates if peer is part of critical quorum")
    void initiateForCriticalQuorum() {
        // peer 6 isn't needed for fallen behind, but it *is* in critical quorum (by default)
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(NodeId.of(6));

        assertEquals(2, countAvailablePermits(permitProvider));
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol should accept connection")
    void shouldAccept() {
        assertEquals(2, countAvailablePermits(permitProvider));
        // obtain 1 of the permits, but 1 will still be available to accept
        permitProvider.acquire();
        assertEquals(1, countAvailablePermits(permitProvider));

        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertTrue(protocol.shouldAccept());
        assertEquals(0, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol won't accept connection if cooldown isn't complete")
    void acceptCooldown() {
        assertEquals(2, countAvailablePermits(permitProvider));

        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                Duration.ofMillis(100),
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        // do an initial sync, so we can verify that the resulting cooldown period is respected
        assertTrue(protocol.shouldAccept());
        assertEquals(1, countAvailablePermits(permitProvider));
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, countAvailablePermits(permitProvider));

        // no time has passed since the previous protocol
        assertFalse(protocol.shouldAccept());
        assertEquals(2, countAvailablePermits(permitProvider));

        // tick part of the way through the cooldown period
        time.tick(Duration.ofMillis(55));

        assertFalse(protocol.shouldAccept());
        assertEquals(2, countAvailablePermits(permitProvider));

        // tick past the end of the cooldown period
        time.tick(Duration.ofMillis(55));

        assertTrue(protocol.shouldAccept());
        assertEquals(1, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't accept if platform has the wrong status")
    void incorrectStatusToAccept() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> BEHIND);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertFalse(protocol.shouldAccept());
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't accept without a permit")
    void noPermitAvailableToAccept() {
        assertEquals(2, countAvailablePermits(permitProvider));

        // waste both available permits
        permitProvider.acquire();
        permitProvider.acquire();

        assertEquals(0, countAvailablePermits(permitProvider));

        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertFalse(protocol.shouldAccept());
        assertEquals(0, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't accept if peer agnostic checks fail")
    void peerAgnosticChecksFailAtAccept() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> true,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertFalse(protocol.shouldAccept());
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol doesn't accept if it has fallen behind")
    void fallenBehindAtAccept() {
        // node is fallen behind
        Mockito.when(fallenBehindManager.hasFallenBehind()).thenReturn(true);

        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertFalse(protocol.shouldAccept());
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Permit closes after failed accept")
    void permitClosesAfterFailedAccept() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertTrue(protocol.shouldAccept());
        assertEquals(1, countAvailablePermits(permitProvider));
        protocol.acceptFailed();
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Permit closes after failed initiate")
    void permitClosesAfterFailedInitiate() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        assertTrue(protocol.shouldInitiate());
        assertEquals(1, countAvailablePermits(permitProvider));
        protocol.initiateFailed();
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol runs successfully when initiating")
    void successfulInitiatedProtocol() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        protocol.shouldInitiate();
        assertEquals(1, countAvailablePermits(permitProvider));
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Protocol runs successfully when accepting")
    void successfulAcceptedProtocol() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertEquals(2, countAvailablePermits(permitProvider));
        protocol.shouldAccept();
        assertEquals(1, countAvailablePermits(permitProvider));
        assertDoesNotThrow(() -> protocol.runProtocol(mock(Connection.class)));
        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("ParallelExecutionException is caught and rethrown as NetworkProtocolException")
    void rethrowParallelExecutionException()
            throws ParallelExecutionException, IOException, SyncException, InterruptedException {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        // mock synchronize to throw a ParallelExecutionException
        Mockito.when(shadowGraphSynchronizer.synchronize(any(), any()))
                .thenThrow(new ParallelExecutionException(mock(Throwable.class)));

        assertEquals(2, countAvailablePermits(permitProvider));
        protocol.shouldAccept();
        assertEquals(1, countAvailablePermits(permitProvider));

        assertThrows(NetworkProtocolException.class, () -> protocol.runProtocol(mock(Connection.class)));

        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("Exception with IOException as root cause is caught and rethrown as IOException")
    void rethrowRootCauseIOException()
            throws ParallelExecutionException, IOException, SyncException, InterruptedException {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        // mock synchronize to throw a ParallelExecutionException with root cause being an IOException
        Mockito.when(shadowGraphSynchronizer.synchronize(any(), any()))
                .thenThrow(new ParallelExecutionException(new IOException()));

        assertEquals(2, countAvailablePermits(permitProvider));
        protocol.shouldAccept();
        assertEquals(1, countAvailablePermits(permitProvider));

        assertThrows(IOException.class, () -> protocol.runProtocol(mock(Connection.class)));

        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("SyncException is caught and rethrown as NetworkProtocolException")
    void rethrowSyncException() throws ParallelExecutionException, IOException, SyncException, InterruptedException {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        // mock synchronize to throw a SyncException
        Mockito.when(shadowGraphSynchronizer.synchronize(any(), any())).thenThrow(new SyncException(""));

        assertEquals(2, countAvailablePermits(permitProvider));
        protocol.shouldAccept();
        assertEquals(1, countAvailablePermits(permitProvider));

        assertThrows(NetworkProtocolException.class, () -> protocol.runProtocol(mock(Connection.class)));

        assertEquals(2, countAvailablePermits(permitProvider));
    }

    @Test
    @DisplayName("acceptOnSimultaneousInitiate should return true")
    void acceptOnSimultaneousInitiate() {
        final ProtocolFactory syncProtocolFactory = new SyncProtocolFactory(
                platformContext,
                shadowGraphSynchronizer,
                fallenBehindManager,
                permitProvider,
                mock(IntakeEventCounter.class),
                () -> false,
                sleepAfterSync,
                syncMetrics,
                () -> ACTIVE);
        final Protocol protocol = syncProtocolFactory.build(peerId);

        assertTrue(protocol.acceptOnSimultaneousInitiate());
    }
}
