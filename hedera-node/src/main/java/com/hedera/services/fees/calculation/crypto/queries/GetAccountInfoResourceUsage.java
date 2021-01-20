package com.hedera.services.fees.calculation.crypto.queries;

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
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.usage.crypto.CryptoGetInfoUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

public class GetAccountInfoResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetAccountInfoResourceUsage.class);

	static Function<Query, CryptoGetInfoUsage> factory = CryptoGetInfoUsage::newEstimate;

	@Override
	public boolean applicableTo(Query query) {
		return query.hasCryptoGetInfo();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getCryptoGetInfo().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		var op = query.getCryptoGetInfo();
		var key = fromAccountId(op.getAccountID());
		if (view.accounts().containsKey(key)) {
			var account = view.accounts().get(key);
			var estimate = factory.apply(query)
					.givenCurrentKey(asKeyUnchecked(account.getKey()))
					.givenCurrentMemo(account.getMemo())
					.givenCurrentTokenAssocs(account.tokens().numAssociations());
			if (account.getProxy() != null) {
				estimate.givenCurrentlyUsingProxy();
			}
			return estimate.get();
		} else {
			return FeeData.getDefaultInstance();
		}
	}
}
