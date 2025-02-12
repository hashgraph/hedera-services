/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.consistency;

import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static com.swirlds.platform.test.fixtures.state.TestPlatformStateFacade.TEST_PLATFORM_STATE_FACADE;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConsistencyTestingToolStateTest {

    private static ConsistencyTestingToolState state;
    private static ConsistencyTestingToolStateLifecycles stateLifecycle;
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
    private ConsistencyTestingToolConfig consistencyTestingToolConfig;
    private StateCommonConfig stateCommonConfig;

    @BeforeAll
    static void initState() {
        state = new ConsistencyTestingToolState();
        stateLifecycle = new ConsistencyTestingToolStateLifecycles(TEST_PLATFORM_STATE_FACADE);
        FAKE_MERKLE_STATE_LIFECYCLES.initStates(state);
    }

    @BeforeEach
    void setUp() {
        platform = mock(Platform.class);
        initTrigger = InitTrigger.GENESIS;
        softwareVersion = new BasicSoftwareVersion(1);
        platformContext = mock(PlatformContext.class);
        configuration = mock(Configuration.class);
        consistencyTestingToolConfig = mock(ConsistencyTestingToolConfig.class);
        stateCommonConfig = mock(StateCommonConfig.class);

        when(platform.getSelfId()).thenReturn(NodeId.of(1L));
        when(platform.getContext()).thenReturn(platformContext);
        when(platformContext.getConfiguration()).thenReturn(configuration);
        when(configuration.getConfigData(ConsistencyTestingToolConfig.class)).thenReturn(consistencyTestingToolConfig);
        when(configuration.getConfigData(StateCommonConfig.class)).thenReturn(stateCommonConfig);
        when(consistencyTestingToolConfig.freezeAfterGenesis()).thenReturn(Duration.ZERO);
        when(stateCommonConfig.savedStateDirectory()).thenReturn(Path.of("consistency-test"));
        when(consistencyTestingToolConfig.logfileDirectory()).thenReturn("consistency-test");

        stateLifecycle.onStateInitialized(state, platform, initTrigger, softwareVersion);

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

    @Test
    void handleConsensusRoundWithApplicationTransaction() {
        final var bytes = Bytes.wrap(new byte[] {1, 1, 1, 1, 1, 1, 1, 1});
        when(consensusTransaction.getApplicationTransaction()).thenReturn(bytes);

        doAnswer(invocation -> {
                    BiConsumer<ConsensusEvent, Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(event, consensusTransaction);
                    return null;
                })
                .when(round)
                .forEachEventTransaction(any());

        stateLifecycle.onHandleConsensusRound(round, state, consumer);

        assertThat(consumedTransactions).isEmpty();
    }

    @Test
    void handleConsensusRoundWithSystemTransaction() {
        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        doAnswer(invocation -> {
                    BiConsumer<ConsensusEvent, Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(event, consensusTransaction);
                    return null;
                })
                .when(round)
                .forEachEventTransaction(any());

        stateLifecycle.onHandleConsensusRound(round, state, consumer);

        assertThat(consumedTransactions).hasSize(1);
    }

    @Test
    void handleConsensusRoundWithMultipleSystemTransactions() {
        // Given
        final var secondConsensusTransaction = mock(TransactionWrapper.class);
        final var thirdConsensusTransaction = mock(TransactionWrapper.class);
        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        doAnswer(invocation -> {
                    BiConsumer<ConsensusEvent, Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(event, consensusTransaction);
                    consumer.accept(event, secondConsensusTransaction);
                    consumer.accept(event, thirdConsensusTransaction);
                    return null;
                })
                .when(round)
                .forEachEventTransaction(any());

        // When
        stateLifecycle.onHandleConsensusRound(round, state, consumer);

        assertThat(consumedTransactions).hasSize(3);
    }

    @Test
    void handleConsensusRoundWithDeprecatedSystemTransaction() {
        when(consensusTransaction.getApplicationTransaction()).thenReturn(Bytes.EMPTY);
        when(consensusTransaction.isSystem()).thenReturn(true);

        doAnswer(invocation -> {
                    BiConsumer<ConsensusEvent, Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(event, consensusTransaction);
                    return null;
                })
                .when(round)
                .forEachEventTransaction(any());

        stateLifecycle.onHandleConsensusRound(round, state, consumer);

        assertThat(consumedTransactions).isEmpty();
    }

    @Test
    void preHandleEventWithMultipleSystemTransactions() {
        final var secondConsensusTransaction = mock(TransactionWrapper.class);
        final var thirdConsensusTransaction = mock(TransactionWrapper.class);
        final var stateSignatureTransactionBytes =
                StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        doAnswer(invocation -> {
                    Consumer<Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(consensusTransaction);
                    consumer.accept(secondConsensusTransaction);
                    consumer.accept(thirdConsensusTransaction);
                    return null;
                })
                .when(event)
                .forEachTransaction(any());

        stateLifecycle.onPreHandle(event, state, consumer);

        assertThat(consumedTransactions).hasSize(3);
    }

    @Test
    void preHandleEventWithSystemTransaction() {
        final var emptyStateSignatureBytes = StateSignatureTransaction.PROTOBUF.toBytes(stateSignatureTransaction);
        when(consensusTransaction.getApplicationTransaction()).thenReturn(emptyStateSignatureBytes);

        doAnswer(invocation -> {
                    Consumer<Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(consensusTransaction);
                    return null;
                })
                .when(event)
                .forEachTransaction(any());

        stateLifecycle.onPreHandle(event, state, consumer);

        assertThat(consumedTransactions).hasSize(1);
    }

    @Test
    void preHandleEventWithApplicationTransaction() {
        final var bytes = Bytes.wrap(new byte[] {1, 1, 1, 1, 1, 1, 1, 1});
        when(consensusTransaction.getApplicationTransaction()).thenReturn(bytes);

        doAnswer(invocation -> {
                    Consumer<Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(consensusTransaction);
                    return null;
                })
                .when(event)
                .forEachTransaction(any());

        stateLifecycle.onPreHandle(event, state, consumer);

        assertThat(consumedTransactions).isEmpty();
    }

    @Test
    void preHandleEventWithDeprecatedSystemTransaction() {
        when(consensusTransaction.isSystem()).thenReturn(true);

        doAnswer(invocation -> {
                    Consumer<Transaction> consumer = invocation.getArgument(0);
                    consumer.accept(consensusTransaction);
                    return null;
                })
                .when(event)
                .forEachTransaction(any());

        stateLifecycle.onPreHandle(event, state, consumer);

        assertThat(consumedTransactions).isEmpty();
    }

    @Test
    void onSealDefaultsToTrue() {
        final boolean result = stateLifecycle.onSealConsensusRound(round, state);

        assertThat(result).isTrue();
    }
}
