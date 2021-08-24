package com.hedera.services.fees.calculation.crypto;

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

import com.hedera.services.fees.annotations.FunctionKey;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountInfoResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountRecordsResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoCreateResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoDeleteResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoUpdateResourceUsage;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;

import java.util.List;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;

@Module
public abstract class CryptoFeesModule {
	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideCryptoQueryEstimators(
			GetAccountInfoResourceUsage getAccountInfoResourceUsage,
			GetAccountRecordsResourceUsage getAccountRecordsResourceUsage
	) {
		return Set.of(getAccountInfoResourceUsage, getAccountRecordsResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoCreate)
	public static List<TxnResourceUsageEstimator> provideCryptoCreateEstimator(
			CryptoCreateResourceUsage cryptoCreateResourceUsage
	) {
		return List.of(cryptoCreateResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoDelete)
	public static List<TxnResourceUsageEstimator> provideCryptoDeleteEstimator(
			CryptoDeleteResourceUsage cryptoDeleteResourceUsage
	) {
		return List.of(cryptoDeleteResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(CryptoUpdate)
	public static List<TxnResourceUsageEstimator> provideCryptoUpdateEstimator(
			CryptoUpdateResourceUsage cryptoUpdateResourceUsage
	) {
		return List.of(cryptoUpdateResourceUsage);
	}
}
