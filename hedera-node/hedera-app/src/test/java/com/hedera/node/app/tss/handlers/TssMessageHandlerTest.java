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

package com.hedera.node.app.tss.handlers;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.tss.PlaceholderTssLibrary.SIGNATURE_SCHEMA;
import static com.hedera.node.app.tss.handlers.TssShareSignatureHandlerTest.PRIVATE_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.tss.TssCryptographyManager;
import com.hedera.node.app.tss.TssCryptographyManager.Vote;
import com.hedera.node.app.tss.TssDirectoryAccessor;
import com.hedera.node.app.tss.TssKeysAccessor;
import com.hedera.node.app.tss.TssMetrics;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.pairings.FakeGroupElement;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Signature;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import java.math.BigInteger;
import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssMessageHandlerTest {
    private static final AccountID NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(666L).build();
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private TssSubmissions submissionManager;

    @Mock
    private PreHandleContext preHandleContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private NodeInfo nodeInfo;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private NetworkInfo networkInfo;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TssCryptographyManager tssCryptographyManager;

    @Mock
    private PairingPublicKey pairingPublicKey;

    @Mock
    private Signature signature;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private WritableTssStore tssStore;

    @Mock
    private TssMetrics tssMetrics;

    @Mock
    private TssDirectoryAccessor directoryAccessor;

    private TssMessageHandler subject;
    private Vote vote;
    private TssParticipantDirectory tssParticipantDirectory = TssParticipantDirectory.createBuilder()
            .withParticipant(0, 1, PRIVATE_KEY.createPublicKey())
            .withSelf(0, PRIVATE_KEY)
            .build(SIGNATURE_SCHEMA);
    private final TssKeysAccessor.TssKeys TSS_KEYS =
            new TssKeysAccessor.TssKeys(List.of(), List.of(), Bytes.EMPTY, tssParticipantDirectory, 1);

    @BeforeEach
    void setUp() {
        final var voteBitSet = new BitSet(8);
        voteBitSet.set(2);
        vote = new Vote(pairingPublicKey, signature, voteBitSet);
        subject = new TssMessageHandler(submissionManager, tssCryptographyManager, tssMetrics, directoryAccessor);
    }

    @Test
    void nothingImplementedYet() {
        assertDoesNotThrow(() -> subject.preHandle(preHandleContext));
        assertDoesNotThrow(() -> subject.pureChecks(getTssBody()));
    }

    @Test
    void submitsVoteOnHandlingMessageWhenThresholdMet() {
        given(handleContext.networkInfo()).willReturn(networkInfo);
        given(handleContext.consensusNow()).willReturn(CONSENSUS_NOW);
        given(handleContext.configuration()).willReturn(DEFAULT_CONFIG);
        given(networkInfo.selfNodeInfo()).willReturn(nodeInfo);
        given(nodeInfo.accountId()).willReturn(NODE_ACCOUNT_ID);
        given(nodeInfo.nodeId()).willReturn(1L);
        given(handleContext.body()).willReturn(getTssBody());
        given(pairingPublicKey.publicKey()).willReturn(new FakeGroupElement(BigInteger.valueOf(10)));

        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssStore);

        given(tssCryptographyManager.getVoteFuture(
                        eq(getTssBody().tssMessageOrThrow().targetRosterHash()),
                        any(TssParticipantDirectory.class),
                        eq(handleContext)))
                .willReturn(CompletableFuture.completedFuture(vote));
        given(signature.getBytes()).willReturn(Bytes.wrap("test"));
        given(directoryAccessor.activeParticipantDirectory()).willReturn(TSS_KEYS.activeParticipantDirectory());

        subject.handle(handleContext);

        verify(submissionManager).submitTssVote(any(), eq(handleContext));
    }

    @Test
    public void testHandleException() {
        when(handleContext.body()).thenReturn(getTssBody());
        when(tssCryptographyManager.getVoteFuture(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated error"));

        // Execute the handler and ensure no vote is submitted
        assertThrows(RuntimeException.class, () -> subject.handle(handleContext));
        verify(submissionManager, never()).submitTssVote(any(), any());
    }

    public static TransactionBody getTssBody() {
        final Bytes targetRosterHash = Bytes.wrap("targetRoster".getBytes());
        final Bytes sourceRosterHash = Bytes.wrap("sourceRoster".getBytes());
        return TransactionBody.newBuilder()
                .tssMessage(TssMessageTransactionBody.newBuilder()
                        .tssMessage(Bytes.wrap("tssMessage".getBytes()))
                        .shareIndex(1)
                        .sourceRosterHash(sourceRosterHash)
                        .targetRosterHash(targetRosterHash)
                        .build())
                .build();
    }
}
