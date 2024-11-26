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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableTssStoreTest {
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
}
