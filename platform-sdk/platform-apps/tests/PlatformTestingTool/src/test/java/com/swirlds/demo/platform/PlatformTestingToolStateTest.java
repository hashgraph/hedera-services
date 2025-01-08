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

import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.crypto.internal.MerkleCryptoEngine;
import com.swirlds.common.metrics.platform.DefaultPlatformMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.demo.platform.fs.stresstest.proto.RandomBytesTransaction;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.crypto.KeyGeneratingException;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.crypto.PublicStores;
import com.swirlds.platform.event.PlatformEvent;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PlatformTestingToolStateTest {

    private static PlatformTestingToolState state;
    private static BasicSoftwareVersion softwareVersion;
    private static final byte[] EMPTY_ARRAY = new byte[] {};
    private Random random;
    private PlatformStateModifier platformStateModifier;
    private Round round;
    private ConsensusEvent event;
    private Consumer<ScopedSystemTransaction<StateSignatureTransaction>> consumer;
    private List<ScopedSystemTransaction<StateSignatureTransaction>> consumedSystemTransactions;
    private Transaction transaction;
    private StateSignatureTransaction stateSignatureTransaction;

    @BeforeAll
    static void initState() {
        final var platformContext = mock(PlatformContext.class);
        final var metrics = mock(DefaultPlatformMetrics.class);
        final var cryptography = mock(MerkleCryptoEngine.class);
        final var roster = mock(Roster.class);
        final var platform = mock(Platform.class);
        final var initTrigger = InitTrigger.GENESIS;
        final var futureHash = mock(Future.class);

        when(platform.getRoster()).thenReturn(roster);
        when(platform.getSelfId()).thenReturn(NodeId.of(1L));
        when(platform.getRoster()).thenReturn(roster);
        when(platformContext.getMetrics()).thenReturn(metrics);
        when(platformContext.getMerkleCryptography()).thenReturn(cryptography);
        when(platform.getContext()).thenReturn(platformContext);
        when(platformContext.getMerkleCryptography()).thenReturn(cryptography);
        when(cryptography.digestTreeAsync(any())).thenReturn(futureHash);

        softwareVersion = new BasicSoftwareVersion(1);
        state = new PlatformTestingToolState(
                FAKE_MERKLE_STATE_LIFECYCLES,
                version -> new BasicSoftwareVersion(softwareVersion.getSoftwareVersion()));
        FAKE_MERKLE_STATE_LIFECYCLES.initStates(state);
        state.init(platform, initTrigger, softwareVersion);
    }

    @BeforeEach
    void setUp() throws KeyStoreException, KeyGeneratingException, NoSuchAlgorithmException, NoSuchProviderException {
        random = new Random();
        platformStateModifier = mock(PlatformStateModifier.class);
        event = mock(PlatformEvent.class);

        when(event.transactionIterator()).thenReturn(Collections.emptyIterator());
        round = mock(Round.class);

        consumedSystemTransactions = new ArrayList<>();
        consumer = systemTransaction -> consumedSystemTransactions.add(systemTransaction);
        transaction = mock(TransactionWrapper.class);

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
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100, 440, 600})
    void handleConsensusRoundWithApplicationTransaction(final Integer transactionSize) {
        // Given
        givenRoundAndEvent();

        final byte[] transactionBytes = new byte[transactionSize];
        random.nextBytes(transactionBytes);
        final var randomBytesTransaction = RandomBytesTransaction.newBuilder()
                .setIsInserSeq(true)
                .setData(ByteString.copyFrom(transactionBytes))
                .build();

        when(transaction.getApplicationTransaction()).thenReturn(Bytes.wrap(randomBytesTransaction.toByteArray()));

        // When
        state.handleConsensusRound(round, platformStateModifier, consumer);

        // Then
        assertThat(consumedSystemTransactions.size()).isZero();
    }

    private void givenRoundAndEvent() {
        when(event.getCreatorId()).thenReturn(new NodeId());
        when(event.getSoftwareVersion()).thenReturn(new SemanticVersion(1, 1, 1, "", ""));
        when(event.getConsensusTimestamp()).thenReturn(Instant.now());
        when(event.consensusTransactionIterator())
                .thenReturn(Collections.singletonList((ConsensusTransaction) transaction)
                        .iterator());
        when(round.iterator()).thenReturn(Collections.singletonList(event).iterator());
    }
}
