// SPDX-License-Identifier: Apache-2.0
package com.swirlds.demo.stats.signing;

import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.APPLICATION_TRANSACTION;
import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.STATE_SIGNATURE_TRANSACTION;
import static com.swirlds.common.utility.ByteUtils.longToByteArray;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.MerkleStateLifecycles;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatsSigningTestingToolStateTest {

    private Random random;
    private StatsSigningTestingToolState state;
    private PlatformStateModifier platformStateModifier;
    private Round round;
    private PlatformEvent event;
    private Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>> consumer;
    private int consumerSize;
    private ConsensusTransaction consensusTransaction;
    private StateSignatureTransaction stateSignatureTransaction;
    private Supplier<SttTransactionPool> transactionPoolSupplier;
    private SttTransactionPool transactionPool;

    @BeforeEach
    void setUp() {
        transactionPool = mock(SttTransactionPool.class);
        transactionPoolSupplier = mock(Supplier.class);
        state = new StatsSigningTestingToolState(
                mock(MerkleStateLifecycles.class), mock(Function.class), transactionPoolSupplier);
        random = new Random();
        platformStateModifier = mock(PlatformStateModifier.class);
        event = mock(PlatformEvent.class);

        final var eventWindow = new EventWindow(10, 5, 20, AncientMode.BIRTH_ROUND_THRESHOLD);
        final var roster = new Roster(Collections.EMPTY_LIST);
        when(event.transactionIterator()).thenReturn(Collections.emptyIterator());
        round = new ConsensusRound(
                roster,
                List.of(event),
                event,
                new Generations(),
                eventWindow,
                new ConsensusSnapshot(),
                false,
                Instant.now());

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

        when(transactionPoolSupplier.get()).thenReturn(transactionPool);
    }

    @Test
    void handleConsensusRoundWithApplicationTransaction() {
        // Given
        givenRoundAndEvent();

        // We need to pass a transaction bigger than 10 bytes because of {@link TransactionCodec#PREAMBLE_SIZE}
        final var bytes =
                Bytes.wrap(longToByteArray(random.nextLong())).append(Bytes.wrap(longToByteArray(random.nextLong())));
        when(consensusTransaction.getApplicationTransaction()).thenReturn(bytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        assertThat(consumerSize).isZero();
    }

    @Test
    void handleConsensusRoundWithSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        assertThat(consumerSize).isEqualTo(1);
    }

    @Test
    void handleConsensusRoundWithMultipleSystemTransaction() {
        // Given
        final var secondConsensusTransaction = mock(TransactionWrapper.class);
        final var thirdConsensusTransaction = mock(TransactionWrapper.class);
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(List.of(consensusTransaction, secondConsensusTransaction, thirdConsensusTransaction)
                        .iterator());

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
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
        assertThat(consumerSize).isZero();
    }

    @Test
    void preHandleConsensusRoundWithApplicationTransaction() {
        // Given
        givenRoundAndEvent();

        // We need to pass a transaction bigger than 10 bytes because of {@link TransactionCodec#PREAMBLE_SIZE}
        final var bytes =
                Bytes.wrap(longToByteArray(random.nextLong())).append(Bytes.wrap(longToByteArray(random.nextLong())));

        final var eventTransaction = new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, bytes));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(eventCore, null, List.of(eventTransaction), Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        event = new PlatformEvent(gossipEvent);

        // When
        state.preHandle(event, consumer);

        // Then
        assertThat(consumerSize).isZero();
    }

    @Test
    void preHandleConsensusRoundWithSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        final var eventTransaction =
                new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, stateSignatureTransactionBytes));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(eventCore, null, List.of(eventTransaction), Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        event = new PlatformEvent(gossipEvent);

        // When
        state.preHandle(event, consumer);

        // Then
        assertThat(consumerSize).isEqualTo(1);
    }

    @Test
    void preHandleConsensusRoundWithMultipleSystemTransaction() {
        // Given
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);

        final var eventTransaction =
                new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, stateSignatureTransactionBytes));
        final var secondEventTransaction =
                new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, stateSignatureTransactionBytes));
        final var thirdEventTransaction =
                new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, stateSignatureTransactionBytes));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(
                eventCore,
                null,
                List.of(eventTransaction, secondEventTransaction, thirdEventTransaction),
                Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        event = new PlatformEvent(gossipEvent);

        // When
        state.preHandle(event, consumer);

        // Then
        assertThat(consumerSize).isEqualTo(3);
    }

    @Test
    void preHandleConsensusRoundWithDeprecatedSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        final var eventTransaction =
                new EventTransaction(new OneOf<>(STATE_SIGNATURE_TRANSACTION, stateSignatureTransactionBytes));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(eventCore, null, List.of(eventTransaction), Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        event = new PlatformEvent(gossipEvent);

        // When
        state.preHandle(event, consumer);

        // Then
        assertThat(consumerSize).isZero();
    }

    private void givenRoundAndEvent() {
        when(event.getCreatorId()).thenReturn(new NodeId());
        when(event.getSoftwareVersion()).thenReturn(new SemanticVersion(1, 1, 1, "", ""));
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(Collections.singletonList(consensusTransaction).iterator());
    }
}
