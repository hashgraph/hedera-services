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
import com.hedera.services.queries.contract.GetContractInfoAnswer;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Optional;

import static com.hedera.services.fees.calculation.token.queries.GetTokenInfoResourceUsage.MOCK_TOKEN_GET_INFO_USAGE;
import static com.hedera.services.queries.token.GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class GetTokenInfoResourceUsageTest {
	TokenRef target = TokenRef.newBuilder().setSymbol("TARGET").build();

	StateView view;
	TokenInfo info = TokenInfo.getDefaultInstance();

	Query satisfiableAnswerOnly = tokenInfoQuery(target, ANSWER_ONLY);
	Query satisfiableCostAnswer = tokenInfoQuery(target, ANSWER_STATE_PROOF);

	GetTokenInfoResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

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
	public void invokesEstimatorAsExpectedForType() throws Exception {
		// when:
		FeeData answerOnlyEstimate = subject.usageGiven(satisfiableAnswerOnly, view);
		FeeData plusStateProofEstimate = subject.usageGiven(satisfiableCostAnswer, view);

		// then:
		assertSame(answerOnlyEstimate, MOCK_TOKEN_GET_INFO_USAGE);
		assertSame(plusStateProofEstimate, MOCK_TOKEN_GET_INFO_USAGE);

		// and when:
		answerOnlyEstimate = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);
		plusStateProofEstimate = subject.usageGivenType(satisfiableCostAnswer, view, ANSWER_STATE_PROOF);

		// then:
		assertSame(answerOnlyEstimate, MOCK_TOKEN_GET_INFO_USAGE);
		assertSame(plusStateProofEstimate, MOCK_TOKEN_GET_INFO_USAGE);
	}

	@Test
	public void setsInfoInQueryCxtIfPresent() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		// when:
		subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertSame(info, queryCtx.get(TOKEN_INFO_CTX_KEY));
	}

	@Test
	public void onlySetsContractInfoInQueryCxtIfFound() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		given(view.infoForToken(target)).willReturn(Optional.empty());

		// when:
		subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertFalse(queryCtx.containsKey(GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY));
	}

	@Test
	public void rethrowsIae() {
		// given:
		Query query = tokenInfoQuery(target, ANSWER_ONLY);
		given(view.infoForToken(any())).willThrow(IllegalStateException.class);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.usageGiven(query, view));
	}

	private Query tokenInfoQuery(TokenRef ref, ResponseType type) {
		TokenGetInfoQuery.Builder op = TokenGetInfoQuery.newBuilder()
				.setToken(ref)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setTokenGetInfo(op)
				.build();
	}
}