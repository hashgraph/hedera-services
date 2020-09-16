package com.hedera.services.queries.meta;

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


import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;
import static com.hedera.test.utils.TxnUtils.*;

@RunWith(JUnitPlatform.class)
class GetTxnRecordAnswerTest {
	private String payer = "0.0.12345";
	private TransactionID targetTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount(payer))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
			.build();
	private TransactionID missingTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount(payer))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(4_321L))
			.build();
	private ExpirableTxnRecord targetRecord = constructTargetRecord();
	private TransactionRecord cachedTargetRecord = targetRecord.asGrpc();

	private StateView view;
	private RecordCache recordCache;
	private AnswerFunctions answerFunctions;
	private OptionValidator optionValidator;
	private String node = "0.0.3";
	private long fee = 1_234L;
	private Transaction paymentTxn;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private GetTxnRecordAnswer subject;
	private PropertySource propertySource;

	@BeforeEach
	private void setup() {
		recordCache = mock(RecordCache.class);
		accounts = mock(FCMap.class);
		propertySource = mock(PropertySource.class);
		view = new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts, propertySource);
		optionValidator = mock(OptionValidator.class);
		answerFunctions = mock(AnswerFunctions.class);

		subject = new GetTxnRecordAnswer(recordCache, optionValidator, answerFunctions);
	}

	@Test
	public void getsExpectedPayment() throws Throwable {
		// given:
		Query query = getRecordQuery(targetTxnId, COST_ANSWER, 5L);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxn());
	}

	@Test
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setTransactionGetRecord(
				TransactionGetRecordResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = getRecordQuery(targetTxnId, COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTransactionGetRecord());
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	public void getsRecordWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
		given(answerFunctions.txnRecord(recordCache, view, sensibleQuery))
				.willReturn(Optional.of(cachedTargetRecord));

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		// and:
		verify(recordCache, never()).getDuplicateRecords(any());
	}

	@Test
	public void getsRecordFromCtxWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		ctx.put(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY, cachedTargetRecord);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		// and:
		verify(answerFunctions, never()).txnRecord(any(), any(), any());
	}

	@Test
	public void getsDuplicateRecordsFromCtxWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L, true);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		ctx.put(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY, cachedTargetRecord);
		ctx.put(GetTxnRecordAnswer.DUPLICATE_RECORDS_CTX_KEY, List.of(cachedTargetRecord));

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		assertEquals(List.of(cachedTargetRecord), opResponse.getDuplicateTransactionRecordsList());
	}

	@Test
	public void recognizesMissingRecordWhenCtxGiven() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, Collections.emptyMap());

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(RECORD_NOT_FOUND, opResponse.getHeader().getNodeTransactionPrecheckCode());
		verify(answerFunctions, never()).txnRecord(any(), any(), any());
	}

	@Test
	public void getsDuplicateRecordsWhenRequested() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L, true);
		given(answerFunctions.txnRecord(recordCache, view, sensibleQuery))
				.willReturn(Optional.of(cachedTargetRecord));
		given(recordCache.getDuplicateRecords(targetTxnId)).willReturn(List.of(cachedTargetRecord));

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		assertEquals(List.of(cachedTargetRecord), opResponse.getDuplicateTransactionRecordsList());
	}

	@Test
	public void recognizesUnavailableRecordFromMiss() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
		given(answerFunctions.txnRecord(recordCache, view, sensibleQuery))
				.willReturn(Optional.empty());

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(RECORD_NOT_FOUND, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	@Test
	public void respectsMetaValidity() throws Throwable {
		// given:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	@Test
	public void requiresAnswerOnlyPaymentButNotCostAnswer() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(getRecordQuery(targetTxnId, COST_ANSWER, 0)));
		assertTrue(subject.requiresNodePayment(getRecordQuery(targetTxnId, ANSWER_ONLY, 0)));
	}

	@Test
	public void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(getRecordQuery(targetTxnId, COST_ANSWER, 0)));
		assertFalse(subject.needsAnswerOnlyCost(getRecordQuery(targetTxnId, ANSWER_ONLY, 0)));
	}

	@Test
	public void syntaxCheckPrioritizesAccountStatus() throws Throwable {
		// setup:
		Query query = getRecordQuery(targetTxnId, ANSWER_ONLY, 123L);

		given(optionValidator.queryableAccountStatus(targetTxnId.getAccountID(), accounts)).willReturn(ACCOUNT_DELETED);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(ACCOUNT_DELETED, validity);
	}

	@Test
	public void syntaxCheckShortCircuitsOnDefaultAccountID() {
		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.checkValidity(Query.getDefaultInstance(), view));
	}

	@Test
	public void syntaxCheckOkForFindableRecord() throws Throwable {
		Query query = getRecordQuery(missingTxnId, ANSWER_ONLY, 123L);

		given(answerFunctions.txnRecord(recordCache, view, query)).willReturn(Optional.of(cachedTargetRecord));
		given(optionValidator.queryableAccountStatus(targetTxnId.getAccountID(), accounts)).willReturn(OK);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(OK, validity);
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(HederaFunctionality.TransactionGetRecord, subject.canonicalFunction());
	}

	Query getRecordQuery(TransactionID txnId, ResponseType type, long payment, boolean duplicates) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		TransactionGetRecordQuery.Builder op = TransactionGetRecordQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder()
						.setResponseType(type)
						.setPayment(paymentTxn))
				.setTransactionID(txnId)
				.setIncludeDuplicates(duplicates);
		return Query.newBuilder()
				.setTransactionGetRecord(op)
				.build();
	}

	Query getRecordQuery(TransactionID txnId, ResponseType type, long payment) throws Throwable {
		return getRecordQuery(txnId, type, payment, false);
	}

	ExpirableTxnRecord constructTargetRecord() {
		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder().setStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS))
				.setTransactionID(targetTxnId)
				.setMemo("Dim galleries, dusk winding stairs got past...")
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(9_999_999_999L))
				.setTransactionFee(555L)
				.setTransferList(withAdjustments(
						asAccount("0.0.2"), -2L,
						asAccount("0.0.2"), -2L,
						asAccount("0.0.1001"), 2L,
						asAccount("0.0.1002"), 2L))
				.build();
		return ExpirableTxnRecord.fromGprc(record);
	}
}
