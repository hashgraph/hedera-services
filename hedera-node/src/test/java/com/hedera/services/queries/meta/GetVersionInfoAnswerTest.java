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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.queries.token.GetTokenInfoAnswer;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.TxnUtils.*;

@RunWith(JUnitPlatform.class)
class GetVersionInfoAnswerTest {
	private String node = "0.0.3";
	private long fee = 1_234L;
	private String payer = "0.0.12345";
	private Transaction paymentTxn;
	StateView view;

	SemanticVersion expectedVersions = SemanticVersion.newBuilder()
			.setMajor(0)
			.setMinor(4)
			.setPatch(0)
			.build();
	GetVersionInfoAnswer subject;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		GetVersionInfoAnswer.VERSION_INFO_RESOURCE = "frozenVersion.properties";

		subject = new GetVersionInfoAnswer();
		GetVersionInfoAnswer.knownActive.set(null);
	}

	@Test
	public void recognizesAvailableResource() {
		// when:
		var status = GetVersionInfoAnswer.invariantValidityCheck();

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		// and:
		assertEquals(expectedVersions, GetVersionInfoAnswer.knownActive.get().protoSemVer());
		assertEquals(expectedVersions, GetVersionInfoAnswer.knownActive.get().hederaSemVer());
	}

	@Test
	public void recognizesUnavailableResource() {
		// setup:
		GetVersionInfoAnswer.VERSION_INFO_RESOURCE = "nonsense.properties";

		// when:
		var status = GetVersionInfoAnswer.invariantValidityCheck();

		// then:
		assertEquals(ResponseCodeEnum.FAIL_INVALID, status);

		// cleanup:
		GetVersionInfoAnswer.VERSION_INFO_RESOURCE = "frozenVersion.properties";
	}

	@Test
	public void getsInvalidResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, FAIL_INVALID, fee);

		// then:
		assertTrue(response.hasNetworkGetVersionInfo());
		assertEquals(FAIL_INVALID, response.getNetworkGetVersionInfo().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getNetworkGetVersionInfo().getHeader().getResponseType());
		assertEquals(fee, response.getNetworkGetVersionInfo().getHeader().getCost());
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasNetworkGetVersionInfo());
		NetworkGetVersionInfoResponse opResponse = response.getNetworkGetVersionInfo();
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	public void getsVersionInfoWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

		// given:
		assertEquals(OK, subject.checkValidity(sensibleQuery, view));

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		var opResponse = response.getNetworkGetVersionInfo();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(expectedVersions, opResponse.getHederaServicesVersion());
		assertEquals(expectedVersions, opResponse.getHapiProtoVersion());
	}

	@Test
	public void respectsMetaValidity() throws Throwable {
		// given:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

		// then:
		var opResponse = response.getNetworkGetVersionInfo();
		assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	private Query validQuery(ResponseType type, long payment) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);

		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		NetworkGetVersionInfoQuery.Builder op = NetworkGetVersionInfoQuery.newBuilder()
				.setHeader(header);
		return Query.newBuilder().setNetworkGetVersionInfo(op).build();
	}
}
