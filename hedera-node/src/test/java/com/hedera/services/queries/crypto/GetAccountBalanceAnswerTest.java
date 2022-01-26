package com.hedera.services.queries.crypto;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNum.fromContractId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.tokenBalanceWith;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class GetAccountBalanceAnswerTest {
	private final String accountIdLit = "0.0.12345";
	private final AccountID target = asAccount(accountIdLit);
	private final String contractIdLit = "0.0.12346";
	private final long balance = 1_234L;
	private final long aBalance = 345;
	private final long bBalance = 456;
	private final long cBalance = 567;
	private final long dBalance = 678;
	private final TokenID aToken = IdUtils.asToken("0.0.3");
	private final TokenID bToken = IdUtils.asToken("0.0.4");
	private final TokenID cToken = IdUtils.asToken("0.0.5");
	private final TokenID dToken = IdUtils.asToken("0.0.6");

	private MerkleToken notDeleted, deleted;
	private final MerkleAccount accountV = MerkleAccountFactory.newAccount()
			.balance(balance)
			.tokens(aToken, bToken, cToken, dToken)
			.get();
	private final MerkleAccount contractV = MerkleAccountFactory.newContract().balance(balance).get();

	private MerkleMap accounts;
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;
	private StateView view;
	private OptionValidator optionValidator;
	private TokenStore tokenStore;
	private AliasManager aliasManager;
	private ScheduleStore scheduleStore;
	private NodeLocalProperties nodeProps;

	private GetAccountBalanceAnswer subject;

	@BeforeEach
	private void setup() {
		deleted = mock(MerkleToken.class);
		given(deleted.isDeleted()).willReturn(true);
		given(deleted.decimals()).willReturn(123);
		notDeleted = mock(MerkleToken.class);
		given(notDeleted.isDeleted()).willReturn(false);
		given(notDeleted.decimals()).willReturn(1).willReturn(2);

		tokenRels = new MerkleMap<>();
		tokenRels.put(
				fromAccountTokenRel(target, aToken),
				new MerkleTokenRelStatus(aBalance, true, true, true));
		tokenRels.put(
				fromAccountTokenRel(target, bToken),
				new MerkleTokenRelStatus(bBalance, false, false, false));
		tokenRels.put(
				fromAccountTokenRel(target, cToken),
				new MerkleTokenRelStatus(cBalance, false, false, true));
		tokenRels.put(
				fromAccountTokenRel(target, dToken),
				new MerkleTokenRelStatus(dBalance, false, false, true));

		accounts = mock(MerkleMap.class);
		nodeProps = mock(NodeLocalProperties.class);
		given(accounts.get(fromAccountId(asAccount(accountIdLit)))).willReturn(accountV);
		given(accounts.get(fromContractId(asContract(contractIdLit)))).willReturn(contractV);

		tokenStore = mock(TokenStore.class);
		given(tokenStore.exists(aToken)).willReturn(true);
		given(tokenStore.exists(bToken)).willReturn(true);
		given(tokenStore.exists(cToken)).willReturn(true);
		given(tokenStore.exists(dToken)).willReturn(false);
		given(tokenStore.get(aToken)).willReturn(notDeleted);
		given(tokenStore.get(bToken)).willReturn(notDeleted);
		given(tokenStore.get(cToken)).willReturn(deleted);

		scheduleStore = mock(ScheduleStore.class);

		final MutableStateChildren children = new MutableStateChildren();
		children.setAccounts(accounts);
		children.setTokenAssociations(tokenRels);
		view = new StateView(
				tokenStore,
				scheduleStore,
				children,
				EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY,
				null);

		optionValidator = mock(OptionValidator.class);
		aliasManager = mock(AliasManager.class);
		subject = new GetAccountBalanceAnswer(aliasManager, optionValidator);
	}

	@Test
	void requiresNothing() {
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
	void hasNoPayment() {
		// expect:
		assertFalse(subject.extractPaymentFrom(mock(Query.class)).isPresent());
	}

	@Test
	void syntaxCheckRequiresId() {
		// given:
		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder().build();
		Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

		// when:
		ResponseCodeEnum status = subject.checkValidity(query, view);

		// expect:
		assertEquals(INVALID_ACCOUNT_ID, status);
	}

	@Test
	void syntaxCheckValidatesCidIfPresent() {
		// setup:
		ContractID cid = asContract(contractIdLit);

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
	void getsValidity() {
		// given:
		Response response = Response.newBuilder().setCryptogetAccountBalance(
				CryptoGetAccountBalanceResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	void requiresOkMetaValidity() {
		// setup:
		AccountID id = asAccount(accountIdLit);

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
	void syntaxCheckValidatesIdIfPresent() {
		// setup:
		AccountID id = asAccount(accountIdLit);

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
	void resolvesAliasIfExtant() {
		final var aliasId = AccountID.newBuilder()
				.setAlias(ByteString.copyFromUtf8("nope"))
				.build();
		final var wellKnownId = EntityNum.fromLong(12345L);
		given(aliasManager.lookupIdBy(aliasId.getAlias())).willReturn(wellKnownId);

		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder()
				.setAccountID(aliasId)
				.build();
		Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

		Response response = subject.responseGiven(query, view, OK);
		ResponseCodeEnum status = response.getCryptogetAccountBalance()
				.getHeader()
				.getNodeTransactionPrecheckCode();
		long answer = response.getCryptogetAccountBalance().getBalance();

		assertTrue(response.getCryptogetAccountBalance().hasHeader(), "Missing response header!");
		assertEquals(
				List.of(tokenBalanceWith(aToken, aBalance, 1),
						tokenBalanceWith(bToken, bBalance, 2),
						tokenBalanceWith(cToken, cBalance, 123),
						tokenBalanceWith(dToken, dBalance, 0)
				),
				response.getCryptogetAccountBalance().getTokenBalancesList());
		assertEquals(OK, status);
		assertEquals(balance, answer);
		assertEquals(wellKnownId.toGrpcAccountId(), response.getCryptogetAccountBalance().getAccountID());
	}

	@Test
	void answersWithAccountBalance() {
		AccountID id = asAccount(accountIdLit);

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
		assertEquals(
				List.of(tokenBalanceWith(aToken, aBalance, 1),
						tokenBalanceWith(bToken, bBalance, 2),
						tokenBalanceWith(cToken, cBalance, 123),
						tokenBalanceWith(dToken, dBalance, 0)
				),
				response.getCryptogetAccountBalance().getTokenBalancesList());
		assertEquals(OK, status);
		assertEquals(balance, answer);
		assertEquals(id, response.getCryptogetAccountBalance().getAccountID());
	}

	@Test
	void answersWithAccountBalanceWhenTheAccountIDIsContractID() {
		// setup:
		ContractID id = asContract(accountIdLit);

		// given:
		CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder()
				.setContractID(id)
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
		assertEquals(
				List.of(tokenBalanceWith(aToken, aBalance, 1),
						tokenBalanceWith(bToken, bBalance, 2),
						tokenBalanceWith(cToken, cBalance, 123),
						tokenBalanceWith(dToken, dBalance, 0)
				),
				response.getCryptogetAccountBalance().getTokenBalancesList());
		assertEquals(OK, status);
		assertEquals(balance, answer);
		assertEquals(asAccount(accountIdLit), response.getCryptogetAccountBalance().getAccountID());
	}

	@Test
	void recognizesFunction() {
		// expect:
		assertEquals(CryptoGetAccountBalance, subject.canonicalFunction());
	}
}
