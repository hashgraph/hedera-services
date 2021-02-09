package com.hedera.services.queries.meta;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
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
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.test.utils.IdUtils.asAccount;

class GetTxnReceiptAnswerTest {
	private TransactionID validTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount("0.0.2"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
			.build();
	private TransactionReceipt receipt = TransactionReceipt.newBuilder()
			.setStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS)
			.build();
	private TransactionReceipt duplicateReceipt = TransactionReceipt.newBuilder()
			.setStatus(DUPLICATE_TRANSACTION)
			.build();
	private TransactionReceipt unclassifiableReceipt = TransactionReceipt.newBuilder()
			.setStatus(INVALID_NODE_ACCOUNT)
			.build();

	StateView view;
	RecordCache recordCache;

	GetTxnReceiptAnswer subject;

	@BeforeEach
	private void setup() {
		view = null;
		recordCache = mock(RecordCache.class);

		subject = new GetTxnReceiptAnswer(recordCache);
	}

	@Test
	public void requiresNothing() {
		// setup:
		TransactionGetReceiptQuery costAnswerOp = TransactionGetReceiptQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ResponseType.COST_ANSWER))
				.build();
		Query costAnswerQuery = Query.newBuilder().setTransactionGetReceipt(costAnswerOp).build();
		TransactionGetReceiptQuery answerOnlyOp = TransactionGetReceiptQuery.newBuilder()
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
	public void rejectsQueryForMissingReceipt() {
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
	public void returnsDuplicatesIfRequested() {
		// setup:
		Query sensibleQuery = queryWith(validTxnId, ANSWER_ONLY, true);
		var duplicateReceipts = List.of(duplicateReceipt, unclassifiableReceipt);

		given(recordCache.getPriorityReceipt(validTxnId)).willReturn(receipt);
		given(recordCache.getDuplicateReceipts(validTxnId)).willReturn(duplicateReceipts);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		TransactionGetReceiptResponse opResponse = response.getTransactionGetReceipt();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
		assertEquals(receipt, opResponse.getReceipt());
		assertEquals(duplicateReceipts, opResponse.getDuplicateTransactionReceiptsList());
	}

	@Test
	public void shortCircuitsToAnswerOnly() {
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
		assertEquals(receipt, opResponse.getReceipt());
		assertTrue(opResponse.getDuplicateTransactionReceiptsList().isEmpty());
		verify(recordCache, never()).getDuplicateReceipts(any());
	}

	@Test
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setTransactionGetReceipt(
				TransactionGetReceiptResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	public void respectsMetaValidity() {
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
	public void expectsNonDefaultTransactionId() {
		// setup:
		Query nonsenseQuery = queryWith(TransactionID.getDefaultInstance());
		Query sensibleQuery = queryWith(validTxnId);

		// expect:
		assertEquals(OK, subject.checkValidity(sensibleQuery, view));
		assertEquals(INVALID_TRANSACTION_ID, subject.checkValidity(nonsenseQuery, view));
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(HederaFunctionality.TransactionGetReceipt, subject.canonicalFunction());
	}

	@Test
	public void hasNoPayment() {
		// expect:
		assertFalse(subject.extractPaymentFrom(mock(Query.class)).isPresent());
	}

	private Query queryWith(TransactionID txnId, ResponseType type, boolean duplicates) {
		TransactionGetReceiptQuery.Builder op = TransactionGetReceiptQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(type))
				.setTransactionID(txnId)
				.setIncludeDuplicates(duplicates);
		return Query.newBuilder().setTransactionGetReceipt(op).build();
	}

	private Query queryWith(TransactionID txnId, ResponseType type) {
		return queryWith(txnId, type, false);
	}

	private Query queryWith(TransactionID txnId) {
		return queryWith(txnId, ANSWER_ONLY);
	}
}
