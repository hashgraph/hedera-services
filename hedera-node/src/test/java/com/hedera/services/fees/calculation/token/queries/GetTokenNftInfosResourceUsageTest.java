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
import com.hedera.services.usage.token.TokenGetNftInfosUsage;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class GetTokenNftInfosResourceUsageTest {
	private static final ByteString m1 = ByteString.copyFromUtf8("metadata1");
	private static final ByteString m2 = ByteString.copyFromUtf8("metadata2");
	private static final List<ByteString> metadatas = List.of(m1, m2);
	private static final List<TokenNftInfo> info = List.of(
			TokenNftInfo.newBuilder()
					.setMetadata(m1)
					.build(),
			TokenNftInfo.newBuilder()
					.setMetadata(m2)
					.build());
	private static final TokenID target = IdUtils.asToken("0.0.123");
	private static final int start = 0;
	private static final int end = 1;
	private static final Query satisfiableAnswerOnly = tokenGetNftInfosQuery(target, start, end, ANSWER_ONLY);

	private TokenGetNftInfosUsage estimator;
	private MockedStatic<TokenGetNftInfosUsage> mockedStatic;
	private FeeData expected;
	private StateView view;

	private GetTokenNftInfosResourceUsage subject;

	@BeforeEach
	private void setup() {
		expected = mock(FeeData.class);
		view = mock(StateView.class);
		estimator = mock(TokenGetNftInfosUsage.class);
		mockedStatic = mockStatic(TokenGetNftInfosUsage.class);
		mockedStatic.when(() -> TokenGetNftInfosUsage.newEstimate(satisfiableAnswerOnly)).thenReturn(estimator);

		given(estimator.givenMetadata(any())).willReturn(estimator);
		given(estimator.get()).willReturn(expected);

		given(view.infosForTokenNfts(target, start, end)).willReturn(Optional.of(info));

		subject = new GetTokenNftInfosResourceUsage();
	}

	@AfterEach
	void tearDown() {
		mockedStatic.close();
	}

	@Test
	void recognizesApplicableQuery() {
		final var applicable = tokenGetNftInfosQuery(target, start, end, COST_ANSWER);
		final var inapplicable = Query.getDefaultInstance();

		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	void setsNftInfosInQueryCxtIfPresent() {
		final var queryCtx = new HashMap<String, Object>();

		final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		assertSame(info, queryCtx.get(TOKEN_NFT_INFOS_CTX_KEY));
		assertSame(expected, usage);
		verify(estimator).givenMetadata(metadatas);
	}

	@Test
	void setsNftInfosInQueryCxtNotPresent() {
		final var queryCtx = new HashMap<String, Object>();

		final var usage = subject.usageGiven(satisfiableAnswerOnly, view);

		assertNull(queryCtx.get(TOKEN_NFT_INFOS_CTX_KEY));
		assertSame(expected, usage);
		verify(estimator).givenMetadata(metadatas);
	}

	@Test
	void setsNftInfosInQueryCxtNotPresentWithType() {
		final var queryCtx = new HashMap<String, Object>();

		final var usage = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);

		assertNull(queryCtx.get(TOKEN_NFT_INFOS_CTX_KEY));
		assertSame(expected, usage);
		verify(estimator).givenMetadata(metadatas);
	}

	@Test
	void onlySetsTokenNftInfosInQueryCxtIfFound() {
		final var queryCtx = new HashMap<String, Object>();
		given(view.infosForTokenNfts(target, start, end)).willReturn(Optional.empty());

		final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		assertFalse(queryCtx.containsKey(TOKEN_NFT_INFOS_CTX_KEY));
		assertSame(FeeData.getDefaultInstance(), usage);
	}

	private static final Query tokenGetNftInfosQuery(
			final TokenID id,
			final long start,
			final long end,
			final ResponseType type
	) {
		final var op = TokenGetNftInfosQuery.newBuilder()
				.setTokenID(id)
				.setStart(start)
				.setEnd(end)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setTokenGetNftInfos(op)
				.build();
	}
}
