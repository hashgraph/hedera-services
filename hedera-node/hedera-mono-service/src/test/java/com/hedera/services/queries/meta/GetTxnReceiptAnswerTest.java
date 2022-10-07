/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.meta;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTxnReceiptAnswerTest {
    private TransactionID validTxnId =
            TransactionID.newBuilder()
                    .setAccountID(asAccount("0.0.2"))
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
                    .build();
    private TxnReceipt receipt =
            TxnReceipt.newBuilder().setStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS.name()).build();
    private TxnReceipt duplicateReceipt =
            TxnReceipt.newBuilder().setStatus(DUPLICATE_TRANSACTION.name()).build();
    private TxnReceipt unclassifiableReceipt =
            TxnReceipt.newBuilder().setStatus(INVALID_NODE_ACCOUNT.name()).build();

    StateView view;
    RecordCache recordCache;

    GetTxnReceiptAnswer subject;

    @BeforeEach
    void setup() {
        view = null;
        recordCache = mock(RecordCache.class);

        subject = new GetTxnReceiptAnswer(recordCache);
    }

    @Test
    void returnsChildrenIfRequested() {
        // setup:
        Query sensibleQuery = queryWith(validTxnId, ANSWER_ONLY, false, true);
        var childReceipts = List.of(duplicateReceipt.toGrpc(), unclassifiableReceipt.toGrpc());

        given(recordCache.getPriorityReceipt(validTxnId)).willReturn(receipt);
        given(recordCache.getChildReceipts(validTxnId)).willReturn(childReceipts);

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        TransactionGetReceiptResponse opResponse = response.getTransactionGetReceipt();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
        assertEquals(receipt.toGrpc(), opResponse.getReceipt());
        assertEquals(childReceipts, opResponse.getChildTransactionReceiptsList());
    }

    @Test
    void requiresNothing() {
        // setup:
        TransactionGetReceiptQuery costAnswerOp =
                TransactionGetReceiptQuery.newBuilder()
                        .setHeader(
                                QueryHeader.newBuilder().setResponseType(ResponseType.COST_ANSWER))
                        .build();
        Query costAnswerQuery = Query.newBuilder().setTransactionGetReceipt(costAnswerOp).build();
        TransactionGetReceiptQuery answerOnlyOp =
                TransactionGetReceiptQuery.newBuilder()
                        .setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY))
                        .build();
        Query answerOnlyQuery = Query.newBuilder().setTransactionGetReceipt(answerOnlyOp).build();

        // expect:
        assertFalse(subject.requiresNodePayment(costAnswerQuery));
        assertFalse(subject.requiresNodePayment(answerOnlyQuery));
        assertFalse(subject.needsAnswerOnlyCost(answerOnlyQuery));
        assertFalse(subject.needsAnswerOnlyCost(costAnswerQuery));
    }

    @Test
    void rejectsQueryForMissingReceipt() {
        // setup:
        Query sensibleQuery = queryWith(validTxnId);

        given(recordCache.getPriorityReceipt(validTxnId)).willReturn(null);

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        TransactionGetReceiptResponse opResponse = response.getTransactionGetReceipt();
        assertEquals(RECEIPT_NOT_FOUND, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void returnsDuplicatesIfRequested() {
        // setup:
        Query sensibleQuery = queryWith(validTxnId, ANSWER_ONLY, true);
        var duplicateReceipts = List.of(duplicateReceipt.toGrpc(), unclassifiableReceipt.toGrpc());

        given(recordCache.getPriorityReceipt(validTxnId)).willReturn(receipt);
        given(recordCache.getDuplicateReceipts(validTxnId)).willReturn(duplicateReceipts);

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        TransactionGetReceiptResponse opResponse = response.getTransactionGetReceipt();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
        assertEquals(receipt.toGrpc(), opResponse.getReceipt());
        assertEquals(duplicateReceipts, opResponse.getDuplicateTransactionReceiptsList());
    }

    @Test
    void shortCircuitsToAnswerOnly() {
        // setup:
        Query sensibleQuery = queryWith(validTxnId, ResponseType.COST_ANSWER);

        given(recordCache.getPriorityReceipt(validTxnId)).willReturn(receipt);

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        TransactionGetReceiptResponse opResponse = response.getTransactionGetReceipt();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
        assertEquals(receipt.toGrpc(), opResponse.getReceipt());
        assertTrue(opResponse.getDuplicateTransactionReceiptsList().isEmpty());
        verify(recordCache, never()).getDuplicateReceipts(any());
    }

    @Test
    void getsValidity() {
        // given:
        Response response =
                Response.newBuilder()
                        .setTransactionGetReceipt(
                                TransactionGetReceiptResponse.newBuilder()
                                        .setHeader(
                                                subject.answerOnlyHeader(
                                                        RESULT_SIZE_LIMIT_EXCEEDED)))
                        .build();

        // expect:
        assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
    }

    @Test
    void respectsMetaValidity() {
        // given:
        Query sensibleQuery = queryWith(validTxnId);

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

        // then:
        TransactionGetReceiptResponse opResponse = response.getTransactionGetReceipt();
        assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
        // and:
        verify(recordCache, never()).isReceiptPresent(any());
    }

    @Test
    void expectsNonDefaultTransactionId() {
        // setup:
        Query nonsenseQuery = queryWith(TransactionID.getDefaultInstance());
        Query sensibleQuery = queryWith(validTxnId);

        // expect:
        assertEquals(OK, subject.checkValidity(sensibleQuery, view));
        assertEquals(INVALID_TRANSACTION_ID, subject.checkValidity(nonsenseQuery, view));
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(HederaFunctionality.TransactionGetReceipt, subject.canonicalFunction());
    }

    @Test
    void hasNoPayment() {
        // expect:
        assertFalse(subject.extractPaymentFrom(mock(Query.class)).isPresent());
    }

    private Query queryWith(TransactionID txnId, ResponseType type, boolean duplicates) {
        return queryWith(txnId, type, duplicates, false);
    }

    private Query queryWith(
            TransactionID txnId, ResponseType type, boolean duplicates, boolean children) {
        TransactionGetReceiptQuery.Builder op =
                TransactionGetReceiptQuery.newBuilder()
                        .setHeader(QueryHeader.newBuilder().setResponseType(type))
                        .setTransactionID(txnId)
                        .setIncludeDuplicates(duplicates)
                        .setIncludeChildReceipts(children);
        return Query.newBuilder().setTransactionGetReceipt(op).build();
    }

    private Query queryWith(TransactionID txnId, ResponseType type) {
        return queryWith(txnId, type, false);
    }

    private Query queryWith(TransactionID txnId) {
        return queryWith(txnId, ANSWER_ONLY);
    }
}
