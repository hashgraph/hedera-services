package com.hedera.services.fees.calculation.token.queries;

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
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.token.TokenGetNftInfoUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoQuery;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.queries.token.GetTokenNftInfoAnswer.NFT_INFO_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class GetTokenNftInfoResourceUsageTest {
	ByteString memo = ByteString.copyFromUtf8("22 a million");
	NftID target = IdUtils.asNftID("0.0.123", 1);
	FeeData expected;

	TokenGetNftInfoUsage estimator;
	Function<Query, TokenGetNftInfoUsage> factory;

	StateView view;
	TokenNftInfo info = TokenNftInfo.newBuilder()
			.setMetadata(memo)
			.build();

	Query satisfiableAnswerOnly = query(target, ANSWER_ONLY);

	GetTokenNftInfoResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		expected = mock(FeeData.class);
		view = mock(StateView.class);
		estimator = mock(TokenGetNftInfoUsage.class);
		factory = mock(Function.class);
		given(factory.apply(any())).willReturn(estimator);

		GetTokenNftInfoResourceUsage.factory = factory;

		given(estimator.givenMemo(any())).willReturn(estimator);
		given(estimator.get()).willReturn(expected);

		given(view.infoForNft(target)).willReturn(Optional.of(info));

		subject = new GetTokenNftInfoResourceUsage();
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		var applicable = query(target, COST_ANSWER);
		var inapplicable = Query.getDefaultInstance();

		// expect:
		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	public void returnsDefaultIfNoSuchNftID() {
		given(view.infoForNft(target)).willReturn(Optional.empty());

		// when:
		var withoutResponse = subject.usageGiven(satisfiableAnswerOnly, view);
		var withResponse = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);

		// then:
		assertSame(FeeData.getDefaultInstance(), withoutResponse);
		assertSame(FeeData.getDefaultInstance(), withResponse);
	}

	@Test
	public void setsInfoInQueryCxtIfPresent() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertSame(info, queryCtx.get(NFT_INFO_CTX_KEY));
		assertSame(expected, usage);
		// and:
		verify(estimator).givenMemo(memo.toString());
	}

	@Test
	public void onlySetsInfoInQueryCxtIfFound() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		given(view.infoForNft(target)).willReturn(Optional.empty());

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertFalse(queryCtx.containsKey(NFT_INFO_CTX_KEY));
		// and:
		assertSame(FeeData.getDefaultInstance(), usage);
	}

	private Query query(NftID id, ResponseType type) {
		TokenGetNftInfoQuery.Builder op = TokenGetNftInfoQuery.newBuilder()
				.setNftID(id)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setTokenGetNftInfo(op)
				.build();
	}
}
