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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Helper class for paying out staking rewards.
 */
@Singleton
public class RewardsPayer {
    private RewardsHelper stakingRewardHelper;
    private StakeRewardCalculatorImpl rewardCalculator;

    @Inject
    public RewardsPayer(
            @NonNull final RewardsHelper stakingRewardHelper,
            @NonNull final StakeRewardCalculatorImpl rewardCalculator) {
        this.stakingRewardHelper = stakingRewardHelper;
        this.rewardCalculator = rewardCalculator;
    }

    public Map<AccountID, Long> payRewardsIfPending(
            @NonNull final Set<AccountID> possibleRewardReceivers,
            @NonNull final WritableAccountStore writableStore,
            @NonNull final WritableNetworkStakingRewardsStore stakingRewardsStore,
            @NonNull final WritableStakingInfoStore stakingInfoStore,
            @NonNull final Instant consensusNow) {
        requireNonNull(possibleRewardReceivers);

        final Map<AccountID, Long> rewardsPaid = new HashMap<>();
        for (final var receiver : possibleRewardReceivers) {
            var receiverAccount = writableStore.get(receiver);
            final var reward = rewardCalculator.computePendingReward(
                    receiverAccount, stakingInfoStore, stakingRewardsStore, consensusNow);
            rewardsPaid.merge(receiver, reward, Long::sum);

            if (reward <= 0) {
                continue;
            }
            stakingRewardHelper.decreasePendingRewardsBy(stakingRewardsStore, reward);
            if (receiverAccount.deleted()) {
                // TODO: If the account is deleted transfer reward to beneficiary.
                // Will be a loop for ContractCalls. Need to check if it is a contract call.
            }
            if (!receiverAccount.declineReward()) {
                applyReward(reward, receiverAccount, writableStore);
            }
        }
        return rewardsPaid;
    }

    /**
     * Applies the reward to the receiver. This is done by updating the receiver's balance.
     * @param reward The reward to apply.
     * @param receiver The account that will receive the reward.
     * @param writableStore The store to update the receiver's balance in.
     */
    private void applyReward(final long reward, final Account receiver, final WritableAccountStore writableStore) {
        final var finalBalance = receiver.tinybarBalance() + reward;
        final var copy = receiver.copyBuilder();
        copy.tinybarBalance(finalBalance);
        writableStore.put(copy.build());
    }
}
