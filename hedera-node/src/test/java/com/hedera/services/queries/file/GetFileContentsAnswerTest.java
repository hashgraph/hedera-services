package com.hedera.services.queries.file;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Arrays;
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
class GetFileContentsAnswerTest {
	int size = 1_234;
	byte[] data = "SOMETHING".getBytes();
	long expiry = 2_000_000L;
	private String node = "0.0.3";
	private String payer = "0.0.12345";
	private String target = "0.0.123";
	private Transaction paymentTxn;
	private long fee = 1_234L;
	FileGetInfoResponse.FileInfo info;

	OptionValidator optionValidator;
	StateView view;

	GetFileContentsAnswer subject;

	@BeforeEach
	private void setup() throws Throwable {
		info = FileGetInfoResponse.FileInfo.newBuilder()
				.setDeleted(false)
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.setFileID(asFile(target))
				.setSize(size)
				.setKeys(TxnHandlingScenario.MISC_FILE_WACL_KT.asKey().getKeyList())
				.build();

		view = mock(StateView.class);
		optionValidator = mock(OptionValidator.class);

		subject = new GetFileContentsAnswer(optionValidator);
	}

	@Test
	public void usesValidator() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		given(optionValidator.queryableFileStatus(asFile(target), view)).willReturn(FILE_DELETED);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(FILE_DELETED, validity);
		// and:
		verify(optionValidator).queryableFileStatus(any(), any());
	}

	@Test
	public void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, target)));
		assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void requiresAnswerOnlyPayment() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, target)));
		assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void getsExpectedPayment() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee, target);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxn());
	}

	@Test
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setFileGetContents(
				FileGetContentsResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(HederaFunctionality.FileGetContents, subject.canonicalFunction());
	}

	@Test
	public void getsTheContents() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, target);

		given(view.contentsOf(asFile(target))).willReturn(Optional.of(data));

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasFileGetContents());
		assertEquals(asFile(target), response.getFileGetContents().getFileContents().getFileID());
		assertTrue(response.getFileGetContents().hasHeader(), "Missing response header!");
		assertEquals(OK, response.getFileGetContents().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, response.getFileGetContents().getHeader().getResponseType());
		assertEquals(fee, response.getFileGetContents().getHeader().getCost());
		// and:
		var actual = response.getFileGetContents().getFileContents().getContents().toByteArray();
		assertTrue(Arrays.equals(data, actual));
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasFileGetContents());
		assertEquals(asFile(target), response.getFileGetContents().getFileContents().getFileID());
		assertEquals(OK, response.getFileGetContents().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getFileGetContents().getHeader().getResponseType());
		assertEquals(fee, response.getFileGetContents().getHeader().getCost());
	}

	@Test
	public void getsInvalidResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, FILE_DELETED, fee);

		// then:
		assertTrue(response.hasFileGetContents());
		assertEquals(asFile(target), response.getFileGetContents().getFileContents().getFileID());
		assertEquals(FILE_DELETED, response.getFileGetContents().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getFileGetContents().getHeader().getResponseType());
		assertEquals(fee, response.getFileGetContents().getHeader().getCost());
	}

	private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		FileGetContentsQuery.Builder op = FileGetContentsQuery.newBuilder()
				.setHeader(header)
				.setFileID(asFile(idLit));
		return Query.newBuilder().setFileGetContents(op).build();
	}
}
