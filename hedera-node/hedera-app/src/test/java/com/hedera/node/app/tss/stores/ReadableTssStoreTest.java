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

package com.hedera.node.app.tss.stores;

import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_ENCRYPTION_KEYS_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_STATUS_KEY;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableTssStoreTest {

    @Mock
    private ReadableKVState<TssMessageMapKey, TssMessageTransactionBody> readableTssMessageState;

    @Mock
    private ReadableKVState<TssVoteMapKey, TssVoteTransactionBody> readableTssVoteState;

    @Mock
    private ReadableKVState<EntityNumber, TssEncryptionKeyTransactionBody> readableTssEncryptionKeyState;

    @Mock
    private ReadableSingletonState<TssStatus> readableTssStatusState;

    @Mock
    private ReadableStates states;

    private ReadableTssStoreImpl tssStore;

    private static final Bytes SOURCE_HASH = Bytes.wrap("SOURCE");
    private static final Bytes TARGET_HASH = Bytes.wrap("TARGET");
    private static final Roster SOURCE_ROSTER = Roster.newBuilder()
            .rosterEntries(
                    new RosterEntry(0L, 4L, Bytes.EMPTY, List.of()),
                    new RosterEntry(1L, 3L, Bytes.EMPTY, List.of()),
                    new RosterEntry(2L, 2L, Bytes.EMPTY, List.of()))
            .build();
    private static final long SOURCE_WEIGHT = 9L;
    private static final Roster TARGET_ROSTER = Roster.newBuilder()
            .rosterEntries(
                    new RosterEntry(0L, 1L, Bytes.EMPTY, List.of()),
                    new RosterEntry(1L, 2L, Bytes.EMPTY, List.of()),
                    new RosterEntry(2L, 3L, Bytes.EMPTY, List.of()),
                    new RosterEntry(3L, 4L, Bytes.EMPTY, List.of()))
            .build();

    @Mock
    private ReadableRosterStore rosterStore;

    @Mock
    private ReadableTssStore subject;

    private final ArgumentCaptor<LongUnaryOperator> nodeWeightCaptor = ArgumentCaptor.forClass(LongUnaryOperator.class);

    @BeforeEach
    void setUp() {
        when(states.<TssMessageMapKey, TssMessageTransactionBody>get(TSS_MESSAGE_MAP_KEY))
                .thenReturn(readableTssMessageState);
        when(states.<TssVoteMapKey, TssVoteTransactionBody>get(TSS_VOTE_MAP_KEY))
                .thenReturn(readableTssVoteState);
        when(states.<EntityNumber, TssEncryptionKeyTransactionBody>get(TSS_ENCRYPTION_KEYS_KEY))
                .thenReturn(readableTssEncryptionKeyState);
        when(states.<TssStatus>getSingleton(TSS_STATUS_KEY)).thenReturn(readableTssStatusState);

        tssStore = new ReadableTssStoreImpl(states);
    }

    @Test
    void assumesSourceRosterIsEquallySizedAndEquallyWeightedWhenMissingFromStore() {
        doCallRealMethod().when(subject).anyWinningVoteFrom(any(), any(), any());
        given(rosterStore.get(TARGET_HASH)).willReturn(TARGET_ROSTER);

        subject.anyWinningVoteFrom(Bytes.EMPTY, TARGET_HASH, rosterStore);

        verify(subject)
                .anyWinningVoteFrom(
                        eq(Bytes.EMPTY),
                        eq(TARGET_HASH),
                        eq((long) TARGET_ROSTER.rosterEntries().size()),
                        nodeWeightCaptor.capture());

        final var nodeWeight = nodeWeightCaptor.getValue();
        TARGET_ROSTER.rosterEntries().forEach(entry -> assertEquals(1L, nodeWeight.applyAsLong(entry.nodeId())));
    }

    @Test
    void usesSourceRosterWeightsWhenPresentInStore() {
        doCallRealMethod().when(subject).anyWinningVoteFrom(any(), any(), any());
        given(rosterStore.get(SOURCE_HASH)).willReturn(SOURCE_ROSTER);

        subject.anyWinningVoteFrom(SOURCE_HASH, TARGET_HASH, rosterStore);

        verify(subject)
                .anyWinningVoteFrom(eq(SOURCE_HASH), eq(TARGET_HASH), eq(SOURCE_WEIGHT), nodeWeightCaptor.capture());

        final var nodeWeight = nodeWeightCaptor.getValue();
        SOURCE_ROSTER
                .rosterEntries()
                .forEach(entry -> assertEquals(entry.weight(), nodeWeight.applyAsLong(entry.nodeId())));
    }

    @Test
    void noRosterKeysWithoutWinningVote() {
        doCallRealMethod().when(subject).consensusRosterKeys(SOURCE_HASH, TARGET_HASH, rosterStore);

        assertTrue(subject.consensusRosterKeys(SOURCE_HASH, TARGET_HASH, rosterStore)
                .isEmpty());
    }

    @Test
    void includesExpectedRosterKeys() {
        final var messages = IntStream.range(0, 4)
                .mapToObj(i ->
                        new TssMessageTransactionBody(SOURCE_HASH, TARGET_HASH, i * 2L + 1, Bytes.wrap("MESSAGE" + i)))
                .toList();
        doCallRealMethod().when(subject).consensusRosterKeys(SOURCE_HASH, TARGET_HASH, rosterStore);
        final var ledgerId = Bytes.wrap("LEDGER_ID");
        final var tssVote = new BitSet();
        tssVote.set(1);
        tssVote.set(3);
        final var winningVote = new TssVoteTransactionBody(
                SOURCE_HASH, TARGET_HASH, ledgerId, Bytes.EMPTY, Bytes.wrap(tssVote.toByteArray()));
        given(subject.anyWinningVoteFrom(SOURCE_HASH, TARGET_HASH, rosterStore)).willReturn(Optional.of(winningVote));
        given(subject.getMessagesForTarget(TARGET_HASH)).willReturn(messages);

        final var maybeRosterKeys = subject.consensusRosterKeys(SOURCE_HASH, TARGET_HASH, rosterStore);
        assertThat(maybeRosterKeys).isPresent();
        final var rosterKeys = maybeRosterKeys.orElseThrow();
        assertThat(rosterKeys.tssMessages()).containsExactly(messages.get(1), messages.get(3));
        assertThat(rosterKeys.ledgerId()).isEqualTo(ledgerId);
    }

    @Test
    void testGetMessage() {
        TssMessageMapKey key = TssMessageMapKey.DEFAULT;
        TssMessageTransactionBody message = TssMessageTransactionBody.DEFAULT;
        when(readableTssMessageState.get(key)).thenReturn(message);

        TssMessageTransactionBody result = tssStore.getMessage(key);
        assertEquals(message, result);
    }

    @Test
    void testExistsMessage() {
        TssMessageMapKey key = TssMessageMapKey.DEFAULT;
        when(readableTssMessageState.contains(key)).thenReturn(true);

        assertTrue(tssStore.exists(key));
    }

    @Test
    void testGetVote() {
        TssVoteMapKey key = TssVoteMapKey.DEFAULT;
        TssVoteTransactionBody vote = TssVoteTransactionBody.DEFAULT;
        when(readableTssVoteState.get(key)).thenReturn(vote);

        TssVoteTransactionBody result = tssStore.getVote(key);
        assertEquals(vote, result);
    }

    @Test
    void testExistsVote() {
        TssVoteMapKey key = TssVoteMapKey.DEFAULT;
        when(readableTssVoteState.contains(key)).thenReturn(true);

        assertTrue(tssStore.exists(key));
    }

    @Test
    void testGetMessagesForTarget() {
        Bytes rosterHash = Bytes.wrap("targetHash".getBytes());
        TssMessageMapKey key = mock(TssMessageMapKey.class);
        TssMessageTransactionBody message = mock(TssMessageTransactionBody.class);
        when(key.rosterHash()).thenReturn(rosterHash);
        when(readableTssMessageState.keys()).thenReturn(singletonList(key).iterator());
        when(readableTssMessageState.get(key)).thenReturn(message);

        List<TssMessageTransactionBody> result = tssStore.getMessagesForTarget(rosterHash);
        assertEquals(1, result.size());
        assertEquals(message, result.get(0));
    }

    @Test
    void testGetTssEncryptionKey() {
        long nodeID = 123L;
        EntityNumber entityNumber = new EntityNumber(nodeID);
        TssEncryptionKeyTransactionBody encryptionKey = TssEncryptionKeyTransactionBody.DEFAULT;
        when(readableTssEncryptionKeyState.get(entityNumber)).thenReturn(encryptionKey);

        TssEncryptionKeyTransactionBody result = tssStore.getTssEncryptionKey(nodeID);
        assertEquals(encryptionKey, result);
    }

    @Test
    void testGetTssStatus() {
        TssStatus status = TssStatus.DEFAULT;
        when(readableTssStatusState.get()).thenReturn(status);

        TssStatus result = tssStore.getTssStatus();
        assertEquals(status, result);
    }

    @Test
    void testAnyWinningVoteFrom() {
        Bytes sourceRosterHash = Bytes.wrap("sourceHash".getBytes());
        Bytes targetRosterHash = Bytes.wrap("targetHash".getBytes());
        TssVoteMapKey key = mock(TssVoteMapKey.class);
        TssVoteTransactionBody vote = mock(TssVoteTransactionBody.class);
        when(key.rosterHash()).thenReturn(targetRosterHash);
        when(vote.sourceRosterHash()).thenReturn(sourceRosterHash);
        when(readableTssVoteState.keys()).thenReturn(singletonList(key).iterator());
        when(readableTssVoteState.get(key)).thenReturn(vote);
        when(vote.tssVote()).thenReturn(Bytes.wrap("vote".getBytes()));

        LongUnaryOperator weightFn = nodeId -> 10L;
        Optional<TssVoteTransactionBody> result =
                tssStore.anyWinningVoteFrom(sourceRosterHash, targetRosterHash, 10L, weightFn);
        assertTrue(result.isPresent());
    }
}
