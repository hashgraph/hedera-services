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

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.signature.SigExpansionResult;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleWorkflowImplTest extends AppTestBase {

    @Mock(strictness = LENIENT)
    private TransactionSignature cryptoSig;

    @Mock(strictness = LENIENT)
    private HederaKey payerKey;

    @Mock(strictness = LENIENT)
    private ReadableStoreFactory storeFactory;

    @Mock(strictness = LENIENT)
    private SwirldTransaction transaction;

    @Mock(strictness = LENIENT)
    private TransactionDispatcher dispatcher;

    @Mock(strictness = LENIENT)
    private WorkflowOnset onset;

    @Mock(strictness = LENIENT)
    private SignaturePreparer signaturePreparer;

    @Mock(strictness = LENIENT)
    private Cryptography cryptography;

    @Mock(strictness = LENIENT)
    private PreHandleContext context;

    @Mock(strictness = LENIENT)
    private HederaState state;

    @Mock(strictness = LENIENT)
    private Event event;

    private PreHandleWorkflowImpl workflow;

    private static final Function<Supplier<?>, CompletableFuture<?>> RUN_INSTANTLY =
            supplier -> CompletableFuture.completedFuture(supplier.get());

    @BeforeEach
    void setup() throws PreCheckException {
        final ConsensusCreateTopicTransactionBody content =
                ConsensusCreateTopicTransactionBody.newBuilder().build();
        final AccountID payerID = AccountID.newBuilder().build();
        final TransactionID transactionID =
                TransactionID.newBuilder().accountID(payerID).build();
        final TransactionBody txBody = TransactionBody.newBuilder()
                .transactionID(transactionID)
                .consensusCreateTopic(content)
                .build();
        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
        final HederaFunctionality functionality = HederaFunctionality.CONSENSUS_CREATE_TOPIC;
        final OnsetResult onsetResult = new OnsetResult(txBody, OK, signatureMap, functionality);
        when(onset.parseAndCheck(any(), any(byte[].class))).thenReturn(onsetResult);

        final Iterator<Transaction> iterator =
                List.of((Transaction) transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        when(transaction.getContents()).thenReturn(new byte[0]);

        workflow = new PreHandleWorkflowImpl(dispatcher, onset, RUN_INSTANTLY);
    }

    @Test
    void resetsDuplicateClassification() {
        final var onsetResult = new OnsetResult(
                com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance(),
                TransactionBody.getDefaultInstance(),
                new byte[0],
                DUPLICATE_TRANSACTION,
                SignatureMap.getDefaultInstance(),
                HederaFunctionality.CryptoTransfer);
        given(context.getStatus()).willReturn(DUPLICATE_TRANSACTION);

        final var meta = workflow.dispatchForMetadata(onsetResult, context, storeFactory);

        assertNotNull(meta);
        verify(context).status(OK);
        verifyNoInteractions(signaturePreparer);
    }

    @Test
    void verifiesExpandedSigsAsync() {
        final var onsetResult = new OnsetResult(
                com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance(),
                TransactionBody.getDefaultInstance(),
                new byte[0],
                DUPLICATE_TRANSACTION,
                SignatureMap.getDefaultInstance(),
                HederaFunctionality.CryptoTransfer);
        given(context.getStatus()).willReturn(DUPLICATE_TRANSACTION);
        given(context.getPayerKey()).willReturn(payerKey);
        given(context.getRequiredNonPayerKeys()).willReturn(Collections.emptyList());
        given(signaturePreparer.expandedSigsFor(onsetResult.transaction(), payerKey, Collections.emptyList()))
                .willReturn(new SigExpansionResult(List.of(cryptoSig), OK));

        final var meta = workflow.dispatchForMetadata(onsetResult, context, storeFactory);

        assertNotNull(meta);
        verify(cryptography).verifyAsync(List.of(cryptoSig));
    }

    @Test
    void shortCircuitsIfExpandedSigsFail() {
        final var onsetResult = new OnsetResult(
                com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance(),
                TransactionBody.getDefaultInstance(),
                new byte[0],
                DUPLICATE_TRANSACTION,
                SignatureMap.getDefaultInstance(),
                HederaFunctionality.CryptoTransfer);
        given(context.getStatus()).willReturn(DUPLICATE_TRANSACTION);
        given(context.getPayerKey()).willReturn(payerKey);
        given(context.getRequiredNonPayerKeys()).willReturn(Collections.emptyList());
        given(signaturePreparer.expandedSigsFor(onsetResult.transaction(), payerKey, Collections.emptyList()))
                .willReturn(new SigExpansionResult(List.of(), INVALID_TOKEN_ID));

        final var meta = workflow.dispatchForMetadata(onsetResult, context, storeFactory);

        assertNotNull(meta);
        verify(context).status(INVALID_TOKEN_ID);
        verifyNoInteractions(cryptography);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters(@Mock ExecutorService executorService) {
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(null, dispatcher, onset))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(executorService, null, onset))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(executorService, dispatcher, null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testStartWithIllegalParameters() {
        // then
        assertThatThrownBy(() -> workflow.start(null, event)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.start(state, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testStartEventWithNoTransactions(@Mock Event localEvent) {
        // given
        when(localEvent.transactionIterator()).thenReturn(Collections.emptyIterator());

        // when
        assertThatCode(() -> workflow.start(state, localEvent)).doesNotThrowAnyException();
    }

    @Test
    void testStartEventWithTwoTransactions(@Mock Event localEvent, @Mock SwirldTransaction transaction2) {
        // given
        final Iterator<Transaction> iterator =
                List.of(transaction, (Transaction) transaction2).iterator();
        when(localEvent.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, localEvent);

        // then
        verify(transaction).setMetadata(any());
        verify(transaction2).setMetadata(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPreHandleSuccess() {
        // when
        workflow.start(state, event);

        // then
        final ArgumentCaptor<Future<TransactionMetadata>> captor = ArgumentCaptor.forClass(Future.class);
        verify(transaction).setMetadata(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPreHandleOnsetCatastrophicFail(@Mock WorkflowOnset localOnset) throws PreCheckException {
        //        // given
        //        when(localOnset.parseAndCheck(any(), any(byte[].class))).thenThrow(new
        // PreCheckException(INVALID_TRANSACTION));
        //        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, RUN_INSTANTLY);
        //
        //        // when
        //        workflow.start(state, event);
        //
        //        // then
        //        final ArgumentCaptor<Future<TransactionMetadata>> captor = ArgumentCaptor.forClass(Future.class);
        //        verify(transaction).setMetadata(captor.capture());
        //        assertThat(captor.getValue())
        //                .succeedsWithin(Duration.ofMillis(100))
        //                .isInstanceOf(ErrorTransactionMetadata.class)
        //                .hasFieldOrPropertyWithValue("status", INVALID_TRANSACTION);
        //        verify(dispatcher, never()).dispatchPreHandle(eq(state), any(), any());
    }

    @Test
    void testPreHandleOnsetMildFail(@Mock WorkflowOnset localOnset) throws PreCheckException {
        // given
        final ConsensusCreateTopicTransactionBody content =
                ConsensusCreateTopicTransactionBody.newBuilder().build();
        final AccountID payerID = AccountID.newBuilder().build();
        final TransactionID transactionID =
                TransactionID.newBuilder().accountID(payerID).build();
        final TransactionBody txBody = TransactionBody.newBuilder()
                .transactionID(transactionID)
                .consensusCreateTopic(content)
                .build();
        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
        final HederaFunctionality functionality = HederaFunctionality.CONSENSUS_CREATE_TOPIC;
        final OnsetResult onsetResult = new OnsetResult(txBody, DUPLICATE_TRANSACTION, signatureMap, functionality);
        when(localOnset.parseAndCheck(any(), any(byte[].class))).thenReturn(onsetResult);

        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, RUN_INSTANTLY);

        // when
        workflow.start(state, event);

        // then
        //        verify(dispatcher).dispatchPreHandle(eq(state), eq(txBody), any());
    }
}
