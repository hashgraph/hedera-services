/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.accounts.staking.EndOfStakingPeriodCalculator;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import dagger.BindsInstance;
import dagger.Component;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Singleton
@Component(modules = StakingActivityModule.class)
public interface StakingActivityApp {
    RewardCalculator rewardCalculator();

    StakePeriodManager periodManager();

    TransactionContext txnCtx();

    SideEffectsTracker sideEffects();

    EndOfStakingPeriodCalculator endOfPeriodCalcs();

    Supplier<MerkleNetworkContext> networkCtx();

    BackingStore<AccountID, HederaAccount> backingAccounts();

    Supplier<AccountStorageAdapter> accounts();

    Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfos();

    TransactionalLedger<AccountID, AccountProperty, HederaAccount> stakingLedger();

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder bundle(InfrastructureBundle bundle);

        StakingActivityApp build();
    }
}
