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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.BDDMockito.*;
import static com.hedera.services.state.merkle.MerkleEntityId.fromContractId;
import static com.hedera.test.utils.IdUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class GetAccountBalanceAnswerTest {
	private FCMap accounts;
	private StateView view;
	private OptionValidator optionValidator;
	private String idLit = "0.0.12345";
	private long balance = 1_234L;
	private MerkleAccount accountV = MapValueFactory.newAccount().balance(balance).get();
	private MerkleAccount contractV = MapValueFactory.newContract().balance(balance).get();

	private GetAccountBalanceAnswer subject;
	private PropertySource propertySource;

	@BeforeEach
	private void setup() {
		accounts = mock(FCMap.class);
		propertySource = mock(PropertySource.class);
		given(accounts.get(MerkleEntityId.fromAccountId(asAccount(idLit)))).willReturn(accountV);
		given(accounts.get(fromContractId(asContract(idLit)))).willReturn(contractV);
		view = new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts, propertySource);

		optionValidator = mock(OptionValidator.class);
		subject = new GetAccountBalanceAnswer(optionValidator);
	}

	@Test
	public void requiresNothing() {
		// setup:
		CryptoGetAccountBalanceQuery costAnswerOp = CryptoGetAccountBalanceQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ResponseType.COST_ANSWER))
				.build();
		Query costAnswerQuery = Query.newBuilder().setCryptogetAccountBalance(costAnswerOp).build();
		CryptoGetAccountBalanceQuery answerOnlyOp = CryptoGetAccountBalanceQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ResponseType.ANSWER_ONLY))
				.build();
		Query answerOnlyQuery = Query.newBuilder().setCryptogetAccountBalance(answerOnlyOp).build();

		// expect:
		assertFalse(subject.requiresNodePayment(costAnswerQuery));
		assertFalse(subject.requiresNodePayment(answerOnlyQuery));
		assertFalse(subject.needsAnswerOnlyCost(answerOnlyQuery));
		assertFalse(subject.needsAnswerOnlyCost(costAnswerQuery));
	}

	@Test
	public void hasNoPayment() {
		// expect:
		assertFalse(subject.extractPaymentFrom(mock(Query.class)).isPresent());
	}

	@Test
	public void syntaxCheckRequiresId() {
		// given:
		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder().build();
		Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

		// when:
		ResponseCodeEnum status = subject.checkValidity(query, view);

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void syntaxCheckValidatesCidIfPresent() {
		// setup:
		ContractID cid = asContract(idLit);

		// given:
		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder()
				.setContractID(cid)
				.build();
		Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();
		// and:
		given(optionValidator.queryableContractStatus(cid, accounts)).willReturn(CONTRACT_DELETED);

		// when:
		ResponseCodeEnum status = subject.checkValidity(query, view);

		// expect:
		assertEquals(CONTRACT_DELETED, status);
	}

	@Test
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setCryptogetAccountBalance(
				CryptoGetAccountBalanceResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	public void requiresOkMetaValidity() {
		// setup:
		AccountID id = asAccount(idLit);

		// given:
		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder()
				.setAccountID(id)
				.build();
		Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

		// when:
		Response response = subject.responseGiven(query, view, PLATFORM_NOT_ACTIVE);
		ResponseCodeEnum status = response.getCryptogetAccountBalance()
				.getHeader()
				.getNodeTransactionPrecheckCode();

		// expect:
		assertEquals(PLATFORM_NOT_ACTIVE, status);
		assertEquals(id, response.getCryptogetAccountBalance().getAccountID());
	}

	@Test
	public void syntaxCheckValidatesIdIfPresent() {
		// setup:
		AccountID id = asAccount(idLit);

		// given:
		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder()
				.setAccountID(id)
				.build();
		Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();
		// and:
		given(optionValidator.queryableAccountStatus(id, accounts))
				.willReturn(ACCOUNT_DELETED);

		// when:
		ResponseCodeEnum status = subject.checkValidity(query, view);

		// expect:
		assertEquals(ACCOUNT_DELETED, status);
	}

	@Test
	public void answersWithAccountBalance() {
		// setup:
		AccountID id = asAccount(idLit);

		// given:
		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder()
				.setAccountID(id)
				.build();
		Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

		// when:
		Response response = subject.responseGiven(query, view, OK);
		ResponseCodeEnum status = response.getCryptogetAccountBalance()
				.getHeader()
				.getNodeTransactionPrecheckCode();
		long answer = response.getCryptogetAccountBalance().getBalance();

		// expect:
		assertTrue(response.getCryptogetAccountBalance().hasHeader(), "Missing response header!");
		assertEquals(OK, status);
		assertEquals(balance, answer);
		assertEquals(id, response.getCryptogetAccountBalance().getAccountID());
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(CryptoGetAccountBalance, subject.canonicalFunction());
	}
}
