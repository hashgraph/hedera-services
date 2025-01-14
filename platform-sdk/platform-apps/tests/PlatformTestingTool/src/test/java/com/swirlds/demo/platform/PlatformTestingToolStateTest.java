/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.APPLICATION_TRANSACTION;
import static com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType.STATE_SIGNATURE_TRANSACTION;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.crypto.internal.MerkleCryptoEngine;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.demo.platform.fs.stresstest.proto.RandomBytesTransaction;
import com.swirlds.demo.platform.fs.stresstest.proto.TestTransaction;
import com.swirlds.demo.platform.fs.stresstest.proto.TestTransactionWrapper;
import com.swirlds.platform.ParameterProvider;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.crypto.KeyGeneratingException;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.crypto.PublicStores;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class PlatformTestingToolStateTest {

    private static PlatformTestingToolState state;
    private static BasicSoftwareVersion softwareVersion;
    private static final byte[] EMPTY_ARRAY = new byte[] {};
    private PlatformTestingToolMain main;
    private Random random;
    private PlatformStateModifier platformStateModifier;
    private Round round;
    private Roster roster;
    private ConsensusEvent event;
    private PlatformEvent platformEvent;
    private Consumer<ScopedSystemTransaction<StateSignatureTransaction>> consumer;
    private List<ScopedSystemTransaction<StateSignatureTransaction>> consumedSystemTransactions;
    private Transaction transaction;
    private StateSignatureTransaction stateSignatureTransaction;
    private static MockedStatic<ParameterProvider> parameterProvider;
    private static ParameterProvider parameterProviderInstance;

    @BeforeAll
    static void initState() {
        final var payloadConfig = mock(PayloadCfgSimple.class);
        when(payloadConfig.isAppendSig()).thenReturn(true);

        softwareVersion = new BasicSoftwareVersion(1);
        state = new PlatformTestingToolState(
                FAKE_MERKLE_STATE_LIFECYCLES,
                version -> new BasicSoftwareVersion(softwareVersion.getSoftwareVersion()));
        FAKE_MERKLE_STATE_LIFECYCLES.initStates(state);
    }

    @BeforeEach
    void setUp() throws KeyStoreException, KeyGeneratingException, NoSuchAlgorithmException, NoSuchProviderException {
        main = new PlatformTestingToolMain();
        random = new Random();
        platformStateModifier = mock(PlatformStateModifier.class);
        roster = new Roster(Collections.EMPTY_LIST);
        transaction = mock(TransactionWrapper.class);
        platformEvent = mock(PlatformEvent.class);

        consumedSystemTransactions = new ArrayList<>();
        consumer = systemTransaction -> consumedSystemTransactions.add(systemTransaction);

        final Randotron randotron = Randotron.create();

        final var keysAndCerts =
                KeysAndCerts.generate("a-name", EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY, new PublicStores());

        final var signer = new PlatformSigner(keysAndCerts);
        final Hash stateHash = randotron.nextHash();
        final Bytes signature = signer.signImmutable(stateHash);

        stateSignatureTransaction = StateSignatureTransaction.newBuilder()
                .round(1000L)
                .signature(signature)
                .hash(stateHash.getBytes())
                .build();

        final var platform = mock(Platform.class);
        final var initTrigger = InitTrigger.GENESIS;
        final var futureHash = mock(Future.class);
        final var platformContext = mock(PlatformContext.class);
        final var metrics = mock(DefaultPlatformMetrics.class);
        final var cryptography = mock(MerkleCryptoEngine.class);
        when(platform.getRoster()).thenReturn(roster);
        when(platform.getSelfId()).thenReturn(NodeId.of(1L));
        when(platform.getRoster()).thenReturn(roster);
        when(platformContext.getMetrics()).thenReturn(metrics);
        when(platformContext.getMerkleCryptography()).thenReturn(cryptography);
        when(platform.getContext()).thenReturn(platformContext);
        when(platformContext.getMerkleCryptography()).thenReturn(cryptography);
        when(cryptography.digestTreeAsync(any())).thenReturn(futureHash);

        parameterProvider = mockStatic(ParameterProvider.class);
        parameterProviderInstance = mock(ParameterProvider.class);
        parameterProvider.when(ParameterProvider::getInstance).thenReturn(parameterProviderInstance);
        when(parameterProviderInstance.getParameters()).thenReturn(new String[] {"configs/FCM1KForTest.json"});
        state.init(platform, initTrigger, softwareVersion);
        state.initChildren();
    }

    @AfterEach
    void tearDown() {
        parameterProvider.close();
    }

    @Test
    void handleConsensusRoundWithApplicationTransactionOfRandomType() {
        // Given
        givenRoundAndEvent();

        final TestTransactionWrapper testTransactionWrapper = getTransactionWithRandomType(300);
        when(transaction.getApplicationTransaction()).thenReturn(Bytes.wrap(testTransactionWrapper.toByteArray()));

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    @Test
    void handleConsensusRoundWithSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final var stateSignatureTransactionBytes = main.encodeSystemTransaction(stateSignatureTransaction);
        when(transaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(1);
    }

    @Test
    void handleConsensusRoundWithMultipleSystemTransaction() {
        // Given
        givenRoundAndEvent();
        final var secondConsensusTransaction = mock(TransactionWrapper.class);
        final var thirdConsensusTransaction = mock(TransactionWrapper.class);
        when(platformEvent.consensusTransactionIterator())
                .thenReturn(List.of(
                                (ConsensusTransaction) transaction,
                                secondConsensusTransaction,
                                thirdConsensusTransaction)
                        .iterator());

        final var stateSignatureTransactionBytes = main.encodeSystemTransaction(stateSignatureTransaction);

        when(transaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(secondConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);
        when(thirdConsensusTransaction.getApplicationTransaction()).thenReturn(stateSignatureTransactionBytes);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(3);
    }

    @Test
    void handleConsensusRoundWithDeprecatedSystemTransaction() {
        // Given
        givenRoundAndEvent();

        final byte[] transactionBytes = new byte[300];
        random.nextBytes(transactionBytes);

        when(transaction.getApplicationTransaction()).thenReturn(Bytes.wrap(transactionBytes));
        when(transaction.isSystem()).thenReturn(true);

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    @Test
    void preHandleConsensusRoundWithApplicationTransactionOfRandomType() {
        // Given
        givenRoundAndEvent();

        final TestTransactionWrapper testTransactionWrapper = getTransactionWithRandomType(300);

        final var eventTransaction = new EventTransaction(
                new OneOf<>(APPLICATION_TRANSACTION, Bytes.wrap(testTransactionWrapper.toByteArray())));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(eventCore, null, List.of(eventTransaction), Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        platformEvent = new PlatformEvent(gossipEvent);

        // When
        state.preHandle(platformEvent, consumer);

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
        state.preHandle(event, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(1);
    }

    @Test
    void preHandleConsensusRoundWithMultipleSystemTransaction() {
        // Given
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
        state.preHandle(event, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isEqualTo(3);
    }

    @Test
    void preHandleConsensusRoundWithDeprecatedSystemTransaction() {
        // Given
        givenRoundAndEvent();
        when(transaction.isSystem()).thenReturn(true);

        final byte[] transactionBytes = new byte[300];
        random.nextBytes(transactionBytes);

        final var eventTransaction = new EventTransaction(new OneOf<>(STATE_SIGNATURE_TRANSACTION, transactionBytes));
        final var eventCore = mock(EventCore.class);
        final var gossipEvent = new GossipEvent(eventCore, null, List.of(eventTransaction), Collections.emptyList());
        when(eventCore.timeCreated()).thenReturn(Timestamp.DEFAULT);
        event = new PlatformEvent(gossipEvent);

        // When
        state.preHandle(event, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    private void givenRoundAndEvent() {
        when(platformEvent.getCreatorId()).thenReturn(new NodeId());
        when(platformEvent.getSoftwareVersion()).thenReturn(new SemanticVersion(1, 1, 1, "", ""));
        when(platformEvent.getConsensusTimestamp()).thenReturn(Instant.now());
        when(platformEvent.transactionIterator())
                .thenReturn(Collections.singletonList(transaction).iterator());
        when(platformEvent.consensusTransactionIterator())
                .thenReturn(Collections.singletonList((ConsensusTransaction) transaction)
                        .iterator());

        final var eventWindow = new EventWindow(10, 5, 20, AncientMode.BIRTH_ROUND_THRESHOLD);
        round = new ConsensusRound(
                roster,
                List.of(platformEvent),
                platformEvent,
                new Generations(),
                eventWindow,
                new ConsensusSnapshot(),
                false,
                Instant.now());
    }

    private TestTransactionWrapper getTransactionWithRandomType(final int transactionSize) {
        final byte[] transactionBytes = new byte[transactionSize];
        random.nextBytes(transactionBytes);

        final var randomBytesTransaction = RandomBytesTransaction.newBuilder()
                .setIsInserSeq(false)
                .setData(ByteString.copyFrom(transactionBytes))
                .build();

        final TestTransaction testTransaction = TestTransaction.newBuilder()
                .setBytesTransaction(randomBytesTransaction)
                .build();

        return TestTransactionWrapper.newBuilder()
                .setTestTransactionRawBytes(ByteString.copyFrom(testTransaction.toByteArray()))
                .build();
    }
}
