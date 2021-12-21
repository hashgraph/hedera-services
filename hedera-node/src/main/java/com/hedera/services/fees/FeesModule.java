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
import com.hedera.services.fees.calculation.consensus.ConsensusFeesModule;
import com.hedera.services.fees.calculation.contract.ContractFeesModule;
import com.hedera.services.fees.calculation.crypto.CryptoFeesModule;
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hedera.services.fees.calculation.file.FileFeesModule;
import com.hedera.services.fees.calculation.file.txns.SystemDeleteFileResourceUsage;
import com.hedera.services.fees.calculation.file.txns.SystemUndeleteFileResourceUsage;
import com.hedera.services.fees.calculation.meta.queries.GetExecTimeResourceUsage;
import com.hedera.services.fees.calculation.meta.queries.GetVersionInfoResourceUsage;
import com.hedera.services.fees.calculation.schedule.ScheduleFeesModule;
import com.hedera.services.fees.calculation.system.txns.FreezeResourceUsage;
import com.hedera.services.fees.calculation.system.txns.UncheckedSubmitResourceUsage;
import com.hedera.services.fees.calculation.token.TokenFeesModule;
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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;

@Module(includes = {
		FileFeesModule.class,
		TokenFeesModule.class,
		CryptoFeesModule.class,
		ContractFeesModule.class,
		ScheduleFeesModule.class,
		ConsensusFeesModule.class,
})
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
			GetTxnRecordResourceUsage getTxnRecordResourceUsage,
			GetExecTimeResourceUsage getExecTimeResourceUsage
	) {
		return Set.of(getVersionInfoResourceUsage, getTxnRecordResourceUsage, getExecTimeResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(Freeze)
	public static List<TxnResourceUsageEstimator> provideFreezeEstimator(
			FreezeResourceUsage freezeResourceUsage
	) {
		return List.of(freezeResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(UncheckedSubmit)
	public static List<TxnResourceUsageEstimator> provideUncheckedSubmitEstimator(
			UncheckedSubmitResourceUsage uncheckedResourceUsage
	) {
		return List.of(uncheckedResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(SystemDelete)
	public static List<TxnResourceUsageEstimator> provideSystemDeleteEstimator(
			SystemDeleteFileResourceUsage systemDeleteFileResourceUsage
	) {
		return List.of(systemDeleteFileResourceUsage);
	}

	@Provides
	@IntoMap
	@FunctionKey(SystemUndelete)
	public static List<TxnResourceUsageEstimator> provideSystemUndeleteEstimator(
			SystemUndeleteFileResourceUsage systemUndeleteFileResourceUsage
	) {
		return List.of(systemUndeleteFileResourceUsage);
	}
}
