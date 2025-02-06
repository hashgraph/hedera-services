/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.util.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_LIST_CONTAINS_NULL_VALUES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INNER_TRANSACTION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.spi.workflows.DispatchOptions.atomicBatchDispatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.AtomicBatchTransactionBody;
import com.hedera.node.app.service.util.impl.handlers.AtomicBatchHandler;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AtomicBatchHandlerTest {
    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock
    private StreamBuilder recordBuilder;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    private AtomicBatchHandler subject;

    private Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    private static final Key SIMPLE_KEY_A = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();
    private AccountID payerId1 = AccountID.newBuilder().accountNum(1001).build();
    private AccountID payerId2 = AccountID.newBuilder().accountNum(1002).build();
    private AccountID payerId3 = AccountID.newBuilder().accountNum(1003).build();

    @BeforeEach
    void setUp() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("atomicBatch.isEnabled", true)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);

        subject = new AtomicBatchHandler();
    }

    @Test
    // BATCH_38
    void batchWithNoTransactionsShouldFail() {
        final var txns = new ArrayList<Transaction>();
        txns.add(null);
        final var txnBody = newAtomicBatch(payerId1, consensusTimestamp, txns);
        given(handleContext.body()).willReturn(txnBody);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        willThrow(new HandleException(BATCH_LIST_CONTAINS_NULL_VALUES))
                .given(handleContext)
                .bodyFromTransaction(txnBody.atomicBatch().transactions().get(0));
    }

    @Test
    void cannotParseInnerTransactionFailed() {
        final var transaction = mock(Transaction.class);
        final var txnBody = newAtomicBatch(payerId1, consensusTimestamp, transaction);
        given(handleContext.body()).willReturn(txnBody);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        willThrow(new HandleException(INVALID_TRANSACTION_BODY))
                .given(handleContext)
                .bodyFromTransaction(transaction);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INNER_TRANSACTION_FAILED, msg.getStatus());
    }

    @Test
    void innerTransactionFailed() {
        final var transaction = mock(Transaction.class);
        final var txnBody = newAtomicBatch(payerId1, consensusTimestamp, transaction);
        final var innerTxnBody = newTxnBodyBuilder(payerId2, consensusTimestamp, SIMPLE_KEY_A)
                .consensusCreateTopic(
                        ConsensusCreateTopicTransactionBody.newBuilder().build())
                .build();
        given(handleContext.body()).willReturn(txnBody);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.bodyFromTransaction(transaction)).willReturn(innerTxnBody);
        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INNER_TRANSACTION_FAILED, msg.getStatus());
    }

    @Test
    void handleDispatched() {
        final var transaction = mock(Transaction.class);
        final var txnBody = newAtomicBatch(payerId1, consensusTimestamp, transaction);
        final var innerTxnBody = newTxnBodyBuilder(payerId2, consensusTimestamp, SIMPLE_KEY_A)
                .consensusCreateTopic(
                        ConsensusCreateTopicTransactionBody.newBuilder().build())
                .build();
        given(handleContext.body()).willReturn(txnBody);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.bodyFromTransaction(transaction)).willReturn(innerTxnBody);
        final var dispatchOptions = atomicBatchDispatch(payerId2, innerTxnBody, StreamBuilder.class);
        given(handleContext.dispatch(dispatchOptions)).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SUCCESS);
        subject.handle(handleContext);
        verify(handleContext).dispatch(dispatchOptions);
    }

    @Test
    void handleMultipleDispatched() {
        final var batchKey = SIMPLE_KEY_A;
        final var transaction1 = mock(Transaction.class);
        final var transaction2 = mock(Transaction.class);
        final var txnBody = newAtomicBatch(payerId1, consensusTimestamp, transaction1, transaction2);
        final var innerTxnBody1 = newTxnBodyBuilder(payerId2, consensusTimestamp, batchKey)
                .consensusCreateTopic(
                        ConsensusCreateTopicTransactionBody.newBuilder().build())
                .build();
        final var innerTxnBody2 = newTxnBodyBuilder(payerId3, consensusTimestamp, batchKey)
                .consensusDeleteTopic(
                        ConsensusDeleteTopicTransactionBody.newBuilder().build())
                .build();
        given(handleContext.body()).willReturn(txnBody);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.bodyFromTransaction(transaction1)).willReturn(innerTxnBody1);
        given(handleContext.bodyFromTransaction(transaction2)).willReturn(innerTxnBody2);
        final var dispatchOptions1 = atomicBatchDispatch(payerId2, innerTxnBody1, StreamBuilder.class);
        final var dispatchOptions2 = atomicBatchDispatch(payerId3, innerTxnBody2, StreamBuilder.class);
        given(handleContext.dispatch(dispatchOptions1)).willReturn(recordBuilder);
        given(handleContext.dispatch(dispatchOptions2)).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SUCCESS);
        subject.handle(handleContext);
        verify(handleContext).dispatch(dispatchOptions1);
        verify(handleContext).dispatch(dispatchOptions2);
    }

    // create atomic batch body
    private TransactionBody newAtomicBatch(
            AccountID payerId, Timestamp consensusTimestamp, List<Transaction> transactions) {
        final var atomicBatchBuilder = AtomicBatchTransactionBody.newBuilder().transactions(transactions);
        final var txnId = TransactionID.newBuilder()
                .accountID(payerId)
                .transactionValidStart(consensusTimestamp)
                .build();

        return TransactionBody.newBuilder()
                .atomicBatch(atomicBatchBuilder)
                .transactionID(txnId)
                .build();
    }

    private TransactionBody newAtomicBatch(
            AccountID payerId, Timestamp consensusTimestamp, Transaction... transactions) {
        return newAtomicBatch(payerId, consensusTimestamp, List.of(transactions));
    }

    // create inner txn body with a batch key
    private TransactionBody.Builder newTxnBodyBuilder(AccountID payerId, Timestamp consensusTimestamp, Key batchKey) {
        final var txnId = TransactionID.newBuilder()
                .accountID(payerId)
                .transactionValidStart(consensusTimestamp)
                .build();
        return TransactionBody.newBuilder().transactionID(txnId).batchKey(batchKey);
    }
}
