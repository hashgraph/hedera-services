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
import static com.hedera.test.utils.QueryUtils.defaultPaymentTxn;
import static com.hedera.test.utils.QueryUtils.payer;
import static com.hedera.test.utils.QueryUtils.queryOf;
import static com.hedera.test.utils.QueryUtils.txnRecordQuery;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionGetRecordResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTxnRecordAnswerTest {
    private static final TransactionID targetTxnId =
            TransactionID.newBuilder()
                    .setAccountID(asAccount(payer))
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
                    .build();
    private static final TransactionID missingTxnId =
            TransactionID.newBuilder()
                    .setAccountID(asAccount(payer))
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(4_321L))
                    .build();
    private static final TransactionRecord cachedTargetRecord =
            TransactionRecord.newBuilder()
                    .setReceipt(
                            TransactionReceipt.newBuilder()
                                    .setStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS))
                    .setTransactionID(targetTxnId)
                    .setMemo("Dim galleries, dusk winding stairs got past...")
                    .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(9_999_999_999L))
                    .setTransactionFee(555L)
                    .setTransferList(
                            withAdjustments(
                                    asAccount("0.0.2"), -2L,
                                    asAccount("0.0.2"), -2L,
                                    asAccount("0.0.1001"), 2L,
                                    asAccount("0.0.1002"), 2L))
                    .build();

    private StateView view;
    private RecordCache recordCache;
    private AnswerFunctions answerFunctions;
    private OptionValidator optionValidator;
    private MerkleMap<EntityNum, MerkleAccount> accounts;

    private GetTxnRecordAnswer subject;
    private NodeLocalProperties nodeProps;

    @BeforeEach
    void setup() {
        recordCache = mock(RecordCache.class);
        accounts = mock(MerkleMap.class);
        nodeProps = mock(NodeLocalProperties.class);
        final MutableStateChildren children = new MutableStateChildren();
        children.setAccounts(AccountStorageAdapter.fromInMemory(accounts));
        view = new StateView(null, children, null);
        optionValidator = mock(OptionValidator.class);
        answerFunctions = mock(AnswerFunctions.class);

        subject = new GetTxnRecordAnswer(recordCache, optionValidator, answerFunctions);
    }

    @Test
    void getsExpectedPayment() {
        final var paymentTxn = defaultPaymentTxn(5L);
        final var query = queryOf(txnRecordQuery(targetTxnId, COST_ANSWER, paymentTxn));

        assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
    }

    @Test
    void getsValidity() {
        final var response =
                Response.newBuilder()
                        .setTransactionGetRecord(
                                TransactionGetRecordResponse.newBuilder()
                                        .setHeader(
                                                subject.answerOnlyHeader(
                                                        RESULT_SIZE_LIMIT_EXCEEDED)))
                        .build();

        assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
    }

    @Test
    void getsCostAnswerResponse() {
        final var fee = 1_234L;
        final var query = queryOf(txnRecordQuery(targetTxnId, COST_ANSWER, fee));

        final var response = subject.responseGiven(query, view, OK, fee);

        assertTrue(response.hasTransactionGetRecord());
        final var opResponse = response.getTransactionGetRecord();
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
        assertEquals(fee, opResponse.getHeader().getCost());
    }

    @Test
    void getsRecordWhenAvailable() {
        final var op = txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
        final var sensibleQuery = queryOf(op);
        given(answerFunctions.txnRecord(recordCache, op))
                .willReturn(Optional.of(cachedTargetRecord));

        final var response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
        verify(recordCache, never()).getDuplicateRecords(any());
    }

    @Test
    void getsRecordFromCtxWhenAvailable() {
        final var sensibleQuery = queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L));
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY, cachedTargetRecord);

        final var response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
        verify(answerFunctions, never()).txnRecord(any(), any());
    }

    @Test
    void getsDuplicateRecordsFromCtxWhenAvailable() {
        final var sensibleQuery = queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L, true));
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY, cachedTargetRecord);
        ctx.put(GetTxnRecordAnswer.DUPLICATE_RECORDS_CTX_KEY, List.of(cachedTargetRecord));

        final var response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
        assertEquals(List.of(cachedTargetRecord), opResponse.getDuplicateTransactionRecordsList());
    }

    @Test
    void getsChildRecordsFromCtxWhenAvailable() {
        final var q = queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L, false, true));
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY, cachedTargetRecord);
        ctx.put(GetTxnRecordAnswer.CHILD_RECORDS_CTX_KEY, List.of(cachedTargetRecord));

        final var response = subject.responseGiven(q, view, OK, 0L, ctx);

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
        assertEquals(List.of(cachedTargetRecord), opResponse.getChildTransactionRecordsList());
    }

    @Test
    void recognizesMissingRecordWhenCtxGiven() {
        final var sensibleQuery = queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L));

        final var response =
                subject.responseGiven(sensibleQuery, view, OK, 0L, Collections.emptyMap());

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(RECORD_NOT_FOUND, opResponse.getHeader().getNodeTransactionPrecheckCode());
        verify(answerFunctions, never()).txnRecord(any(), any());
    }

    @Test
    void getsDuplicateRecordsWhenRequested() {
        final var op = txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L, true);
        final var sensibleQuery = queryOf(op);
        given(answerFunctions.txnRecord(recordCache, op))
                .willReturn(Optional.of(cachedTargetRecord));
        given(recordCache.getDuplicateRecords(targetTxnId)).willReturn(List.of(cachedTargetRecord));

        final var response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
        assertEquals(List.of(cachedTargetRecord), opResponse.getDuplicateTransactionRecordsList());
    }

    @Test
    void getsChildRecordsWhenRequested() {
        final var op = txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L, false, true);
        final var sensibleQuery = queryOf(op);
        given(answerFunctions.txnRecord(recordCache, op))
                .willReturn(Optional.of(cachedTargetRecord));
        given(recordCache.getChildRecords(targetTxnId)).willReturn(List.of(cachedTargetRecord));

        final var response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
        assertEquals(List.of(cachedTargetRecord), opResponse.getChildTransactionRecordsList());
    }

    @Test
    void recognizesUnavailableRecordFromMiss() {
        final var op = txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
        final var sensibleQuery = queryOf(op);
        given(answerFunctions.txnRecord(recordCache, op)).willReturn(Optional.empty());

        final var response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        final var opResponse = response.getTransactionGetRecord();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(RECORD_NOT_FOUND, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void respectsMetaValidity() {
        final var sensibleQuery = queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 5L));

        final var response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

        final var opResponse = response.getTransactionGetRecord();
        assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void requiresAnswerOnlyPaymentButNotCostAnswer() {
        assertFalse(
                subject.requiresNodePayment(queryOf(txnRecordQuery(targetTxnId, COST_ANSWER, 0))));
        assertTrue(
                subject.requiresNodePayment(queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 0))));
    }

    @Test
    void requiresAnswerOnlyCostAsExpected() {
        assertTrue(
                subject.needsAnswerOnlyCost(queryOf(txnRecordQuery(targetTxnId, COST_ANSWER, 0))));
        assertFalse(
                subject.needsAnswerOnlyCost(queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 0))));
    }

    @Test
    void syntaxCheckPrioritizesAccountStatus() {
        final var query = queryOf(txnRecordQuery(targetTxnId, ANSWER_ONLY, 123L));
        given(optionValidator.queryableAccountStatus(eq(targetTxnId.getAccountID()), any()))
                .willReturn(ACCOUNT_DELETED);

        final var validity = subject.checkValidity(query, view);

        assertEquals(ACCOUNT_DELETED, validity);
    }

    @Test
    void syntaxCheckShortCircuitsOnDefaultAccountID() {
        assertEquals(INVALID_ACCOUNT_ID, subject.checkValidity(Query.getDefaultInstance(), view));
    }

    @Test
    void syntaxCheckOkForFindableRecord() {
        final var op = txnRecordQuery(missingTxnId, ANSWER_ONLY, 123L);
        final var query = queryOf(op);
        given(answerFunctions.txnRecord(recordCache, op))
                .willReturn(Optional.of(cachedTargetRecord));
        given(optionValidator.queryableAccountStatus(eq(targetTxnId.getAccountID()), any()))
                .willReturn(OK);

        final var validity = subject.checkValidity(query, view);

        assertEquals(OK, validity);
    }

    @Test
    void recognizesFunction() {
        assertEquals(HederaFunctionality.TransactionGetRecord, subject.canonicalFunction());
    }
}
