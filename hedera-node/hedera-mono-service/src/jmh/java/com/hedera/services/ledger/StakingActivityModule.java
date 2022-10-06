/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_PERIOD_MINS;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_RATE;
import static com.hedera.services.mocks.MockDynamicProperties.mockPropertiesWith;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.SupplierMapPropertySource;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.interceptors.StakingAccountsCommitInterceptor;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.mocks.MockAccountNumbers;
import com.hedera.services.mocks.MockAccountTracking;
import com.hedera.services.mocks.MockEntityCreator;
import com.hedera.services.mocks.MockProps;
import com.hedera.services.mocks.MockRecordsHistorian;
import com.hedera.services.mocks.MockTransactionContext;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.setup.InfrastructureType;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.validation.AccountUsageTracking;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public interface StakingActivityModule {
    @Binds
    @Singleton
    EntityCreator bindEntityCreator(MockEntityCreator entityCreator);

    @Binds
    @Singleton
    RecordsHistorian bindRecordsHistorian(MockRecordsHistorian recordsHistorian);

    @Binds
    @Singleton
    TransactionContext bindTransactionContext(MockTransactionContext transactionContext);

    @Binds
    @Singleton
    BackingStore<AccountID, MerkleAccount> bindBackingAccounts(BackingAccounts backingAccounts);

    @Provides
    @Singleton
    static GlobalDynamicProperties provideGlobalDynamicProperties() {
        return mockPropertiesWith(500_000_000, 163_840);
    }

    @Provides
    @Singleton
    static Supplier<MerkleMap<EntityNum, MerkleAccount>> provideAccountsSupplier(
            InfrastructureBundle bundle) {
        return bundle.getterFor(InfrastructureType.ACCOUNTS_MM);
    }

    @Provides
    @Singleton
    static Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> provideStakingInfosSupplier(
            InfrastructureBundle bundle) {
        return bundle.getterFor(InfrastructureType.STAKING_INFOS_MM);
    }

    @Provides
    @Singleton
    static Supplier<MerkleNetworkContext> provideNetworkContext() {
        final var networkCtx = new MerkleNetworkContext();
        return () -> networkCtx;
    }

    @Binds
    @Singleton
    @MockProps
    AccountNumbers bindAccountNumbers(MockAccountNumbers accountNumbers);

    @Binds
    @Singleton
    AccountUsageTracking bindUsageTracking(MockAccountTracking accountTracking);

    @Provides
    @Singleton
    @CompositeProps
    static PropertySource providePropertySource() {
        final Map<String, Supplier<Object>> source = new HashMap<>();
        source.put(STAKING_PERIOD_MINS, () -> 1440L);
        source.put(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS, () -> 365);
        source.put(STAKING_REWARD_RATE, () -> 273972602739726L);
        source.put(ACCOUNTS_STAKING_REWARD_ACCOUNT, () -> 800L);
        return new SupplierMapPropertySource(source);
    }

    @Provides
    @Singleton
    static TransactionalLedger<AccountID, AccountProperty, MerkleAccount> provideAccountsLedger(
            final BackingStore<AccountID, MerkleAccount> backingAccounts,
            final SideEffectsTracker sideEffectsTracker,
            final Supplier<MerkleNetworkContext> networkCtx,
            final GlobalDynamicProperties dynamicProperties,
            final RewardCalculator rewardCalculator,
            final StakeChangeManager stakeChangeManager,
            final StakePeriodManager stakePeriodManager,
            final StakeInfoManager stakeInfoManager,
            final @MockProps AccountNumbers accountNumbers,
            final TransactionContext txnCtx,
            final AccountUsageTracking usageTracking) {
        final var accountsLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        MerkleAccount::new,
                        backingAccounts,
                        new ChangeSummaryManager<>());
        final var accountsCommitInterceptor =
                new StakingAccountsCommitInterceptor(
                        sideEffectsTracker,
                        networkCtx,
                        dynamicProperties,
                        rewardCalculator,
                        stakeChangeManager,
                        stakePeriodManager,
                        stakeInfoManager,
                        accountNumbers,
                        txnCtx,
                        usageTracking);
        accountsLedger.setCommitInterceptor(accountsCommitInterceptor);
        return accountsLedger;
    }
}
