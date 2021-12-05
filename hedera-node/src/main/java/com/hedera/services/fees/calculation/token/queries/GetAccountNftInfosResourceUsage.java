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
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hedera.services.queries.token.GetAccountNftInfosAnswer.ACCOUNT_NFT_INFO_CTX_KEY;
import static com.hedera.services.utils.MiscUtils.putIfNotNull;

@Singleton
public final class GetAccountNftInfosResourceUsage implements QueryResourceUsageEstimator {
	private static final Function<Query, TokenGetAccountNftInfosUsage> factory
			= TokenGetAccountNftInfosUsage::newEstimate;

	@Inject
	public GetAccountNftInfosResourceUsage() {
		/* No-op */
	}

	@Override
	public boolean applicableTo(final Query query) {
		return query.hasTokenGetAccountNftInfos();
	}

	@Override
	public FeeData usageGiven(final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
		final var op = query.getTokenGetAccountNftInfos();
		final var accountId = EntityNum.fromAccountId(op.getAccountID());
		final var optionalInfo = view.infoForAccountNfts(
				accountId,
				op.getStart(),
				op.getEnd());
		if (optionalInfo.isPresent()) {
			final var info = optionalInfo.get();
			putIfNotNull(queryCtx, ACCOUNT_NFT_INFO_CTX_KEY, info);

			final List<ByteString> m = new ArrayList<>();
			info.forEach(s -> m.add(s.getMetadata()));
			return factory.apply(query).givenMetadata(m).get();
		} else {
			return FeeData.getDefaultInstance();
		}
	}
}
