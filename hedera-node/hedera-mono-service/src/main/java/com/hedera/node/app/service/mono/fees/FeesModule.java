/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
 */

package com.hedera.node.app.service.mono.fees;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;

import com.hedera.node.app.service.mono.fees.annotations.FunctionKey;
import com.hedera.node.app.service.mono.fees.annotations.GasPriceMultiplier;
import com.hedera.node.app.service.mono.fees.annotations.GenericPriceMultiplier;
import com.hedera.node.app.service.mono.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.node.app.service.mono.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.node.app.service.mono.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.fees.calculation.consensus.ConsensusFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.contract.ContractFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.crypto.CryptoFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.crypto.queries.GetAccountDetailsResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.ethereum.EthereumFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.file.FileFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.file.txns.SystemDeleteFileResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.file.txns.SystemUndeleteFileResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.meta.queries.GetExecTimeResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.meta.queries.GetVersionInfoResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.schedule.ScheduleFeesModule;
import com.hedera.node.app.service.mono.fees.calculation.system.txns.FreezeResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.system.txns.UncheckedSubmitResourceUsage;
import com.hedera.node.app.service.mono.fees.calculation.token.TokenFeesModule;
import com.hedera.node.app.service.mono.fees.charging.NarratedCharging;
import com.hedera.node.app.service.mono.fees.charging.NarratedLedgerCharging;
import com.hedera.node.app.service.mono.fees.charging.RecordedStorageFeeCharging;
import com.hedera.node.app.service.mono.fees.charging.StorageFeeCharging;
import com.hedera.node.app.service.mono.fees.congestion.FeeMultiplierSource;
import com.hedera.node.app.service.mono.fees.congestion.GasPriceMultiplierSource;
import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import com.hedera.node.app.service.mono.fees.congestion.TxnRateFeeMultiplierSource;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;

@Module(
        includes = {
            FileFeesModule.class,
            TokenFeesModule.class,
            CryptoFeesModule.class,
            ContractFeesModule.class,
            EthereumFeesModule.class,
            ScheduleFeesModule.class,
            ConsensusFeesModule.class,
        })
public interface FeesModule {
    @Binds
    @Singleton
    StorageFeeCharging bindStorageFeeCharging(RecordedStorageFeeCharging recordedStorageFeeCharging);

    @Binds
    @Singleton
    FeeCalculator bindFeeCalculator(UsageBasedFeeCalculator usageBasedFeeCalculator);

    @Binds
    @Singleton
    UsagePricesProvider bindUsagePricesProvider(BasicFcfsUsagePrices basicFcfsUsagePrices);

    @Binds
    @Singleton
    FeeExemptions bindFeeExemptions(StandardExemptions standardExemptions);

    @Binds
    @Singleton
    @GenericPriceMultiplier
    FeeMultiplierSource bindGenericFeeMultiplierSource(TxnRateFeeMultiplierSource txnRateFeeMultiplierSource);

    @Binds
    @Singleton
    @GasPriceMultiplier
    FeeMultiplierSource bindGasFeeMultiplierSource(GasPriceMultiplierSource gasPriceMultiplierSource);

    @Provides
    @Singleton
    static MultiplierSources provideMultiplierSources(
            final @GasPriceMultiplier FeeMultiplierSource gasMultiplier,
            final @GenericPriceMultiplier FeeMultiplierSource genericMultiplier) {
        return new MultiplierSources(genericMultiplier, gasMultiplier);
    }

    @Binds
    @Singleton
    NarratedCharging bindNarratedCharging(NarratedLedgerCharging narratedLedgerCharging);

    @Binds
    @Singleton
    HbarCentExchange bindHbarCentExchange(BasicHbarCentExchange basicHbarCentExchange);

    @Provides
    @ElementsIntoSet
    static Set<QueryResourceUsageEstimator> provideMetaQueryEstimators(
            GetVersionInfoResourceUsage getVersionInfoResourceUsage,
            GetTxnRecordResourceUsage getTxnRecordResourceUsage,
            GetExecTimeResourceUsage getExecTimeResourceUsage,
            GetAccountDetailsResourceUsage getAccountDetailsResourceUsage) {
        return Set.of(
                getVersionInfoResourceUsage,
                getTxnRecordResourceUsage,
                getExecTimeResourceUsage,
                getAccountDetailsResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(Freeze)
    static List<TxnResourceUsageEstimator> provideFreezeEstimator(FreezeResourceUsage freezeResourceUsage) {
        return List.of(freezeResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(UncheckedSubmit)
    static List<TxnResourceUsageEstimator> provideUncheckedSubmitEstimator(
            UncheckedSubmitResourceUsage uncheckedResourceUsage) {
        return List.of(uncheckedResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(SystemDelete)
    static List<TxnResourceUsageEstimator> provideSystemDeleteEstimator(
            SystemDeleteFileResourceUsage systemDeleteFileResourceUsage) {
        return List.of(systemDeleteFileResourceUsage);
    }

    @Provides
    @IntoMap
    @FunctionKey(SystemUndelete)
    static List<TxnResourceUsageEstimator> provideSystemUndeleteEstimator(
            SystemUndeleteFileResourceUsage systemUndeleteFileResourceUsage) {
        return List.of(systemUndeleteFileResourceUsage);
    }
}
