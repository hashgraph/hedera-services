// SPDX-License-Identifier: Apache-2.0
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
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ISSTestingToolStateTest {

    private static final int RUNNING_SUM_INDEX = 3;
    private Random random;
    private ISSTestingToolMain main;
    private ISSTestingToolState state;
    private PlatformStateModifier platformStateModifier;
    private Round round;
    private ConsensusEvent event;
    private Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>> consumer;
    private int consumerSize;
    private ConsensusTransaction consensusTransaction;
    private StateSignatureTransaction stateSignatureTransaction;

    @BeforeEach
    void setUp() {
        state = new ISSTestingToolState(mock(MerkleStateLifecycles.class), mock(Function.class));
        main = mock(ISSTestingToolMain.class);
        random = new Random();
        platformStateModifier = mock(PlatformStateModifier.class);
        round = mock(Round.class);
        event = mock(ConsensusEvent.class);

        consumer = systemTransactions -> consumerSize = systemTransactions.size();
        consensusTransaction = mock(TransactionWrapper.class);

        final byte[] signature = new byte[384];
        random.nextBytes(signature);
        final byte[] hash = new byte[48];
        random.nextBytes(hash);
        stateSignatureTransaction = StateSignatureTransaction.newBuilder()
                .signature(Bytes.wrap(signature))
                .hash(Bytes.wrap(hash))
                .round(round.getRoundNum())
                .build();
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
                .isPositive();
        assertThat(consumerSize).isZero();
    }

    @Test
    void handleConsensusRoundWithSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(main.encodeSystemTransaction(stateSignatureTransaction)).thenReturn(stateSignatureTransactionBytes);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).isNull();
        assertThat(consumerSize).isEqualTo(1);
    }

    @Test
    void handleConsensusRoundWithMultipleSystemTransaction() {
        // Given
        final var secondConsensusTransaction = mock(TransactionWrapper.class);
        final var thirdConsensusTransaction = mock(TransactionWrapper.class);
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(List.of(consensusTransaction, secondConsensusTransaction, thirdConsensusTransaction)
                        .iterator());

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(main.encodeSystemTransaction(stateSignatureTransaction)).thenReturn(stateSignatureTransactionBytes);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).isNull();
        assertThat(consumerSize).isEqualTo(3);
    }

    @Test
    void handleConsensusRoundWithDeprecatedSystemTransaction() {
        // Given
        givenRoundAndEvent();

        when(consensusTransaction.isSystem()).thenReturn(true);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).isNull();
        assertThat(consumerSize).isEqualTo(1);
    }

    @Test
    void handleConsensusRoundWithEmptyTransaction() {
        // Given
        givenRoundAndEvent();

        final var emptyStateSignatureTransaction = StateSignatureTransaction.DEFAULT;
        final var emptyStateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(emptyStateSignatureTransaction);
        when(main.encodeSystemTransaction(emptyStateSignatureTransaction))
                .thenReturn(emptyStateSignatureTransactionBytes);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(emptyStateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isZero();
        assertThat(consumerSize).isZero();
    }

    @Test
    void handleConsensusRoundWithNullTransaction() {
        // Given
        givenRoundAndEvent();

        final var emptyStateSignatureTransaction = StateSignatureTransaction.DEFAULT;
        final var emptyStateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(emptyStateSignatureTransaction);
        when(main.encodeSystemTransaction(null))
                .thenReturn(StateSignatureTransaction.PROTOBUF.toBytes(emptyStateSignatureTransaction));
        when(consensusTransaction.getApplicationTransaction()).thenReturn(emptyStateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(2)).getConsensusTimestamp();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isZero();
        assertThat(consumerSize).isZero();
    }

    private void givenRoundAndEvent() {
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(Collections.singletonList(consensusTransaction).iterator());
    }
}
