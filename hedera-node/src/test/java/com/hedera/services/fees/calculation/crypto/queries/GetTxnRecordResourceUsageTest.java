package com.hedera.services.fees.calculation.crypto.queries;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.fees.calculation.FeeCalcUtils;
import com.hedera.services.queries.meta.GetTxnRecordAnswer;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseType.*;

@RunWith(JUnitPlatform.class)
class GetTxnRecordResourceUsageTest {
	private TransactionID targetTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount("0.0.2"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
			.build();
	private TransactionID missingTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount("1.2.3"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
			.build();
	Query satisfiableAnswerOnly = txnRecordQuery(targetTxnId, ANSWER_ONLY);
	Query satisfiableAnswerOnlyWithDups = txnRecordQuery(targetTxnId, ANSWER_ONLY, true);
	Query satisfiableCostAnswer = txnRecordQuery(targetTxnId, COST_ANSWER);
	Query unsatisfiable = txnRecordQuery(missingTxnId, ANSWER_ONLY);

	StateView view;
	RecordCache recordCache;
	CryptoFeeBuilder usageEstimator;
	TransactionRecord desiredRecord;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	GetTxnRecordResourceUsage subject;
	AnswerFunctions answerFunctions;

	@BeforeEach
	private void setup() throws Throwable {
		desiredRecord = mock(TransactionRecord.class);

		usageEstimator = mock(CryptoFeeBuilder.class);
		recordCache = mock(RecordCache.class);
		accounts = mock(FCMap.class);
		view = new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts);

		answerFunctions = mock(AnswerFunctions.class);
		given(answerFunctions.txnRecord(recordCache, view, satisfiableAnswerOnly))
				.willReturn(Optional.of(desiredRecord));
		given(answerFunctions.txnRecord(recordCache, view, satisfiableAnswerOnlyWithDups))
				.willReturn(Optional.of(desiredRecord));
		given(answerFunctions.txnRecord(recordCache, view, satisfiableCostAnswer))
				.willReturn(Optional.of(desiredRecord));
		given(answerFunctions.txnRecord(recordCache, view, unsatisfiable))
				.willReturn(Optional.empty());
		given(recordCache.getDuplicateRecords(targetTxnId)).willReturn(List.of(desiredRecord));

		subject = new GetTxnRecordResourceUsage(recordCache, answerFunctions, usageEstimator);
	}

	@Test
	public void invokesEstimatorAsExpectedForType() {
		// setup:
		FeeData costAnswerUsage = mock(FeeData.class);
		FeeData answerOnlyUsage = mock(FeeData.class);

		// given:
		given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, COST_ANSWER))
				.willReturn(costAnswerUsage);
		given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		FeeData costAnswerEstimate = subject.usageGiven(satisfiableCostAnswer, view);
		FeeData answerOnlyEstimate = subject.usageGiven(satisfiableAnswerOnly, view);

		// then:
		assertTrue(costAnswerEstimate == costAnswerUsage);
		assertTrue(answerOnlyEstimate == answerOnlyUsage);
	}

	@Test
	public void returnsSummedUsagesIfDuplicatesPresent() {
		// setup:
		FeeData answerOnlyUsage = mock(FeeData.class);
		FeeData summedUsage = mock(FeeData.class);
		var queryCtx = new HashMap<String, Object>();
		var sumFn = mock(BinaryOperator.class);
		GetTxnRecordResourceUsage.sumFn = sumFn;

		// given:
		given(sumFn.apply(answerOnlyUsage, answerOnlyUsage)).willReturn(summedUsage);
		given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnlyWithDups, view, queryCtx);

		// then:
		assertEquals(summedUsage, usage);

		// cleanup:
		GetTxnRecordResourceUsage.sumFn = FeeCalcUtils::sumOfUsages;
	}

	@Test
	public void setsDuplicateRecordsInQueryCtxIfAppropos() {
		// setup:
		FeeData answerOnlyUsage = mock(FeeData.class);
		var queryCtx = new HashMap<String, Object>();
		var sumFn = mock(BinaryOperator.class);
		GetTxnRecordResourceUsage.sumFn = sumFn;

		// given:
		given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		subject.usageGiven(satisfiableAnswerOnlyWithDups, view, queryCtx);

		// then:
		assertEquals(List.of(desiredRecord), queryCtx.get(GetTxnRecordAnswer.DUPLICATE_RECORDS_CTX_KEY));

		// cleanup:
		GetTxnRecordResourceUsage.sumFn = FeeCalcUtils::sumOfUsages;
	}

	@Test
	public void setsPriorityRecordInQueryCxtIfPresent() {
		// setup:
		FeeData answerOnlyUsage = mock(FeeData.class);
		var queryCtx = new HashMap<String, Object>();

		// given:
		given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);

		// when:
		subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertEquals(desiredRecord, queryCtx.get(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY));
	}

	@Test
	public void setsOnlyFoundPriorityRecordInQueryCxtIfPresent() {
		// setup:
		FeeData answerOnlyUsage = mock(FeeData.class);
		var queryCtx = new HashMap<String, Object>();

		// given:
		given(usageEstimator.getTransactionRecordQueryFeeMatrices(desiredRecord, ANSWER_ONLY))
				.willReturn(answerOnlyUsage);
		given(answerFunctions.txnRecord(recordCache, view, satisfiableAnswerOnly))
				.willReturn(Optional.empty());

		// when:
		subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertFalse(queryCtx.containsKey(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY));
	}

	@Test
	public void rethrowsIae() {
		// given:
		Query query = txnRecordQuery(missingTxnId, ANSWER_ONLY);
		given(usageEstimator.getTransactionRecordQueryFeeMatrices(any(), any()))
				.willThrow(IllegalStateException.class);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(query, view));
	}

	@Test
	public void recognizesApplicableQueries() {
		// given:
		Query no = nonTxnRecordQuery();
		Query yes = txnRecordQuery(targetTxnId, ANSWER_ONLY);

		// expect:
		assertTrue(subject.applicableTo(yes));
		assertFalse(subject.applicableTo(no));
	}

	Query nonTxnRecordQuery() {
		return Query.getDefaultInstance();
	}


	Query txnRecordQuery(TransactionID txnId, ResponseType type) {
		return txnRecordQuery(txnId, type, false);
	}

	Query txnRecordQuery(TransactionID txnId, ResponseType type, boolean duplicates) {
		TransactionGetRecordQuery.Builder op = TransactionGetRecordQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(type))
				.setTransactionID(txnId)
				.setIncludeDuplicates(duplicates);
		return Query.newBuilder()
				.setTransactionGetRecord(op)
				.build();
	}
}
