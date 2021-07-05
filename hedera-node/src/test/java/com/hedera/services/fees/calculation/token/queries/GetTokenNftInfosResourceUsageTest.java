package com.hedera.services.fees.calculation.token.queries;

/*
 * -
 *
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.token.TokenGetNftInfosUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.queries.token.GetTokenNftInfosAnswer.TOKEN_NFT_INFOS_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GetTokenNftInfosResourceUsageTest {
	ByteString m1 = ByteString.copyFromUtf8("metadata1"), m2 = ByteString.copyFromUtf8("metadata2");
	List<ByteString> metadatas = List.of(m1, m2);
	TokenGetNftInfosUsage estimator;
	Function<Query, TokenGetNftInfosUsage> factory;
	FeeData expected;
	TokenID target = IdUtils.asToken("0.0.123");
	int start = 0, end = 1;

	StateView view;
	List<TokenNftInfo> info = List.of(TokenNftInfo.newBuilder()
					.setMetadata(m1)
					.build(),
			TokenNftInfo.newBuilder()
					.setMetadata(m2)
					.build());

	Query satisfiableAnswerOnly = tokenGetNftInfosQuery(target, start, end, ResponseType.ANSWER_ONLY);

	GetTokenNftInfosResourceUsage subject;

	@BeforeEach
	private void setup() throws Throwable {
		expected = mock(FeeData.class);
		view = mock(StateView.class);
		estimator = mock(TokenGetNftInfosUsage.class);
		factory = mock(Function.class);
		given(factory.apply(any())).willReturn(estimator);

		GetTokenNftInfosResourceUsage.factory = factory;

		given(estimator.givenMetadata(any())).willReturn(estimator);
		given(estimator.get()).willReturn(expected);

		given(view.infoForTokenNfts(target, start, end)).willReturn(Optional.of(info));

		subject = new GetTokenNftInfosResourceUsage();
	}

	@Test
	public void recognizesApplicableQuery() {
		// given:
		var applicable = tokenGetNftInfosQuery(target, start, end, COST_ANSWER);
		var inapplicable = Query.getDefaultInstance();

		// expect:
		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	public void setsNftInfosInQueryCxtIfPresent() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertSame(info, queryCtx.get(TOKEN_NFT_INFOS_CTX_KEY));
		assertSame(expected, usage);
		// and:
		verify(estimator).givenMetadata(metadatas);
	}

	@Test
	public void setsNftInfosInQueryCxtNotPresent() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnly, view);

		// then:
		assertNull(queryCtx.get(TOKEN_NFT_INFOS_CTX_KEY));
		assertSame(expected, usage);
		// and:
		verify(estimator).givenMetadata(metadatas);
	}

	@Test
	public void setsNftInfosInQueryCxtNotPresentWithType() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		// when:
		var usage = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);

		// then:
		assertNull(queryCtx.get(TOKEN_NFT_INFOS_CTX_KEY));
		assertSame(expected, usage);
		// and:
		verify(estimator).givenMetadata(metadatas);
	}

	@Test
	public void onlySetsTokenNftInfosInQueryCxtIfFound() {
		// setup:
		var queryCtx = new HashMap<String, Object>();

		given(view.infoForTokenNfts(target, start, end)).willReturn(Optional.empty());

		// when:
		var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		// then:
		assertFalse(queryCtx.containsKey(TOKEN_NFT_INFOS_CTX_KEY));
		// and:
		assertSame(FeeData.getDefaultInstance(), usage);
	}

	private Query tokenGetNftInfosQuery(TokenID id, long start, long end, ResponseType type) {
		TokenGetNftInfosQuery.Builder op = TokenGetNftInfosQuery.newBuilder()
				.setTokenID(id)
				.setStart(start)
				.setEnd(end)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setTokenGetNftInfos(op)
				.build();
	}
}
