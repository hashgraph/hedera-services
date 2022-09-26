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
package com.hedera.services.fees.calculation.crypto.queries;

import static com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage.MISSING_RECORD_STANDIN;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.QueryUtils.queryOf;
import static com.hedera.test.utils.QueryUtils.txnRecordQuery;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.FeeCalcUtils;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.queries.meta.GetTxnRecordAnswer;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTxnRecordResourceUsageTest {
    private static final TransactionID targetTxnId =
            TransactionID.newBuilder()
                    .setAccountID(asAccount("0.0.2"))
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
                    .build();
    private static final TransactionID missingTxnId =
            TransactionID.newBuilder()
                    .setAccountID(asAccount("1.2.3"))
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
                    .build();
    private static final TransactionGetRecordQuery satisfiableAnswerOnly =
            txnRecordQuery(targetTxnId, ANSWER_ONLY);
    private static final TransactionGetRecordQuery satisfiableAnswerOnlyWithDups =
            txnRecordQuery(targetTxnId, ANSWER_ONLY, true);
    private static final TransactionGetRecordQuery satisfiableAnswerOnlyWithChildrenNoDups =
            txnRecordQuery(targetTxnId, ANSWER_ONLY, false, true);
    private static final TransactionGetRecordQuery satisfiableCostAnswer =
            txnRecordQuery(targetTxnId, COST_ANSWER);
    private static final TransactionGetRecordQuery unsatisfiable =
            txnRecordQuery(missingTxnId, ANSWER_ONLY);
    private static final Query satisfiableAnswerOnlyQuery = queryOf(satisfiableAnswerOnly);
    private static final Query satisfiableAnswerOnlyWithDupsQuery =
            queryOf(satisfiableAnswerOnlyWithDups);
    private static final Query satisfiableAnswerOnlyWithChildrenQuery =
            queryOf(satisfiableAnswerOnlyWithChildrenNoDups);
    private static final Query satisfiableCostAnswerQuery = queryOf(satisfiableCostAnswer);

    private StateView view;
    private RecordCache recordCache;
    private CryptoFeeBuilder usageEstimator;
    private TransactionRecord desiredRecord;
    private GetTxnRecordResourceUsage subject;
    private AnswerFunctions answerFunctions;

    @BeforeEach
    void setup() {
        desiredRecord = mock(TransactionRecord.class);

        usageEstimator = mock(CryptoFeeBuilder.class);
        recordCache = mock(RecordCache.class);
        final var children = new MutableStateChildren();
        view = new StateView(null, children, null);

        answerFunctions = mock(AnswerFunctions.class);
        given(answerFunctions.txnRecord(recordCache, satisfiableAnswerOnly))
                .willReturn(Optional.of(desiredRecord));
        given(answerFunctions.txnRecord(recordCache, satisfiableAnswerOnlyWithDups))
                .willReturn(Optional.of(desiredRecord));
        given(answerFunctions.txnRecord(recordCache, satisfiableCostAnswer))
                .willReturn(Optional.of(desiredRecord));
        given(answerFunctions.txnRecord(recordCache, unsatisfiable)).willReturn(Optional.empty());
        given(recordCache.getDuplicateRecords(targetTxnId)).willReturn(List.of(desiredRecord));

        subject = new GetTxnRecordResourceUsage(recordCache, answerFunctions, usageEstimator);
    }

    @Test
    void setsChildRecordsInQueryCtxIfAppropos() {
        final var answerOnlyUsage = mock(FeeData.class);
        final var queryCtx = new HashMap<String, Object>();
        final var mockedStatic = mockStatic(FeeCalcUtils.class);
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);
        given(recordCache.getChildRecords(targetTxnId)).willReturn(List.of(desiredRecord));
        given(answerFunctions.txnRecord(recordCache, satisfiableAnswerOnlyWithChildrenNoDups))
                .willReturn(Optional.of(desiredRecord));

        subject.usageGiven(satisfiableAnswerOnlyWithChildrenQuery, view, queryCtx);

        assertEquals(
                List.of(desiredRecord), queryCtx.get(GetTxnRecordAnswer.CHILD_RECORDS_CTX_KEY));

        mockedStatic.close();
    }

    @Test
    void invokesEstimatorAsExpectedForType() {
        final var costAnswerUsage = mock(FeeData.class);
        final var answerOnlyUsage = mock(FeeData.class);
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, COST_ANSWER))
                .willReturn(costAnswerUsage);
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);

        var costAnswerEstimate = subject.usageGiven(satisfiableCostAnswerQuery, view);
        var answerOnlyEstimate = subject.usageGiven(satisfiableAnswerOnlyQuery, view);
        assertSame(costAnswerUsage, costAnswerEstimate);
        assertSame(answerOnlyUsage, answerOnlyEstimate);

        costAnswerEstimate = subject.usageGivenType(satisfiableCostAnswerQuery, view, COST_ANSWER);
        answerOnlyEstimate = subject.usageGivenType(satisfiableAnswerOnlyQuery, view, ANSWER_ONLY);
        assertSame(costAnswerUsage, costAnswerEstimate);
        assertSame(answerOnlyUsage, answerOnlyEstimate);
    }

    @Test
    void returnsSummedUsagesIfDuplicatesPresent() {
        final var answerOnlyUsage = mock(FeeData.class);
        final var summedUsage = mock(FeeData.class);
        final var queryCtx = new HashMap<String, Object>();
        final var mockedStatic = mockStatic(FeeCalcUtils.class);
        mockedStatic
                .when(() -> FeeCalcUtils.sumOfUsages(answerOnlyUsage, answerOnlyUsage))
                .thenReturn(summedUsage);
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);

        final var usage = subject.usageGiven(satisfiableAnswerOnlyWithDupsQuery, view, queryCtx);

        assertEquals(summedUsage, usage);

        mockedStatic.close();
    }

    @Test
    void setsDuplicateRecordsInQueryCtxIfAppropos() {
        final var answerOnlyUsage = mock(FeeData.class);
        final var queryCtx = new HashMap<String, Object>();
        final var mockedStatic = mockStatic(FeeCalcUtils.class);
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);

        subject.usageGiven(satisfiableAnswerOnlyWithDupsQuery, view, queryCtx);

        assertEquals(
                List.of(desiredRecord), queryCtx.get(GetTxnRecordAnswer.DUPLICATE_RECORDS_CTX_KEY));

        mockedStatic.close();
    }

    @Test
    void setsPriorityRecordInQueryCxtIfPresent() {
        final var answerOnlyUsage = mock(FeeData.class);
        final var queryCtx = new HashMap<String, Object>();
        given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);

        subject.usageGiven(satisfiableAnswerOnlyQuery, view, queryCtx);

        assertEquals(desiredRecord, queryCtx.get(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY));
    }

    @Test
    void onlySetsPriorityRecordInQueryCxtIfFound() {
        final var answerOnlyUsage = mock(FeeData.class);
        final var queryCtx = new HashMap<String, Object>();
        given(
                        usageEstimator.getTransactionRecordQueryFeeMatrices(
                                MISSING_RECORD_STANDIN, ANSWER_ONLY))
                .willReturn(answerOnlyUsage);
        given(answerFunctions.txnRecord(recordCache, satisfiableAnswerOnly))
                .willReturn(Optional.empty());

        final var actual = subject.usageGiven(satisfiableAnswerOnlyQuery, view, queryCtx);

        assertFalse(queryCtx.containsKey(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY));
        assertSame(answerOnlyUsage, actual);
    }

    @Test
    void recognizesApplicableQueries() {
        assertTrue(subject.applicableTo(satisfiableAnswerOnlyQuery));
        assertFalse(subject.applicableTo(Query.getDefaultInstance()));
    }
}
