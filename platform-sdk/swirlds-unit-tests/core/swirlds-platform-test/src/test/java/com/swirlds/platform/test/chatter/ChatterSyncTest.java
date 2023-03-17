/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter;

import static org.mockito.Mockito.when;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.platform.Connection;
import com.swirlds.platform.chatter.ChatterSyncProtocol;
import com.swirlds.platform.chatter.protocol.MessageProvider;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.sync.FallenBehindManager;
import com.swirlds.platform.sync.ShadowGraphSynchronizer;
import com.swirlds.platform.sync.SyncException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class ChatterSyncTest {
    private static final NodeId PEER_ID = new NodeId(false, 1L);
    final CommunicationState state = new CommunicationState();
    final ShadowGraphSynchronizer synchronizer = Mockito.mock(ShadowGraphSynchronizer.class);
    final Connection connection = Mockito.mock(Connection.class);
    final MessageProvider messageProvider = Mockito.mock(MessageProvider.class);
    final FallenBehindManager fallenBehindManager = Mockito.mock(FallenBehindManager.class);
    final ChatterSyncProtocol chatterSync =
            new ChatterSyncProtocol(PEER_ID, state, messageProvider, synchronizer, fallenBehindManager);

    private static List<Arguments> exceptions() {
        return List.of(
                Arguments.of(new SyncException("fake exception")),
                Arguments.of(new RuntimeException("fake exception")));
    }

    @BeforeEach
    void setup() {
        resetFallenBehind();
        state.reset();
    }

    @Test
    void regularRun()
            throws ParallelExecutionException, IOException, SyncException, InterruptedException,
                    NetworkProtocolException {
        chatterSync.runProtocol(connection);
        Mockito.verify(synchronizer).synchronize(connection);
    }

    @Test
    void suspendedCheck() {
        Assertions.assertTrue(chatterSync.shouldInitiate(), "if communication is not suspended, we should sync");
        Assertions.assertTrue(chatterSync.shouldAccept(), "if communication is not suspended, we should sync");

        state.suspend();
        Assertions.assertFalse(chatterSync.shouldInitiate(), "if communication is suspended, we should NOT sync");
        Assertions.assertFalse(chatterSync.shouldAccept(), "if communication is suspended, we should NOT sync");

        state.unsuspend();
        Assertions.assertTrue(chatterSync.shouldInitiate(), "if communication is not suspended, we should sync");
        Assertions.assertTrue(chatterSync.shouldAccept(), "if communication is not suspended, we should sync");
    }

    @Test
    void fallenBehindCheck() {
        Assertions.assertTrue(
                chatterSync.shouldInitiate(), "if the peer has not reported us as fallen behind, we should sync");
        Assertions.assertTrue(
                chatterSync.shouldAccept(), "if the peer has not reported us as fallen behind, we should sync");

        when(fallenBehindManager.getNeededForFallenBehind()).thenReturn(List.of(0L, PEER_ID.getId()));
        Assertions.assertTrue(
                chatterSync.shouldInitiate(), "if the peer has not reported us as fallen behind, we should sync");
        Assertions.assertTrue(
                chatterSync.shouldAccept(), "if the peer has not reported us as fallen behind, we should sync");

        when(fallenBehindManager.getNeededForFallenBehind()).thenReturn(List.of(0L));
        Assertions.assertFalse(
                chatterSync.shouldInitiate(), "if the peer has reported us as fallen behind, we should not sync");
        Assertions.assertFalse(
                chatterSync.shouldAccept(), "if the peer has reported us as fallen behind, we should not sync");

        resetFallenBehind();
        Assertions.assertTrue(
                chatterSync.shouldInitiate(), "if the peer has not reported us as fallen behind, we should sync");
        Assertions.assertTrue(
                chatterSync.shouldAccept(), "if the peer has not reported us as fallen behind, we should sync");
    }

    @Test
    void testAcceptInitiate() {
        Assertions.assertTrue(chatterSync.shouldAccept(), "in a default state we are out of sync, so we should accept");
        Assertions.assertTrue(
                chatterSync.shouldInitiate(), "in a default state we are out of sync, so we should initiate");
        // this is called when we are in a sync
        state.chatterSyncStartingPhase3();
        Assertions.assertTrue(
                chatterSync.shouldAccept(),
                "even if we are in sync, we don't know what the state of the neighbor is, so we should accept");
        Assertions.assertFalse(chatterSync.shouldInitiate(), "if we are in sync, we should not initiate");
    }

    private void resetFallenBehind() {
        when(fallenBehindManager.getNeededForFallenBehind()).thenReturn(null);
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void throwsException(final Exception e)
            throws ParallelExecutionException, IOException, SyncException, InterruptedException {
        Mockito.doAnswer(a -> {
                    state.chatterSyncStartingPhase3();
                    throw e;
                })
                .when(synchronizer)
                .synchronize(Mockito.any());
        Assertions.assertThrows(
                Exception.class, () -> chatterSync.runProtocol(connection), "the exception should be propagated");
        Assertions.assertFalse(state.shouldChatter(), "we should not be chattering after an exception");
    }
}
