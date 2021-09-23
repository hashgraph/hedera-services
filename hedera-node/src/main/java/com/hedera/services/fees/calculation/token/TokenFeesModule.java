package com.hedera.services.fees.calculation.token;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *
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

import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.token.queries.GetAccountNftInfosResourceUsage;
import com.hedera.services.fees.calculation.token.queries.GetTokenInfoResourceUsage;
import com.hedera.services.fees.calculation.token.queries.GetTokenNftInfoResourceUsage;
import com.hedera.services.fees.calculation.token.queries.GetTokenNftInfosResourceUsage;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import java.util.Set;

@Module
public abstract class TokenFeesModule {
	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideTokenQueryEstimators(
			GetTokenInfoResourceUsage getTokenInfoResourceUsage,
			GetTokenNftInfoResourceUsage getTokenNftInfoResourceUsage,
			GetTokenNftInfosResourceUsage getTokenNftInfosResourceUsage,
			GetAccountNftInfosResourceUsage getAccountNftInfosResourceUsage
	) {
		return Set.of(
				getTokenInfoResourceUsage,
				getTokenNftInfoResourceUsage,
				getTokenNftInfosResourceUsage,
				getAccountNftInfosResourceUsage);
	}
}
