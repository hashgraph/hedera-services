/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.staking;

import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for staking reward
 */
@Singleton
public class StakingRewardHelper {
    private static final Logger log = LogManager.getLogger(StakingRewardHelper.class);
    public static final long MAX_PENDING_REWARDS = 50_000_000_000L * HBARS_TO_TINYBARS;

    @Inject
    public StakingRewardHelper() {}

    /**
     * Looks through all the accounts modified in state and returns a list of accounts which are staked to a node
     * and has stakedId or stakedToMe or balance or declineReward changed in this transaction.
     * @param writableAccountStore The store to write to for updated values
     * @param readableAccountStore The store to read from for original values
     * @return A list of accounts which are staked to a node and could possibly receive a reward
     */
    public Set<AccountID> getPossibleRewardReceivers(
            final WritableAccountStore writableAccountStore, final ReadableAccountStore readableAccountStore) {
        final var possibleRewardReceivers = new HashSet<AccountID>();
        for (final AccountID modifiedId : writableAccountStore.modifiedAccountsInState()) {
            final var modifiedAcct = writableAccountStore.get(modifiedId);
            final var originalAcct = readableAccountStore.getAccountById(modifiedId);
            // It is possible that original account is null if the account was created in this transaction
            // In that case it is not a reward situation
            // If the account existed before this transaction and is staked to a node,
            // and the current transaction modified the stakedToMe field or declineReward or
            // the stakedId field, then it is a reward situation
            if (isRewardSituation(modifiedAcct, originalAcct)) {
                possibleRewardReceivers.add(modifiedId);
            }
        }
        return possibleRewardReceivers;
    }

    /**
     * Returns true if the account is staked to a node and the current transaction modified the stakedToMe field
     * (by changing balance of the current account or the account which is staking to current account) or
     * declineReward or the stakedId field
     * @param modifiedAccount the account which is modified in the current transaction and is in modifications
     * @param originalAccount the account before the current transaction
     * @return true if the account is staked to a node and the current transaction modified the stakedToMe field
     */
    private boolean isRewardSituation(@NonNull final Account modifiedAccount, @Nullable final Account originalAccount) {
        requireNonNull(modifiedAccount);
        if (originalAccount == null) {
            return false;
        }
        final var hasStakedToMeUpdate = modifiedAccount.stakedToMe() != originalAccount.stakedToMe();
        final var hasBalanceChange = modifiedAccount.tinybarBalance() != originalAccount.tinybarBalance();
        final var hasStakeMetaChanges = (modifiedAccount.declineReward() != originalAccount.declineReward())
                || (modifiedAccount.stakedId() != originalAccount.stakedId());
        final var isStakedToNode = originalAccount.hasStakedNodeId() && originalAccount.stakedNodeId() >= 0L;
        // TODO: Look for all read keys if they are smart contracts
        return modifiedAccount.smartContract()
                || (isStakedToNode && (hasStakedToMeUpdate || hasBalanceChange || hasStakeMetaChanges));
    }

    public void decreasePendingRewardsBy(final WritableNetworkStakingRewardsStore stakingRewardsStore, long amount) {
        final var currentPendingRewards = stakingRewardsStore.pendingRewards();
        var newPendingRewards = currentPendingRewards - amount;
        if (newPendingRewards < 0) {
            log.error(
                    "Pending rewards decreased by {} to a meaningless {}, fixing to zero hbar",
                    amount,
                    newPendingRewards);
            newPendingRewards = 0;
        }
        final var stakingRewards = stakingRewardsStore.get();
        final var copy = stakingRewards.copyBuilder();
        stakingRewardsStore.put(copy.pendingRewards(newPendingRewards).build());
    }

    public void increasePendingRewardsBy(final WritableNetworkStakingRewardsStore stakingRewardsStore, long amount) {
        final var currentPendingRewards = stakingRewardsStore.pendingRewards();
        var newPendingRewards = currentPendingRewards + amount;
        if (newPendingRewards > MAX_PENDING_REWARDS) {
            log.error(
                    "Pending rewards increased by {} to an un-payable {}, fixing to 50B hbar",
                    amount,
                    newPendingRewards);
            newPendingRewards = MAX_PENDING_REWARDS;
        }
        final var stakingRewards = stakingRewardsStore.get();
        final var copy = stakingRewards.copyBuilder();
        stakingRewardsStore.put(copy.pendingRewards(newPendingRewards).build());
    }

    public static long totalStake(@NonNull Account account) {
        return account.tinybarBalance() + account.stakedToMe();
    }
}
