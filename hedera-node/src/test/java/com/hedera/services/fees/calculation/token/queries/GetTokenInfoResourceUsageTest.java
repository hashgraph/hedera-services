package com.hedera.services.fees.calculation.token.queries;

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
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.queries.token.GetTokenInfoAnswer;
import com.hedera.services.usage.token.TokenGetInfoUsage;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.queries.token.GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class GetTokenInfoResourceUsageTest {
	public static final FeeData MOCK_TOKEN_GET_INFO_USAGE = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(1)
					.setBpt(1)
					.setVpt(1)
					.setRbh(1)
					.setSbh(1)
					.setGas(1)
					.setTv(1)
					.setBpr(1)
					.setSbpr(1)
					.build(), 1);

	String symbol = "HEYMAOK";
	String name = "IsItReallyOk";
	TokenID target = IdUtils.asToken("0.0.123");

	TokenGetInfoUsage estimator;
	Function<Query, TokenGetInfoUsage> factory;

	StateView view;
	TokenInfo info = TokenInfo.newBuilder()
			.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey())
			.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey())
			.setWipeKey(TxnHandlingScenario.TOKEN_WIPE_KT.asKey())
			.setSupplyKey(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey())
			.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asKey())
			.setSymbol(symbol)
			.setName(name)
			.setAutoRenewAccount(IdUtils.asAccount("1.2.3"))
			.build();

	Query satisfiableAnswerOnly = tokenInfoQuery(target, ANSWER_ONLY);

	GetTokenInfoResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);
		estimator = mock(TokenGetInfoUsage.class);
		factory = mock(Function.class);
		given(factory.apply(any())).willReturn(estimator);

		GetTokenInfoResourceUsage.factory = factory;

		given(estimator.givenCurrentAdminKey(any())).willReturn(estimator);
		given(estimator.givenCurrentWipeKey(any())).willReturn(estimator);
		given(estimator.givenCurrentKycKey(any())).willReturn(estimator);
		given(estimator.givenCurrentSupplyKey(any())).willReturn(estimator);
		given(estimator.givenCurrentFreezeKey(any())).willReturn(estimator);
		given(estimator.givenCurrentSymbol(any())).willReturn(estimator);
		given(estimator.givenCurrentName(any())).willReturn(estimator);
		given(estimator.givenCurrentlyUsingAutoRenewAccount()).willReturn(estimator);
		given(estimator.get()).willReturn(MOCK_TOKEN_GET_INFO_USAGE);

		given(view.infoForToken(target)).willReturn(Optional.of(info));

		subject = new GetTokenInfoResourceUsage();
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		var applicable = tokenInfoQuery(target, COST_ANSWER);
		var inapplicable = Query.getDefaultInstance();

		// expect:
		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	public void setsInfoInQueryCxtIfPresent() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertSame(info, queryCtx.get(TOKEN_INFO_CTX_KEY));
		assertSame(MOCK_TOKEN_GET_INFO_USAGE, usage);
		// and:
		verify(estimator).givenCurrentAdminKey(Optional.of(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey()));
		verify(estimator).givenCurrentWipeKey(Optional.of(TxnHandlingScenario.TOKEN_WIPE_KT.asKey()));
		verify(estimator).givenCurrentKycKey(Optional.of(TxnHandlingScenario.TOKEN_KYC_KT.asKey()));
		verify(estimator).givenCurrentSupplyKey(Optional.of(TxnHandlingScenario.TOKEN_SUPPLY_KT.asKey()));
		verify(estimator).givenCurrentFreezeKey(Optional.of(TxnHandlingScenario.TOKEN_FREEZE_KT.asKey()));
		verify(estimator).givenCurrentSymbol(symbol);
		verify(estimator).givenCurrentName(name);
		verify(estimator).givenCurrentlyUsingAutoRenewAccount();
	}

	@Test
	public void onlySetsTokenInfoInQueryCxtIfFound() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		given(view.infoForToken(target)).willReturn(Optional.empty());

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertFalse(queryCtx.containsKey(GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY));
		// and:
		assertSame(FeeData.getDefaultInstance(), usage);
	}

	@Test
	public void rethrowsIae() {
		// given:
		Query query = tokenInfoQuery(target, ANSWER_ONLY);
		given(view.infoForToken(any())).willThrow(IllegalStateException.class);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(query, view));
	}

	private Query tokenInfoQuery(TokenID id, ResponseType type) {
		TokenGetInfoQuery.Builder op = TokenGetInfoQuery.newBuilder()
				.setToken(id)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setTokenGetInfo(op)
				.build();
	}
}