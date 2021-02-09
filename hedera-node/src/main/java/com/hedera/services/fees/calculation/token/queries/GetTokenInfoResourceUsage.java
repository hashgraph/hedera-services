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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.usage.token.TokenGetInfoUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.queries.AnswerService.NO_QUERY_CTX;
import static com.hedera.services.queries.token.GetTokenInfoAnswer.TOKEN_INFO_CTX_KEY;

public class GetTokenInfoResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetTokenInfoResourceUsage.class);

	static Function<Query, TokenGetInfoUsage> factory = TokenGetInfoUsage::newEstimate;

	@Override
	public boolean applicableTo(Query query) {
		return query.hasTokenGetInfo();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageFor(query, view, query.getTokenGetInfo().getHeader().getResponseType(), NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		return usageFor(query, view, type, NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGiven(Query query, StateView view, Map<String, Object> queryCtx) {
		return usageFor(
				query,
				view,
				query.getTokenGetInfo().getHeader().getResponseType(),
				Optional.of(queryCtx));
	}

	private FeeData usageFor(Query query, StateView view, ResponseType type, Optional<Map<String, Object>> queryCtx) {
		var op = query.getTokenGetInfo();
		var optionalInfo = view.infoForToken(op.getToken());
		if (optionalInfo.isPresent()) {
			var info = optionalInfo.get();
			queryCtx.ifPresent(ctx -> ctx.put(TOKEN_INFO_CTX_KEY, info));
			var estimate = factory.apply(query)
					.givenCurrentAdminKey(ifPresent(info, TokenInfo::hasAdminKey, TokenInfo::getAdminKey))
					.givenCurrentFreezeKey(ifPresent(info, TokenInfo::hasFreezeKey, TokenInfo::getFreezeKey))
					.givenCurrentWipeKey(ifPresent(info, TokenInfo::hasWipeKey, TokenInfo::getWipeKey))
					.givenCurrentSupplyKey(ifPresent(info, TokenInfo::hasSupplyKey, TokenInfo::getSupplyKey))
					.givenCurrentKycKey(ifPresent(info, TokenInfo::hasKycKey, TokenInfo::getKycKey))
					.givenCurrentName(info.getName())
					.givenCurrentMemo(info.getMemo())
					.givenCurrentSymbol(info.getSymbol());
			if (info.hasAutoRenewAccount()) {
				estimate.givenCurrentlyUsingAutoRenewAccount();
			}
			return estimate.get();
		} else {
			return FeeData.getDefaultInstance();
		}
	}

	public static Optional<Key> ifPresent(TokenInfo info, Predicate<TokenInfo> check, Function<TokenInfo, Key> getter) {
		return check.test(info) ? Optional.of(getter.apply(info)) : Optional.empty();
	}
}
