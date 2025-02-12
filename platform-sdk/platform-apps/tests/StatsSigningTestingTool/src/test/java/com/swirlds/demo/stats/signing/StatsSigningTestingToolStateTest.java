/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.stats.signing;

import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.APPLICATION_TRANSACTION;
import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.STATE_SIGNATURE_TRANSACTION;
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
import com.swirlds.demo.stats.signing.algorithms.X25519SigningAlgorithm;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import java.security.SignatureException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatsSigningTestingToolStateTest {

    private static final int transactionSize = 100;
    private Random random;
    private StatsSigningTestingToolState state;
    private StatsSigningTestingToolStateLifecycles stateLifecycles;
    private StatsSigningTestingToolMain main;
    private Round round;
    private PlatformEvent event;
    private Consumer<ScopedSystemTransaction<StateSignatureTransaction>> consumer;
    private List<ScopedSystemTransaction<StateSignatureTransaction>> consumedSystemTransactions;
    private ConsensusTransaction consensusTransaction;
    private StateSignatureTransaction stateSignatureTransaction;

    @BeforeEach
    void setUp() {
        final SttTransactionPool transactionPool = mock(SttTransactionPool.class);
        final Supplier<SttTransactionPool> transactionPoolSupplier = mock(Supplier.class);
        final Function<SemanticVersion, SoftwareVersion> versionFactory = mock(Function.class);
        state = new StatsSigningTestingToolState(versionFactory);
        stateLifecycles = new StatsSigningTestingToolStateLifecycles(transactionPoolSupplier);
        main = new StatsSigningTestingToolMain();
        random = new Random();
        event = mock(PlatformEvent.class);

        final var eventWindow = new EventWindow(10, 5, 20, AncientMode.BIRTH_ROUND_THRESHOLD);
        final var roster = new Roster(Collections.EMPTY_LIST);
        when(event.transactionIterator()).thenReturn(Collections.emptyIterator());
        round = new ConsensusRound(
                roster, List.of(event), event, eventWindow, new ConsensusSnapshot(), false, Instant.now());

        consumedSystemTransactions = new ArrayList<>();
        consumer = systemTransaction -> consumedSystemTransactions.add(systemTransaction);
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void handleConsensusRoundWithApplicationTransaction(final boolean signedTransaction) throws SignatureException {
        // Given
        givenRoundAndEvent();

        final var transactionBytes =
                signedTransaction ? getSignedApplicationTransaction() : getUnsignedApplicationTransaction();

        when(consensusTransaction.getApplicationTransaction()).thenReturn(transactionBytes);

        // When
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    @Test
    void handleConsensusRoundWithSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var stateSignatureTransactionBytes = main.encodeSystemTransaction(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(1);
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

        final var stateSignatureTransactionBytes = main.encodeSystemTransaction(stateSignatureTransaction);

        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(3);
    }

    @Test
    void handleConsensusRoundWithDeprecatedSystemTransaction() {
        // Given
        givenRoundAndEvent();

        when(consensusTransaction.getApplicationTransaction()).thenReturn(Bytes.EMPTY);
        when(consensusTransaction.isSystem()).thenReturn(true);

        // When
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preHandleConsensusRoundWithApplicationTransaction(final boolean signedTransaction) throws SignatureException {
        // Given
        givenRoundAndEvent();

        final var bytes = signedTransaction ? getSignedApplicationTransaction() : getUnsignedApplicationTransaction();

        final var eventTransaction = new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, bytes));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(eventCore, null, List.of(eventTransaction), Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        event = new PlatformEvent(gossipEvent);

        // When
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    @Test
    void preHandleConsensusRoundWithSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var stateSignatureTransactionBytes = main.encodeSystemTransaction(stateSignatureTransaction);
        final var eventTransaction =
                new EventTransaction(new OneOf<>(APPLICATION_TRANSACTION, stateSignatureTransactionBytes));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(eventCore, null, List.of(eventTransaction), Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        event = new PlatformEvent(gossipEvent);

        // When
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(1);
    }

    @Test
    void preHandleConsensusRoundWithMultipleSystemTransaction() {
        // Given
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());

        final var stateSignatureTransactionBytes = main.encodeSystemTransaction(stateSignatureTransaction);

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
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(3);
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
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    @Test
    void onSealDefaultsToTrue() {
        // Given (empty)

        // When
        final boolean result = stateLifecycles.onSealConsensusRound(round, state);

        // Then
        assertThat(result).isTrue();
    }

    private void givenRoundAndEvent() {
        when(event.getCreatorId()).thenReturn(new NodeId());
        when(event.getSoftwareVersion()).thenReturn(new SemanticVersion(1, 1, 1, "", ""));
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(Collections.singletonList(consensusTransaction).iterator());
    }

    private Bytes getSignedApplicationTransaction() throws SignatureException {
        final byte[] data = new byte[transactionSize];
        random.nextBytes(data);

        final var alg = new X25519SigningAlgorithm();
        alg.tryAcquirePrimitives();
        final var exSig = alg.signEx(data, 0, data.length);
        final var sig = exSig.getSignature();
        final var transactionId = 80_000L;
        return Bytes.wrap(TransactionCodec.encode(alg, transactionId, sig, data));
    }

    private Bytes getUnsignedApplicationTransaction() {
        final byte[] data = new byte[transactionSize];
        random.nextBytes(data);

        final var transactionId = 80_000L;
        return Bytes.wrap(TransactionCodec.encode(null, transactionId, null, data));
    }
}
