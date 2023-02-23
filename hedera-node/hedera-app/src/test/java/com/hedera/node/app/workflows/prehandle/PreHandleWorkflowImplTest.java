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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleWorkflowImplTest {

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
    private HederaState state;

    @Mock(strictness = LENIENT)
    private Event event;

    private PreHandleWorkflowImpl workflow;

    private static final Function<Runnable, CompletableFuture<Void>> RUN_INSTANTLY = runnable -> {
        runnable.run();
        return CompletableFuture.completedFuture(null);
    };

    @BeforeEach
    void setup(@Mock ReadableStates readableStates) throws PreCheckException {
        when(state.createReadableStates(any())).thenReturn(readableStates);
        final ConsensusCreateTopicTransactionBody content =
                ConsensusCreateTopicTransactionBody.newBuilder().build();
        final AccountID payerID = AccountID.newBuilder().build();
        final TransactionID transactionID =
                TransactionID.newBuilder().setAccountID(payerID).build();
        final TransactionBody txBody = TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setConsensusCreateTopic(content)
                .build();
        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
        final HederaFunctionality functionality = HederaFunctionality.ConsensusCreateTopic;
        final OnsetResult onsetResult = new OnsetResult(txBody, txBody.toByteArray(), OK, signatureMap, functionality);
        when(onset.parseAndCheck(any(), any(byte[].class))).thenReturn(onsetResult);

        final Iterator<Transaction> iterator =
                List.of((Transaction) transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        when(transaction.getContents()).thenReturn(new byte[0]);

        workflow = new PreHandleWorkflowImpl(dispatcher, onset, signaturePreparer, cryptography, RUN_INSTANTLY);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters(@Mock ExecutorService executorService) {
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(null, dispatcher, onset, signaturePreparer, cryptography))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new PreHandleWorkflowImpl(executorService, null, onset, signaturePreparer, cryptography))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new PreHandleWorkflowImpl(executorService, dispatcher, null, signaturePreparer, cryptography))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(executorService, dispatcher, onset, null, cryptography))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(executorService, dispatcher, onset, signaturePreparer, null))
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

    @SuppressWarnings("JUnitMalformedDeclaration")
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
        // given
        when(localOnset.parseAndCheck(any(), any(byte[].class))).thenThrow(new PreCheckException(INVALID_TRANSACTION));
        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, signaturePreparer, cryptography, RUN_INSTANTLY);

        // when
        workflow.start(state, event);

        // then
        final ArgumentCaptor<TransactionMetadata> captor = ArgumentCaptor.forClass(TransactionMetadata.class);
        verify(transaction).setMetadata(captor.capture());
        assertThat(captor.getValue()).hasFieldOrPropertyWithValue("status", INVALID_TRANSACTION);
        verify(dispatcher, never()).dispatchPreHandle(any(), any());
    }

    @Test
    void testPreHandleOnsetMildFail(@Mock WorkflowOnset localOnset) throws PreCheckException {
        // given
        final ConsensusCreateTopicTransactionBody content =
                ConsensusCreateTopicTransactionBody.newBuilder().build();
        final AccountID payerID = AccountID.newBuilder().build();
        final TransactionID transactionID =
                TransactionID.newBuilder().setAccountID(payerID).build();
        final TransactionBody txBody = TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setConsensusCreateTopic(content)
                .build();
        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
        final HederaFunctionality functionality = HederaFunctionality.ConsensusCreateTopic;
        final OnsetResult onsetResult =
                new OnsetResult(txBody, txBody.toByteArray(), DUPLICATE_TRANSACTION, signatureMap, functionality);
        when(localOnset.parseAndCheck(any(), any(byte[].class))).thenReturn(onsetResult);

        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, signaturePreparer, cryptography, RUN_INSTANTLY);

        // when
        workflow.start(state, event);

        // then
        verify(dispatcher).dispatchPreHandle(any(), any());
    }
}
