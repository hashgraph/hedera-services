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
import com.hedera.services.usage.token.TokenGetAccountNftInfosUsage;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.queries.token.GetAccountNftInfosAnswer.ACCOUNT_NFT_INFO_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class GetAccountNftInfosResourceUsageTest {
	private static final ByteString m1 = ByteString.copyFromUtf8("metadata1");
	private static final ByteString m2 = ByteString.copyFromUtf8("metadata2");
	private static final List<ByteString> metadata = List.of(m1, m2);
	private static final List<TokenNftInfo> info = List.of(
			TokenNftInfo.newBuilder()
					.setMetadata(m1)
					.build(),
			TokenNftInfo.newBuilder()
					.setMetadata(m2)
					.build());
	private static final EntityNum target = EntityNum.fromLong(123);
	private static final int start = 0;
	private static final int end = 1;
	private static final Query satisfiableAnswerOnly = tokenGetAccountNftInfosQuery(target, start, end, ANSWER_ONLY);

	private TokenGetAccountNftInfosUsage estimator;
	private MockedStatic<TokenGetAccountNftInfosUsage> mockedStatic;
	private FeeData expected;
	private StateView view;

	private GetAccountNftInfosResourceUsage subject;

	@BeforeEach
	private void setup() {
		expected = mock(FeeData.class);
		view = mock(StateView.class);
		estimator = mock(TokenGetAccountNftInfosUsage.class);
		mockedStatic = mockStatic(TokenGetAccountNftInfosUsage.class);
		mockedStatic.when(() -> TokenGetAccountNftInfosUsage.newEstimate(satisfiableAnswerOnly)).thenReturn(estimator);

		given(estimator.givenMetadata(any())).willReturn(estimator);
		given(estimator.get()).willReturn(expected);

		given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.of(info));

		subject = new GetAccountNftInfosResourceUsage();
	}

	@AfterEach
	void tearDown() {
		mockedStatic.close();
	}

	@Test
	void recognizesApplicableQuery() {
		final var applicable = tokenGetAccountNftInfosQuery(target, start, end, COST_ANSWER);
		final var inapplicable = Query.getDefaultInstance();

		assertTrue(subject.applicableTo(applicable));
		assertFalse(subject.applicableTo(inapplicable));
	}

	@Test
	void setsInfoInQueryCxtIfPresent() {
		final var queryCtx = new HashMap<String, Object>();

		final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		assertSame(info, queryCtx.get(ACCOUNT_NFT_INFO_CTX_KEY));
		assertSame(expected, usage);
		verify(estimator).givenMetadata(metadata);
	}

	@Test
	void onlySetsTokenInfoInQueryCxtIfFound() {
		final var queryCtx = new HashMap<String, Object>();
		given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.empty());

		final var usage = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

		assertFalse(queryCtx.containsKey(ACCOUNT_NFT_INFO_CTX_KEY));
		assertSame(FeeData.getDefaultInstance(), usage);
	}

	@Test
	void doesntSetTokenInfoInQueryCxtNotFound() {
		final var queryCtx = new HashMap<String, Object>();
		given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.of(info));

		final var usage = subject.usageGiven(satisfiableAnswerOnly, view);

		assertFalse(queryCtx.containsKey(ACCOUNT_NFT_INFO_CTX_KEY));
		verify(estimator).givenMetadata(metadata);
		assertNotSame(FeeData.getDefaultInstance(), usage);
	}

	@Test
	void doesntSetTokenInfoForAnswerOnlyType() {
		final var queryCtx = new HashMap<String, Object>();
		given(view.infoForAccountNfts(target, start, end)).willReturn(Optional.of(info));

		final var usage = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);

		assertFalse(queryCtx.containsKey(ACCOUNT_NFT_INFO_CTX_KEY));
		assertNotSame(FeeData.getDefaultInstance(), usage);
	}

	private static final Query tokenGetAccountNftInfosQuery(
			final EntityNum id,
			final long start,
			final long end,
			final ResponseType type) {
		final var op = TokenGetAccountNftInfosQuery.newBuilder()
				.setAccountID(id.toGrpcAccountId())
				.setStart(start)
				.setEnd(end)
				.setHeader(QueryHeader.newBuilder().setResponseType(type));
		return Query.newBuilder()
				.setTokenGetAccountNftInfos(op)
				.build();
	}
}
