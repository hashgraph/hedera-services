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
import static org.mockito.Mockito.*;

import com.hedera.node.app.spi.meta.ErrorTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.Dispatcher;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
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
class PreHandleWorkflowImplTest {

    @Mock private TransactionMetadata metadata;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private SwirldTransaction transaction;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Dispatcher dispatcher;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private WorkflowOnset onset;

    @Mock private HederaState state;

    @Mock(strictness = Mock.Strictness.LENIENT)
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
                TransactionID.newBuilder().setAccountID(payerID).build();
        final TransactionBody txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setConsensusCreateTopic(content)
                        .build();
        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
        final HederaFunctionality functionality = HederaFunctionality.ConsensusCreateTopic;
        final OnsetResult onsetResult = new OnsetResult(txBody, OK, signatureMap, functionality);
        when(onset.parseAndCheck(any(), any(byte[].class))).thenReturn(onsetResult);

        when(dispatcher.dispatchPreHandle(state, txBody, payerID)).thenReturn(metadata);

        final Iterator<Transaction> iterator = List.of((Transaction) transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        when(transaction.getContents()).thenReturn(new byte[0]);

        workflow = new PreHandleWorkflowImpl(dispatcher, onset, RUN_INSTANTLY);
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
        assertThatThrownBy(() -> workflow.start(null, event))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.start(state, null))
                .isInstanceOf(NullPointerException.class);
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
    void testStartEventWithTwoTransactions(
            @Mock Event localEvent, @Mock SwirldTransaction transaction2) {
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
        final ArgumentCaptor<Future<TransactionMetadata>> captor =
                ArgumentCaptor.forClass(Future.class);
        verify(transaction).setMetadata(captor.capture());
        assertThat(captor.getValue()).succeedsWithin(Duration.ofMillis(100)).isEqualTo(metadata);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPreHandleOnsetCatastrophicFail(@Mock WorkflowOnset localOnset)
            throws PreCheckException {
        // given
        when(localOnset.parseAndCheck(any(), any(byte[].class)))
                .thenThrow(new PreCheckException(INVALID_TRANSACTION));
        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, RUN_INSTANTLY);

        // when
        workflow.start(state, event);

        // then
        final ArgumentCaptor<Future<TransactionMetadata>> captor =
                ArgumentCaptor.forClass(Future.class);
        verify(transaction).setMetadata(captor.capture());
        assertThat(captor.getValue())
                .succeedsWithin(Duration.ofMillis(100))
                .isInstanceOf(ErrorTransactionMetadata.class)
                .hasFieldOrPropertyWithValue("status", INVALID_TRANSACTION);
        verify(dispatcher, never()).dispatchPreHandle(eq(state), any(), any());
    }

    @Test
    void testPreHandleOnsetMildFail(@Mock WorkflowOnset localOnset) throws PreCheckException {
        // given
        final ConsensusCreateTopicTransactionBody content =
                ConsensusCreateTopicTransactionBody.newBuilder().build();
        final AccountID payerID = AccountID.newBuilder().build();
        final TransactionID transactionID =
                TransactionID.newBuilder().setAccountID(payerID).build();
        final TransactionBody txBody =
                TransactionBody.newBuilder()
                        .setTransactionID(transactionID)
                        .setConsensusCreateTopic(content)
                        .build();
        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
        final HederaFunctionality functionality = HederaFunctionality.ConsensusCreateTopic;
        final OnsetResult onsetResult =
                new OnsetResult(txBody, DUPLICATE_TRANSACTION, signatureMap, functionality);
        when(localOnset.parseAndCheck(any(), any(byte[].class))).thenReturn(onsetResult);

        workflow = new PreHandleWorkflowImpl(dispatcher, localOnset, RUN_INSTANTLY);

        // when
        workflow.start(state, event);

        // then
        verify(dispatcher).dispatchPreHandle(eq(state), eq(txBody), any());
    }
}
