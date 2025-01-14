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

package com.swirlds.demo.addressbook;

import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.state.merkle.singleton.StringLeaf;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressBookTestingToolStateTest {

    private static final int RUNNING_SUM_INDEX = 3;
    private static AddressBookTestingToolState state;
    private static AddressBookTestingToolStateLifecycles stateLifecycles;
    private AddressBookTestingToolMain main;
    private Random random;
    private Platform platform;
    private PlatformContext platformContext;
    private Round round;
    private ConsensusEvent event;
    private List<ScopedSystemTransaction<StateSignatureTransaction>> consumedTransactions;
    private Consumer<ScopedSystemTransaction<StateSignatureTransaction>> consumer;
    private Transaction consensusTransaction;
    private StateSignatureTransaction stateSignatureTransaction;
    private InitTrigger initTrigger;
    private SoftwareVersion softwareVersion;
    private Configuration configuration;
    private AddressBookConfig addressBookConfig;
    private AddressBookTestingToolConfig addressBookTestingToolConfig;

    @BeforeAll
    static void initState() {
        state = new AddressBookTestingToolState(mock(Function.class));
        stateLifecycles = new AddressBookTestingToolStateLifecycles();
        FAKE_MERKLE_STATE_LIFECYCLES.initStates(state);
    }

    @BeforeEach
    void setUp() {
        state.setChild(RUNNING_SUM_INDEX, new StringLeaf("0"));
        platform = mock(Platform.class);
        initTrigger = InitTrigger.GENESIS;
        softwareVersion = new BasicSoftwareVersion(1);
        platformContext = mock(PlatformContext.class);
        configuration = mock(Configuration.class);
        addressBookConfig = mock(AddressBookConfig.class);
        addressBookTestingToolConfig = mock(AddressBookTestingToolConfig.class);

        when(platform.getContext()).thenReturn(platformContext);
        when(platformContext.getConfiguration()).thenReturn(configuration);
        when(configuration.getConfigData(AddressBookConfig.class)).thenReturn(addressBookConfig);
        when(configuration.getConfigData(AddressBookTestingToolConfig.class)).thenReturn(addressBookTestingToolConfig);
        when(addressBookTestingToolConfig.freezeAfterGenesis()).thenReturn(Duration.ZERO);
        when(addressBookTestingToolConfig.testScenario())
                .thenReturn(String.valueOf(AddressBookTestScenario.GENESIS_NORMAL));

        stateLifecycles.onStateInitialized(state, platform, initTrigger, softwareVersion);

        main = mock(AddressBookTestingToolMain.class);
        random = new Random();
        round = mock(Round.class);
        event = mock(ConsensusEvent.class);

        consumedTransactions = new ArrayList<>();
        consumer = systemTransaction -> consumedTransactions.add(systemTransaction);
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

    @AfterEach
    void tearDown() {
        state.setChild(RUNNING_SUM_INDEX, null);
    }

    @Test
    void handleConsensusRoundWithApplicationTransaction() {
        // Given
        givenRoundAndEvent();

        final var bytes = Bytes.wrap(new byte[] {1, 1, 1, 1});
        when(consensusTransaction.getApplicationTransaction()).thenReturn(bytes);

        // When
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isPositive();
        assertThat(consumedTransactions).isEmpty();
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
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isZero();
        assertThat(consumedTransactions).hasSize(1);
    }

    @Test
    void handleConsensusRoundWithMultipleSystemTransaction() {
        // Given
        final var secondConsensusTransaction = mock(TransactionWrapper.class);
        final var thirdConsensusTransaction = mock(TransactionWrapper.class);
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(List.of(
                                (ConsensusTransaction) consensusTransaction,
                                secondConsensusTransaction,
                                thirdConsensusTransaction)
                        .iterator());

        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(main.encodeSystemTransaction(stateSignatureTransaction)).thenReturn(stateSignatureTransactionBytes);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isZero();
        assertThat(consumedTransactions).hasSize(3);
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
        verify(round, times(1)).iterator();
        verify(event, times(1)).consensusTransactionIterator();

        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isZero();
        assertThat(consumedTransactions).isEmpty();
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
        when(consensusTransaction.isSystem()).thenReturn(false);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(emptyStateSignatureTransactionBytes);

        // When
        stateLifecycles.onHandleConsensusRound(round, state, consumer);

        // Then
        verify(round, times(1)).iterator();
        verify(event, times(1)).consensusTransactionIterator();

        System.out.println(consumedTransactions);
        assertThat(Long.parseLong(((StringLeaf) state.getChild(RUNNING_SUM_INDEX)).getLabel()))
                .isZero();
        assertThat(consumedTransactions).isEmpty();
    }

    @Test
    void preHandleEventWithMultipleSystemTransaction() {
        // Given
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        final var secondConsensusTransaction = mock(TransactionWrapper.class);
        final var thirdConsensusTransaction = mock(TransactionWrapper.class);
        when(event.transactionIterator())
                .thenReturn(List.of(consensusTransaction, secondConsensusTransaction, thirdConsensusTransaction)
                        .iterator());
        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedTransactions).hasSize(3);
    }

    @Test
    void preHandleEventWithSystemTransaction() {
        // Given
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.transactionIterator())
                .thenReturn(Collections.singletonList(consensusTransaction).iterator());
        final var emptyStateSignatureBytes = StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(emptyStateSignatureBytes);

        // When
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedTransactions).hasSize(1);
    }

    @Test
    void preHandleEventWithDeprecatedSystemTransaction() {
        // Given
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.transactionIterator())
                .thenReturn(Collections.singletonList(consensusTransaction).iterator());
        when(consensusTransaction.isSystem()).thenReturn(true);

        // When
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedTransactions).isEmpty();
    }

    @Test
    void preHandleEventWithEmptyTransaction() {
        // Given
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.transactionIterator())
                .thenReturn(Collections.singletonList(consensusTransaction).iterator());
        final var emptyStateSignatureBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(StateSignatureTransaction.DEFAULT);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(emptyStateSignatureBytes);

        // When
        stateLifecycles.onPreHandle(event, state, consumer);

        // Then
        assertThat(consumedTransactions).isEmpty();
    }

    private void givenRoundAndEvent() {
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(Collections.singletonList((ConsensusTransaction) consensusTransaction)
                        .iterator());
    }
}
