/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.tss.TssMetrics;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.spi.info.NodeInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssVoteHandlerTest {

    @Mock
    private PreHandleContext preHandleContext;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock
    private WritableTssStore tssBaseStore;

    @Mock
    private ReadableRosterStore rosterStore;

    @Mock
    private TssVoteTransactionBody tssVoteTransactionBody;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private TssMetrics tssMetrics;

    @Mock(strictness = LENIENT)
    private NodeInfo nodeInfo;

    private TssVoteHandler tssVoteHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tssVoteHandler = new TssVoteHandler(tssMetrics);
        when(handleContext.creatorInfo()).thenReturn(nodeInfo);
        when(nodeInfo.nodeId()).thenReturn(1L);
    }

    @Test
    void handleDoesNotThrowWhenValidContext() throws HandleException {
        when(handleContext.body()).thenReturn(transactionBody);
        when(transactionBody.tssVoteOrThrow()).thenReturn(tssVoteTransactionBody);
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssBaseStore);

        when(tssVoteTransactionBody.targetRosterHash()).thenReturn(Bytes.EMPTY);
        when(tssBaseStore.exists(any(TssVoteMapKey.class))).thenReturn(false);

        try (MockedStatic<TssVoteHandler> mockedStatic = mockStatic(TssVoteHandler.class)) {
            mockedStatic
                    .when(() -> TssVoteHandler.hasReachedThreshold(any(), any()))
                    .thenReturn(false);
            tssVoteHandler.handle(handleContext);
        }

        verify(tssBaseStore).put(any(TssVoteMapKey.class), eq(tssVoteTransactionBody));
    }

    @Test
    void handleReturnsWhenDuplicateVoteExists() throws HandleException {
        when(handleContext.body()).thenReturn(transactionBody);
        when(transactionBody.tssVoteOrThrow()).thenReturn(tssVoteTransactionBody);
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssBaseStore);
        when(tssVoteTransactionBody.targetRosterHash()).thenReturn(Bytes.EMPTY);
        when(tssBaseStore.exists(any(TssVoteMapKey.class))).thenReturn(true);

        tssVoteHandler.handle(handleContext);

        verify(tssBaseStore, never()).put(any(TssVoteMapKey.class), eq(tssVoteTransactionBody));
    }

    @Test
    void hasReachedThresholdReturnsFalseWhenThresholdIsNotMet() {
        // Setup in-memory data
        final RosterEntry rosterEntry1 = new RosterEntry(1L, 1L, null, null, List.of());
        final RosterEntry rosterEntry2 = new RosterEntry(2L, 4L, null, null, List.of());
        final RosterEntry rosterEntry3 = new RosterEntry(3L, 2L, null, null, List.of());
        final Roster roster = new Roster(List.of(rosterEntry1, rosterEntry2, rosterEntry3));
        final TssVoteTransactionBody voteTransactionBody =
                new TssVoteTransactionBody(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY);
        final TssVoteTransactionBody voteTransactionBody2 =
                new TssVoteTransactionBody(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.fromHex("01"));
        final TssVoteTransactionBody voteTransactionBody3 =
                new TssVoteTransactionBody(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.fromHex("02"));

        // Setup stores
        final Map<TssVoteMapKey, TssVoteTransactionBody> voteStore = new HashMap<>();
        voteStore.put(new TssVoteMapKey(Bytes.EMPTY, 1L), voteTransactionBody);
        voteStore.put(new TssVoteMapKey(Bytes.EMPTY, 2L), voteTransactionBody2);
        voteStore.put(new TssVoteMapKey(Bytes.EMPTY, 3L), voteTransactionBody3);

        // Mock behavior
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssBaseStore);
        when(storeFactory.readableStore(ReadableRosterStore.class)).thenReturn(rosterStore);
        when(rosterStore.getActiveRoster()).thenReturn(roster);
        when(tssBaseStore.exists(any(TssVoteMapKey.class)))
                .thenAnswer(invocation -> voteStore.containsKey(invocation.getArgument(0)));
        when(tssBaseStore.getVote(any(TssVoteMapKey.class)))
                .thenAnswer(invocation -> voteStore.get(invocation.getArgument(0)));

        final boolean result = TssVoteHandler.hasReachedThreshold(voteTransactionBody, handleContext);

        assertFalse(result, "Threshold should not be met");
    }

    @Test
    void hasReachedThresholdReturnsTrueWhenThresholdIsMet() {
        // Setup in-memory data
        final RosterEntry rosterEntry1 = new RosterEntry(1L, 1L, null, null, List.of());
        final RosterEntry rosterEntry2 = new RosterEntry(2L, 2L, null, null, List.of());
        final RosterEntry rosterEntry3 = new RosterEntry(3L, 3L, null, null, List.of());
        final Roster roster = new Roster(List.of(rosterEntry1, rosterEntry2, rosterEntry3));
        final TssVoteTransactionBody voteTransactionBody =
                new TssVoteTransactionBody(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY);
        final TssVoteTransactionBody voteTransactionBody2 =
                new TssVoteTransactionBody(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.fromHex("01"));
        final TssVoteTransactionBody voteTransactionBody3 =
                new TssVoteTransactionBody(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY);

        // Setup stores
        final Map<TssVoteMapKey, TssVoteTransactionBody> voteStore = new HashMap<>();
        voteStore.put(new TssVoteMapKey(Bytes.EMPTY, 1L), voteTransactionBody);
        voteStore.put(new TssVoteMapKey(Bytes.EMPTY, 2L), voteTransactionBody2);
        voteStore.put(new TssVoteMapKey(Bytes.EMPTY, 3L), voteTransactionBody3);

        // Mock behavior
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssBaseStore);
        when(storeFactory.readableStore(ReadableRosterStore.class)).thenReturn(rosterStore);
        when(rosterStore.getActiveRoster()).thenReturn(roster);
        when(tssBaseStore.exists(any(TssVoteMapKey.class)))
                .thenAnswer(invocation -> voteStore.containsKey(invocation.getArgument(0)));
        when(tssBaseStore.getVote(any(TssVoteMapKey.class)))
                .thenAnswer(invocation -> voteStore.get(invocation.getArgument(0)));

        boolean result = TssVoteHandler.hasReachedThreshold(voteTransactionBody, handleContext);

        assertTrue(result, "Threshold should be met");
    }

    @Test
    void preHandleDoesNotThrowWhenContextIsValid() {
        assertDoesNotThrow(() -> tssVoteHandler.preHandle(preHandleContext));
    }

    @Test
    void pureChecksDoesNotThrowWhenTransactionBodyIsValid() {
        assertDoesNotThrow(() -> tssVoteHandler.pureChecks(transactionBody));
    }

    @Test
    void hasReachedThresholdThrowsIllegalArgumentExceptionWhenActiveRosterIsNull() {
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.readableStore(ReadableRosterStore.class)).thenReturn(rosterStore);
        when(rosterStore.getActiveRoster()).thenReturn(null);

        TssVoteTransactionBody voteTransactionBody =
                new TssVoteTransactionBody(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY);

        assertThrows(
                IllegalArgumentException.class,
                () -> TssVoteHandler.hasReachedThreshold(voteTransactionBody, handleContext));
    }
}
