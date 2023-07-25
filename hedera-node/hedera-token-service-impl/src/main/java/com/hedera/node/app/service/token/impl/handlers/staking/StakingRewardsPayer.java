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
import com.hedera.node.app.service.token.ReadableAccountStore;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for paying out staking rewards.
 */
@Singleton
public class StakingRewardsPayer {
    private static final Logger log = LogManager.getLogger(StakingRewardsPayer.class);
    private StakingRewardsHelper stakingRewardHelper;
    private StakeRewardCalculatorImpl rewardCalculator;

    @Inject
    public StakingRewardsPayer(
            @NonNull final StakingRewardsHelper stakingRewardHelper,
            @NonNull final StakeRewardCalculatorImpl rewardCalculator) {
        this.stakingRewardHelper = stakingRewardHelper;
        this.rewardCalculator = rewardCalculator;
    }

    public Map<AccountID, Long> payRewardsIfPending(
            @NonNull final Set<AccountID> possibleRewardReceivers,
            @NonNull final ReadableAccountStore readableStore,
            @NonNull final WritableAccountStore writableStore,
            @NonNull final WritableNetworkStakingRewardsStore stakingRewardsStore,
            @NonNull final WritableStakingInfoStore stakingInfoStore,
            @NonNull final Instant consensusNow) {
        requireNonNull(possibleRewardReceivers);

        final Map<AccountID, Long> rewardsPaid = new HashMap<>();
        for (final var receiver : possibleRewardReceivers) {
            final var originalAccount = readableStore.getAccountById(receiver);
            final var modifiedAccount = writableStore.get(receiver);
            final var reward = rewardCalculator.computePendingReward(
                    originalAccount, stakingInfoStore, stakingRewardsStore, consensusNow);

            var receiverId = receiver;
            var beneficiary = originalAccount;
            if (reward > 0) {
                stakingRewardHelper.decreasePendingRewardsBy(stakingRewardsStore, reward);

                // We cannot reward a deleted account, so keep redirecting to the beneficiaries of deleted
                // accounts until we find a non-deleted account to try to reward (it may still decline)
                if (originalAccount.deleted()) {
                    // TODO: need to get this info ?
                    final var maxRedirects = 0;
                    var j = 1;
                    do {
                        if (j++ > maxRedirects) {
                            log.error(
                                    "With {}5 accounts deleted, last redirect in modifications led to deleted"
                                            + " beneficiary 0.0.{}",
                                    maxRedirects,
                                    receiverId);
                            throw new IllegalStateException("Had to redirect reward to a deleted beneficiary");
                        }
                        // TODO: need to get this info ?
                        //                    receiverId = txnCtx.getBeneficiaryOfDeleted(receiverNum);
                        beneficiary = writableStore.get(receiverId);
                    } while (beneficiary.deleted());
                }
            }

            if (!beneficiary.declineReward() && reward >= 0) {
                // even if reward is zero it will be added to rewardsPaid
                applyReward(reward, modifiedAccount, writableStore);
                rewardsPaid.merge(receiver, reward, Long::sum);
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
