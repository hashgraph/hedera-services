package com.hedera.services.fees;

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
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.calculation.consensus.queries.GetTopicInfoResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetBytecodeResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetContractInfoResourceUsage;
import com.hedera.services.fees.calculation.contract.queries.GetContractRecordsResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCallResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCreateResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractDeleteResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractUpdateResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountInfoResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountRecordsResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoCreateResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoDeleteResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoUpdateResourceUsage;
import com.hedera.services.fees.calculation.file.queries.GetFileContentsResourceUsage;
import com.hedera.services.fees.calculation.file.queries.GetFileInfoResourceUsage;
import com.hedera.services.fees.calculation.meta.queries.GetVersionInfoResourceUsage;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.fees.charging.NarratedLedgerCharging;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;

import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;

@Module
public abstract class FeesModule {
	@Binds
	@Singleton
	public abstract FeeCalculator bindFeeCalculator(UsageBasedFeeCalculator usageBasedFeeCalculator);

	@Binds
	@Singleton
	public abstract UsagePricesProvider bindUsagePricesProvider(BasicFcfsUsagePrices basicFcfsUsagePrices);

	@Binds
	@Singleton
	public abstract FeeExemptions bindFeeExemptions(StandardExemptions standardExemptions);

	@Binds
	@Singleton
	public abstract FeeMultiplierSource bindFeeMultiplierSource(TxnRateFeeMultiplierSource txnRateFeeMultiplierSource);

	@Binds
	@Singleton
	public abstract NarratedCharging bindNarratedCharging(NarratedLedgerCharging narratedLedgerCharging);

	@Binds
	@Singleton
	public abstract HbarCentExchange bindHbarCentExchange(BasicHbarCentExchange basicHbarCentExchange);

	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideMetaQueryEstimators(
			GetVersionInfoResourceUsage getVersionInfoResourceUsage,
			GetTxnRecordResourceUsage getTxnRecordResourceUsage
	) {
		return Set.of(getVersionInfoResourceUsage, getTxnRecordResourceUsage);
	}

	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideCryptoQueryEstimators(
			GetAccountInfoResourceUsage getAccountInfoResourceUsage,
			GetAccountRecordsResourceUsage getAccountRecordsResourceUsage
	) {
		return Set.of(getAccountInfoResourceUsage, getAccountRecordsResourceUsage);
	}

	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideFileQueryEstimators(
			GetFileInfoResourceUsage getFileInfoResourceUsage,
			GetFileContentsResourceUsage getFileContentsResourceUsage
	) {
		return Set.of(getFileInfoResourceUsage, getFileContentsResourceUsage);
	}

	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideConsensusQueryEstimators(
			GetTopicInfoResourceUsage getTopicInfoResourceUsage
	) {
		return Set.of(getTopicInfoResourceUsage);
	}

	@Provides
	@ElementsIntoSet
	public static Set<QueryResourceUsageEstimator> provideContractQueryEstimators(
			GetBytecodeResourceUsage getBytecodeResourceUsage,
			GetContractInfoResourceUsage getContractInfoResourceUsage,
			GetContractRecordsResourceUsage getContractRecordsResourceUsage
	) {
		return Set.of(getBytecodeResourceUsage, getContractInfoResourceUsage, getContractRecordsResourceUsage);
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

	@Provides
	@IntoMap
	@FunctionKey(ContractCall)
	public static List<TxnResourceUsageEstimator> provideContractCallUpdateEstimator(
			ContractCallResourceUsage contractCallResourceUsage
	) {
		return List.of(contractCallResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(ContractCreate)
	public static List<TxnResourceUsageEstimator> provideContractCreateUpdateEstimator(
			ContractCreateResourceUsage contractCreateResourceUsage
	) {
		return List.of(contractCreateResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(ContractDelete)
	public static List<TxnResourceUsageEstimator> provideContractDeleteEstimator(
			ContractDeleteResourceUsage contractDeleteResourceUsage
	) {
		return List.of(contractDeleteResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(ContractUpdate)
	public static List<TxnResourceUsageEstimator> provideContractUpdateEstimator(
			ContractUpdateResourceUsage contractUpdateResourceUsage
	) {
		return List.of(contractUpdateResourceUsage);
	}
}
