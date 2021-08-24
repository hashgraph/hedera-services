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
import com.hedera.services.usage.token.TokenGetNftInfosUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.queries.AnswerService.NO_QUERY_CTX;
import static com.hedera.services.queries.token.GetTokenNftInfosAnswer.TOKEN_NFT_INFOS_CTX_KEY;

@Singleton
public class GetTokenNftInfosResourceUsage implements QueryResourceUsageEstimator {
	static Function<Query, TokenGetNftInfosUsage> factory = TokenGetNftInfosUsage::newEstimate;

	@Inject
	public GetTokenNftInfosResourceUsage() {
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasTokenGetNftInfos();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageFor(query, view, query.getTokenGetNftInfos().getHeader().getResponseType(), NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		return usageFor(query, view, type, NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGiven(Query query, StateView view, Map<String, Object> queryCtx) {
		return usageFor(query, view, query.getTokenGetNftInfos().getHeader().getResponseType(), Optional.of(queryCtx));
	}

	private FeeData usageFor(Query query, StateView view, ResponseType type, Optional<Map<String, Object>> queryCtx) {
		final var op = query.getTokenGetNftInfos();
		final var optionalInfos = view.infosForTokenNfts(
				op.getTokenID(),
				op.getStart(),
				op.getEnd());
		if (optionalInfos.isPresent()) {
			final var infos = optionalInfos.get();
			queryCtx.ifPresent(ctx -> ctx.put(TOKEN_NFT_INFOS_CTX_KEY, infos));

			List<ByteString> meta = new ArrayList<>();
			infos.forEach(info -> meta.add(info.getMetadata()));
			return factory.apply(query).givenMetadata(meta).get();
		} else {
			return FeeData.getDefaultInstance();
		}
	}
}
