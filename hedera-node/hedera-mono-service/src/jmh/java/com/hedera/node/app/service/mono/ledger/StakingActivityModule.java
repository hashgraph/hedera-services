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
package com.hedera.node.app.service.mono.ledger;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STORE_ON_DISK;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_PERIOD_MINS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_RATE;
import static com.hedera.node.app.service.mono.mocks.MockDynamicProperties.mockPropertiesWith;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.*;
import com.hedera.node.app.service.mono.ledger.accounts.staking.RewardCalculator;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeChangeManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeInfoManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakePeriodManager;
import com.hedera.node.app.service.mono.ledger.backing.BackingAccounts;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.interceptors.StakingAccountsCommitInterceptor;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.ChangeSummaryManager;
import com.hedera.node.app.service.mono.mocks.MockAccountTracking;
import com.hedera.node.app.service.mono.mocks.MockEntityCreator;
import com.hedera.node.app.service.mono.mocks.MockProps;
import com.hedera.node.app.service.mono.mocks.MockRecordsHistorian;
import com.hedera.node.app.service.mono.mocks.MockTransactionContext;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.setup.InfrastructureBundle;
import com.hedera.node.app.service.mono.setup.InfrastructureType;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.validation.AccountUsageTracking;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.test.mocks.MockAccountNumbers;
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
    BackingStore<AccountID, HederaAccount> bindBackingAccounts(BackingAccounts backingAccounts);

    @Provides
    @Singleton
    static GlobalDynamicProperties provideGlobalDynamicProperties() {
        return mockPropertiesWith(500_000_000, 163_840);
    }

    @Provides
    @Singleton
    @SuppressWarnings("unchecked")
    static Supplier<AccountStorageAdapter> provideAccountsSupplier(
            final InfrastructureBundle bundle) {
        return () ->
                AccountStorageAdapter.fromInMemory(MerkleMapLike.from(
                        (MerkleMap<EntityNum, MerkleAccount>)
                                bundle.getterFor(InfrastructureType.ACCOUNTS_MM).get()));
    }

    @Provides
    @Singleton
    @SuppressWarnings("unchecked")
    static Supplier<RecordsStorageAdapter> providePayerRecordsSupplier(
            final InfrastructureBundle bundle) {
        return () ->
                RecordsStorageAdapter.fromLegacy(
                        (MerkleMap<EntityNum, MerkleAccount>)
                                bundle.getterFor(InfrastructureType.ACCOUNTS_MM).get());
    }

    @Provides
    @Singleton
    static Supplier<MerkleMapLike<EntityNum, MerkleStakingInfo>> provideStakingInfosSupplier(
            final InfrastructureBundle bundle) {
        return bundle.getterFor(InfrastructureType.STAKING_INFOS_MM);
    }

    @Provides
    @Singleton
    static Supplier<MerkleNetworkContext> provideNetworkContext() {
        final var networkCtx = new MerkleNetworkContext();
        return () -> networkCtx;
    }

    @Provides
    @Singleton
    @MockProps
    static HederaAccountNumbers bindAccountNumbers() {
        return new MockAccountNumbers();
    }

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
    static TransactionalLedger<AccountID, AccountProperty, HederaAccount> provideAccountsLedger(
            final BackingStore<AccountID, HederaAccount> backingAccounts,
            final SideEffectsTracker sideEffectsTracker,
            final BootstrapProperties bootstrapProperties,
            final Supplier<MerkleNetworkContext> networkCtx,
            final GlobalDynamicProperties dynamicProperties,
            final RewardCalculator rewardCalculator,
            final StakeChangeManager stakeChangeManager,
            final StakePeriodManager stakePeriodManager,
            final StakeInfoManager stakeInfoManager,
            final @MockProps HederaAccountNumbers accountNumbers,
            final TransactionContext txnCtx,
            final AccountUsageTracking usageTracking) {
        final Supplier<HederaAccount> accountSupplier =
                bootstrapProperties.getBooleanProperty(ACCOUNTS_STORE_ON_DISK)
                        ? OnDiskAccount::new
                        : MerkleAccount::new;
        final var accountsLedger =
                new TransactionalLedger<>(
                        AccountProperty.class,
                        accountSupplier,
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
