package com.hedera.services.queries.crypto;

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
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.spongycastle.util.encoders.Hex;

import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;
import static com.hedera.test.utils.TxnUtils.*;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;

@RunWith(JUnitPlatform.class)
class GetAccountInfoAnswerTest {
	private StateView view;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private OptionValidator optionValidator;
	private String node = "0.0.3";
	private String payer = "0.0.12345";
	private MerkleAccount payerAccount;
	private String target = payer;

	private long fee = 1_234L;
	private Transaction paymentTxn;

	private GetAccountInfoAnswer subject;

	@BeforeEach
	private void setup() throws Throwable {
		payerAccount = MapValueFactory.newAccount()
				.accountKeys(COMPLEX_KEY_ACCOUNT_KT)
				.proxy(asAccount("1.2.3"))
				.senderThreshold(1_234L)
				.receiverThreshold(4_321L)
				.receiverSigRequired(true)
				.balance(555L)
				.autoRenewPeriod(1_000_000L)
				.expirationTime(9_999_999L)
				.get();
		accounts = mock(FCMap.class);
		given(accounts.get(MerkleEntityId.fromPojoAccountId(asAccount(target)))).willReturn(payerAccount);

		view = new StateView(StateView.EMPTY_TOPICS, accounts);
		optionValidator = mock(OptionValidator.class);

		subject = new GetAccountInfoAnswer(optionValidator);
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasCryptoGetInfo());
		assertEquals(OK, response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getCryptoGetInfo().getHeader().getResponseType());
		assertEquals(fee, response.getCryptoGetInfo().getHeader().getCost());
	}

	@Test
	public void getsInvalidResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, ACCOUNT_DELETED, fee);

		// then:
		assertTrue(response.hasCryptoGetInfo());
		assertEquals(ACCOUNT_DELETED, response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getCryptoGetInfo().getHeader().getResponseType());
		assertEquals(fee, response.getCryptoGetInfo().getHeader().getCost());
	}

	@Test
	public void getsTheAccountInfo() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasCryptoGetInfo());
		assertTrue(response.getCryptoGetInfo().hasHeader(), "Missing response header!");
		assertEquals(OK, response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, response.getCryptoGetInfo().getHeader().getResponseType());
		assertEquals(0, response.getCryptoGetInfo().getHeader().getCost());
		// and:
		CryptoGetInfoResponse.AccountInfo info = response.getCryptoGetInfo().getAccountInfo();
		assertEquals(asAccount(payer), info.getAccountID());
		String address = Hex.toHexString(asSolidityAddress(0, 0L, 12_345L));
		assertEquals(address, info.getContractAccountID());
		assertEquals(payerAccount.getBalance(), info.getBalance());
		assertEquals(payerAccount.getReceiverThreshold(), info.getGenerateReceiveRecordThreshold());
		assertEquals(payerAccount.getSenderThreshold(), info.getGenerateSendRecordThreshold());
		assertEquals(payerAccount.getAutoRenewSecs(), info.getAutoRenewPeriod().getSeconds());
		assertEquals(payerAccount.getProxy(), EntityId.ofNullableAccountId(info.getProxyAccountID()));
		assertEquals(JKey.mapJKey(payerAccount.getKey()), info.getKey());
		assertEquals(payerAccount.isReceiverSigRequired(), info.getReceiverSigRequired());
		assertEquals(payerAccount.getExpiry(), info.getExpirationTime().getSeconds());
	}

	@Test
	public void usesValidator() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		given(optionValidator.queryableAccountStatus(asAccount(target), accounts)).willReturn(ACCOUNT_DELETED);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(ACCOUNT_DELETED, validity);
		// and:
		verify(optionValidator).queryableAccountStatus(any(), any());
	}

	@Test
	public void getsExpectedPayment() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee, target);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxn());
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
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setCryptoGetInfo(
				CryptoGetInfoResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(CryptoGetInfo, subject.canonicalFunction());
	}

	private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		CryptoGetInfoQuery.Builder op = CryptoGetInfoQuery.newBuilder()
				.setHeader(header)
				.setAccountID(asAccount(idLit));
		return Query.newBuilder().setCryptoGetInfo(op).build();
	}
}
