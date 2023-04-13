/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.prehandle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleWorkflowImplTest extends AppTestBase {
//
//    @Mock(strictness = LENIENT)
//    private TransactionSignature cryptoSig;
//
//    @Mock(strictness = LENIENT)
//    private JKey payerKey;
//
//    @Mock(strictness = LENIENT)
//    private SwirldTransaction transaction;
//
//    @Mock(strictness = LENIENT)
//    private TransactionDispatcher dispatcher;
//
//    @Mock(strictness = LENIENT)
//    private TransactionChecker transactionChecker;
//
//    @Mock(strictness = LENIENT)
//    private SignaturePreparer signaturePreparer;
//
//    @Mock(strictness = LENIENT)
//    private Cryptography cryptography;
//
//    @Mock(strictness = LENIENT)
//    private PreHandleContext context;
//
//    @Mock(strictness = LENIENT)
//    private HederaState state;
//
//    @Mock(strictness = LENIENT)
//    private Event event;
//
//    @Mock(strictness = LENIENT)
//    private MapReadableStates readableStates;
//
//    @Mock
//    private ReadableKVState accountState;
//
//    @Mock
//    private MerkleAccount payerAccount;
//
//    @Mock
//    private ConsensusTransactionImpl workflowTxn;
//
//    private PreHandleWorkflowImpl workflow;
//
//    private static final Function<Runnable, CompletableFuture<Void>> RUN_INSTANTLY = runnable -> {
//        runnable.run();
//        return CompletableFuture.completedFuture(null);
//    };
//
//    @BeforeEach
//    void setup() throws PreCheckException {
//        final ConsensusCreateTopicTransactionBody content =
//                ConsensusCreateTopicTransactionBody.newBuilder().build();
//        final AccountID payerID = AccountID.newBuilder().build();
//        final TransactionID transactionID =
//                TransactionID.newBuilder().accountID(payerID).build();
//        final TransactionBody txBody = TransactionBody.newBuilder()
//                .transactionID(transactionID)
//                .consensusCreateTopic(content)
//                .build();
//        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
//        final HederaFunctionality functionality = HederaFunctionality.CONSENSUS_CREATE_TOPIC;
//        final TransactionInfo txInfo = new TransactionInfo(
//                com.hedera.hapi.node.base.Transaction.newBuilder().build(), txBody, signatureMap, functionality);
//        when(transactionChecker.parseAndCheck(any())).thenReturn(txInfo);
//
//        final Iterator<Transaction> iterator =
//                List.of((Transaction) transaction).iterator();
//        when(event.transactionIterator()).thenReturn(iterator);
//
//        when(transaction.getContents()).thenReturn(new byte[0]);
//
//        final ExecutorService exec = Executors.newSingleThreadExecutor();
//        workflow = new PreHandleWorkflowImpl(
//                exec, dispatcher, transactionChecker, signaturePreparer, cryptography, RUN_INSTANTLY);
//    }
//
//    @Test
//    void verifiesExpandedSigsAsync() throws PreCheckException {
//        final AccountID payerID = AccountID.newBuilder().accountNum(1000L).build();
//        final TransactionID transactionID =
//                TransactionID.newBuilder().accountID(payerID).build();
//        final var onsetResult = new TransactionInfo(
//                com.hedera.hapi.node.base.Transaction.newBuilder().build(),
//                TransactionBody.newBuilder().transactionID(transactionID).build(),
//                SignatureMap.newBuilder().build(),
//                HederaFunctionality.CRYPTO_TRANSFER);
//        given(transactionChecker.parseAndCheck(any())).willReturn(onsetResult);
//        given(context.getStatus()).willReturn(DUPLICATE_TRANSACTION);
//        given(context.getPayerKey()).willReturn(payerKey);
//        given(context.getRequiredNonPayerKeys()).willReturn(Collections.emptyList());
//        given(signaturePreparer.prepareSignature(any(), any(), any(), any())).willReturn(cryptoSig);
//        given(workflowTxn.getContents()).willReturn(cryptoTransferContents());
//        given(state.createReadableStates(TokenService.NAME)).willReturn(readableStates);
//        given(readableStates.get("ACCOUNTS")).willReturn(accountState);
//        given(accountState.get(any())).willReturn(payerAccount);
//        given(payerAccount.getAccountKey()).willReturn(payerKey);
//
//        final var meta = workflow.preHandle(state, workflowTxn);
//
//        assertNotNull(meta);
//        verify(cryptography).verifyAsync(cryptoSig);
//    }
//
//    @SuppressWarnings("ConstantConditions")
//    @Test
//    void testConstructorWithIllegalParameters(@Mock ExecutorService executorService) {
//        assertThatThrownBy(() -> new PreHandleWorkflowImpl(
//                    null, dispatcher, transactionChecker, signaturePreparer, cryptography, recordCache))
//                .isInstanceOf(NullPointerException.class);
//        assertThatThrownBy(() -> new PreHandleWorkflowImpl(
//                        executorService, null, transactionChecker, signaturePreparer, cryptography))
//                .isInstanceOf(NullPointerException.class);
//        assertThatThrownBy(() ->
//                        new PreHandleWorkflowImpl(executorService, dispatcher, null, signaturePreparer, cryptography))
//                .isInstanceOf(NullPointerException.class);
//        assertThatThrownBy(() ->
//                        new PreHandleWorkflowImpl(executorService, dispatcher, transactionChecker, null, cryptography))
//                .isInstanceOf(NullPointerException.class);
//        assertThatThrownBy(() -> new PreHandleWorkflowImpl(
//                        executorService, dispatcher, transactionChecker, signaturePreparer, null))
//                .isInstanceOf(NullPointerException.class);
//    }
//
//    @SuppressWarnings("ConstantConditions")
//    @Test
//    void testStartWithIllegalParameters() {
//        // then
//        AssertionsForClassTypes.assertThatThrownBy(() -> workflow.start(null, event))
//                .isInstanceOf(NullPointerException.class);
//        AssertionsForClassTypes.assertThatThrownBy(() -> workflow.start(state, null))
//                .isInstanceOf(NullPointerException.class);
//    }
//
//    @Test
//    void testStartEventWithNoTransactions(@Mock Event localEvent) {
//        // given
//        when(localEvent.transactionIterator()).thenReturn(Collections.emptyIterator());
//
//        // when
//        assertThatCode(() -> workflow.start(state, localEvent)).doesNotThrowAnyException();
//    }
//
//    @Test
//    void testStartEventWithTwoTransactions(@Mock Event localEvent, @Mock SwirldTransaction transaction2) {
//        // given
//        final Iterator<Transaction> iterator =
//                List.of(transaction, (Transaction) transaction2).iterator();
//        when(localEvent.transactionIterator()).thenReturn(iterator);
//
//        // when
//        workflow.start(state, localEvent);
//
//        // then
//        verify(transaction).setMetadata(any());
//        verify(transaction2).setMetadata(any());
//    }
//
//    @SuppressWarnings("unchecked")
//    @Test
//    void testPreHandleSuccess() {
//        // when
//        workflow.start(state, event);
//
//        // then
//        final ArgumentCaptor<Future<PreHandleResult>> captor = ArgumentCaptor.forClass(Future.class);
//        verify(transaction).setMetadata(any());
//    }
//
//    @Test
//    void testPreHandleOnsetCatastrophicFail(@Mock TransactionChecker localOnset) throws PreCheckException {
//        // given
//        when(localOnset.parseAndCheck(any())).thenThrow(new PreCheckException(INVALID_TRANSACTION));
//        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, signaturePreparer, cryptography, RUN_INSTANTLY);
//
//        // when
//        workflow.start(state, event);
//
//        // then
//        final ArgumentCaptor<PreHandleResult> captor = ArgumentCaptor.forClass(PreHandleResult.class);
//        verify(transaction).setMetadata(captor.capture());
//        AssertionsForClassTypes.assertThat(captor.getValue())
//                .hasFieldOrPropertyWithValue("status", INVALID_TRANSACTION);
//        verify(dispatcher, never()).dispatchPreHandle(any(), any());
//    }
//
//    @Test
//    void testPreHandleOnsetMildFail(@Mock TransactionChecker localOnset) throws PreCheckException {
//        // given
//        final ConsensusCreateTopicTransactionBody content =
//                ConsensusCreateTopicTransactionBody.newBuilder().build();
//        final AccountID payerID = AccountID.newBuilder().build();
//        final TransactionID transactionID =
//                TransactionID.newBuilder().accountID(payerID).build();
//        final TransactionBody txBody = TransactionBody.newBuilder()
//                .transactionID(transactionID)
//                .consensusCreateTopic(content)
//                .build();
//        final var signedTxn = SignedTransaction.newBuilder()
//                .bodyBytes(PbjConverter.asWrappedBytes(TransactionBody.PROTOBUF, txBody))
//                .build();
//        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
//        final var txn = com.hedera.hapi.node.base.Transaction.newBuilder()
//                .signedTransactionBytes(PbjConverter.asWrappedBytes(SignedTransaction.PROTOBUF, signedTxn))
//                .sigMap(signatureMap)
//                .build();
//        final HederaFunctionality functionality = HederaFunctionality.CONSENSUS_CREATE_TOPIC;
//        final TransactionInfo onsetResult = new TransactionInfo(txn, txBody, signatureMap, functionality);
//        when(localOnset.parseAndCheck(any())).thenReturn(onsetResult);
//
//        given(transactionChecker.parseAndCheck(any())).willReturn(onsetResult);
//        given(context.getStatus()).willReturn(DUPLICATE_TRANSACTION);
//        given(context.getPayerKey()).willReturn(payerKey);
//        given(context.getRequiredNonPayerKeys()).willReturn(Collections.emptyList());
//        given(signaturePreparer.prepareSignature(any(), any(), any(), any())).willReturn(cryptoSig);
//        given(state.createReadableStates(TokenService.NAME)).willReturn(readableStates);
//        given(readableStates.get("ACCOUNTS")).willReturn(accountState);
//
//        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, signaturePreparer, cryptography, RUN_INSTANTLY);
//
//        // when
//        workflow.start(state, event);
//
//        verify(dispatcher).dispatchPreHandle(any(), any());
//    }
//
//    private byte[] cryptoTransferContents() {
//        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
//                .setSignedTransactionBytes(com.hederahashgraph.api.proto.java.SignedTransaction.newBuilder()
//                        .setBodyBytes(com.hederahashgraph.api.proto.java.TransactionBody.newBuilder()
//                                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
//                                .build()
//                                .toByteString())
//                        .build()
//                        .toByteString())
//                .build()
//                .toByteArray();
//    }
}
