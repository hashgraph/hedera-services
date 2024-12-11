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

package com.swirlds.demo.iss;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.state.merkle.singleton.StringLeaf;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ISSTestingToolStateTest {

    private static final int RUNNING_SUM_INDEX = 3;
    private ISSTestingToolState state;
    private PlatformStateModifier platformStateModifier;
    private Round round;
    private ConsensusEvent event;
    private Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>> consumer;
    private ConsensusTransaction consensusTransaction;

    @BeforeEach
    void setUp() {
        state = new ISSTestingToolState(mock(MerkleStateLifecycles.class), mock(Function.class));
        platformStateModifier = mock(PlatformStateModifier.class);
        round = mock(Round.class);
        event = mock(ConsensusEvent.class);
        consumer = mock(Consumer.class);
        consensusTransaction = mock(TransactionWrapper.class);
    }

    @Test
    void handleConsensusRoundWithApplicationTransaction() {
        // Given
        givenRoundAndEvent();

        final var bytes = Bytes.wrap(new byte[] {1, 1, 1, 1});
        when(consensusTransaction.getApplicationTransaction()).thenReturn(bytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isGreaterThan(0L);
    }

    @Test
    void handleConsensusRoundWithSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var bytes = Bytes.wrap(new byte[] {1, 0, 1, 1, 0, 1, 0, 0});
        when(consensusTransaction.getApplicationTransaction()).thenReturn(bytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).isNull();
    }

    @Test
    void handleConsensusRoundWithEmptyTransaction() {
        // Given
        givenRoundAndEvent();

        final var bytes = Bytes.wrap(new byte[0]);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(bytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isEqualTo(0L);
    }

    private void givenRoundAndEvent() {
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(Collections.singletonList(consensusTransaction).iterator());
    }
}
