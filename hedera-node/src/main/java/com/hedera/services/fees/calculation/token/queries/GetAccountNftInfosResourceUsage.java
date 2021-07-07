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
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.usage.token.TokenGetAccountNftInfosUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.queries.AnswerService.NO_QUERY_CTX;
import static com.hedera.services.queries.token.GetAccountNftInfosAnswer.ACCOUNT_NFT_INFO_CTX_KEY;

public class GetAccountNftInfosResourceUsage implements QueryResourceUsageEstimator {
	static Function<Query, TokenGetAccountNftInfosUsage> factory = TokenGetAccountNftInfosUsage::newEstimate;

    static Function<Query, TokenGetAccountNftInfosUsage> factory = TokenGetAccountNftInfosUsage::newEstimate;

	@Override
	public boolean applicableTo(Query query) {
		return query.hasTokenGetAccountNftInfos();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageFor(query, view, NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		return usageFor(query, view, NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGiven(Query query, StateView view, Map<String, Object> queryCtx) {
		return usageFor(
				query,
				view,
				Optional.of(queryCtx));
	}

	private FeeData usageFor(Query query, StateView view, Optional<Map<String, Object>> queryCtx) {
		var op = query.getTokenGetAccountNftInfos();
		var optionalInfo = view.infoForAccountNfts(
				op.getAccountID(),
				op.getStart(),
				op.getEnd());
		if (optionalInfo.isPresent()) {
			var info = optionalInfo.get();
			queryCtx.ifPresent(ctx -> ctx.put(ACCOUNT_NFT_INFO_CTX_KEY, info));

			List<ByteString> m = new ArrayList<>();
			info.forEach(s -> m.add(s.getMetadata()));
			return factory.apply(query).givenMetadata(m).get();
		} else {
			return FeeData.getDefaultInstance();
		}
	}

	public static Optional<Key> ifPresent(TokenInfo info, Predicate<TokenInfo> check, Function<TokenInfo, Key> getter) {
		return check.test(info) ? Optional.of(getter.apply(info)) : Optional.empty();
	}
}
