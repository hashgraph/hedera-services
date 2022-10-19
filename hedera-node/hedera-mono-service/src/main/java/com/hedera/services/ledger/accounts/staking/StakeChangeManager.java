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
package com.hedera.services.ledger.accounts.staking;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.EnumMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StakeChangeManager {
    private final StakeInfoManager stakeInfoManager;
    private final Supplier<AccountStorageAdapter> accounts;

    @Inject
    public StakeChangeManager(
            final StakeInfoManager stakeInfoManager,
            final Supplier<AccountStorageAdapter> accounts) {
        this.stakeInfoManager = stakeInfoManager;
        this.accounts = accounts;
    }

    public void withdrawStake(
            final long curNodeId, final long amount, final boolean declinedReward) {
        final var node = stakeInfoManager.mutableStakeInfoFor(curNodeId);
        node.removeRewardStake(amount, declinedReward);
    }

    public void awardStake(final long newNodeId, final long amount, final boolean declinedReward) {
        final var node = stakeInfoManager.mutableStakeInfoFor(newNodeId);
        node.addRewardStake(amount, declinedReward);
    }

    public void initializeAllStakingStartsTo(final long todayNumber) {
        final var mutableAccounts = accounts.get();
        for (var key : mutableAccounts.keySet()) {
            final var account = mutableAccounts.getForModify(key);
            if (account.getStakedId() < 0) {
                account.setStakePeriodStart(todayNumber);
            }
        }
    }

    public int findOrAdd(
            final long accountNum,
            final EntityChangeSet<AccountID, HederaAccount, AccountProperty> pendingChanges) {
        final var n = pendingChanges.size();
        for (int i = 0; i < n; i++) {
            if (pendingChanges.id(i).getAccountNum() == accountNum) {
                return i;
            }
        }
        // This account wasn't in the current change set
        pendingChanges.include(
                STATIC_PROPERTIES.scopedAccountWith(accountNum),
                accounts.get().get(EntityNum.fromLong(accountNum)),
                new EnumMap<>(AccountProperty.class));
        return n;
    }
}
