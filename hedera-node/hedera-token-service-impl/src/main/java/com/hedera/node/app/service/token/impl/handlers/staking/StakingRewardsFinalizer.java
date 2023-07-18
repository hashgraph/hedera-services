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
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakeIdChangeType.FROM_ACCOUNT_TO_ACCOUNT;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.utils.RewardCalculator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.AccountsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StakingRewardsFinalizer {
    private final RewardCalculator rewardCalculator;
    private final StakingRewardHelper stakingRewardHelper;

    @Inject
    public StakingRewardsFinalizer(
            @NonNull final RewardCalculator rewardCalculator, @NonNull final StakingRewardHelper stakingRewardHelper) {
        this.rewardCalculator = requireNonNull(rewardCalculator);
        this.stakingRewardHelper = requireNonNull(stakingRewardHelper);
    }

    public void applyStakingRewards(final HandleContext context) {
        final var readableAccountStore = context.readableStore(ReadableAccountStore.class);
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var stakingRewardsStore = context.writableStore(WritableNetworkStakingRewardsStore.class);
        final var accountsConfig = context.configuration().getConfigData(AccountsConfig.class);
        final var stakingRewardAccount = accountsConfig.stakingRewardAccount();

        // Apply all changes related to stakedId changes
        applyStakedToMeChanges(writableAccountStore, readableAccountStore);
        final var possibleRewardReceivers =
                stakingRewardHelper.getPossibleRewardReceivers(writableAccountStore, readableAccountStore);
        final var totalPaidRewards =
                payRewardsIfPending(possibleRewardReceivers, writableAccountStore, stakingRewardsStore);
        finalizeStakeMetadata(writableAccountStore);
        decreaseStakingRewardAccountBalance(totalPaidRewards, stakingRewardAccount, writableAccountStore);
    }

    private void finalizeStakeMetadata(final WritableAccountStore writableAccountStore) {}

    private void decreaseStakingRewardAccountBalance(
            final long totalPaidRewards,
            final long stakingRewardAccountNum,
            final WritableAccountStore writableAccountStore) {
        if (totalPaidRewards > 0) {
            final var stakingRewardAccount = writableAccountStore.get(asAccount(stakingRewardAccountNum));
            final var finalBalance = stakingRewardAccount.tinybarBalance() - totalPaidRewards;
            // TODO : Should we throw if balance < 0

            final var copy = stakingRewardAccount.copyBuilder();
            copy.tinybarBalance(finalBalance);
            writableAccountStore.put(copy.build());
        }
    }

    /**
     * If there is an account X that staked to account Y. If account Y is staked to a node, then
     * change in X balance will contribute to Y's stakedToMe balance. This function will update
     * Y's stakedToMe balance.
     * @param writableAccountStore The store to write to for updated values
     * @param readableAccountStore The store to read from for original values
     */
    public void applyStakedToMeChanges(
            @NonNull final WritableAccountStore writableAccountStore,
            @NonNull final ReadableAccountStore readableAccountStore) {
        final var modifiedAccounts = writableAccountStore.modifiedAccountsInState();
        for (final var id : modifiedAccounts) {
            final var originalAccount = readableAccountStore.getAccountById(id);
            final var modifiedAccount = writableAccountStore.get(id);

            final var originalAccountStakedAccountId =
                    originalAccount != null ? originalAccount.stakedAccountId() : null;
            final var modifiedAccountStakedAccountId = modifiedAccount.stakedAccountId();

            final var scenario = StakeIdChangeType.forCase(
                    originalAccount.stakedId().kind(),
                    modifiedAccount.stakedId().kind());

            // If the stakedId is changed from account or to account. Then we need to update the
            // stakedToMe balance of new account. This is needed in order to trigger next level rewards
            // if the account is staked to node
            if (scenario.equals(FROM_ACCOUNT_TO_ACCOUNT)
                    && originalAccount.stakedAccountId().equals(modifiedAccountStakedAccountId)) {
                final var roundedFinalBalance = roundedToHbar(modifiedAccount.tinybarBalance());
                final var roundedInitialBalance = roundedToHbar(originalAccount.tinybarBalance());
                final var delta = roundedFinalBalance - roundedInitialBalance;
                // Even if the stakee's total stake hasn't changed, we still want to
                // trigger a reward situation whenever the staker balance changes;
                if (roundedFinalBalance != roundedInitialBalance) {
                    updateStakedToMeFor(modifiedAccountStakedAccountId, delta, writableAccountStore);
                }
            } else {
                if (scenario.withdrawsFromAccount()) {
                    final var roundedInitialBalance = roundedToHbar(originalAccount.tinybarBalance());
                    // Always trigger a reward situation for the old stakee when they are
                    // losing an indirect staker, even if it doesn't change their total stake
                    updateStakedToMeFor(originalAccountStakedAccountId, -roundedInitialBalance, writableAccountStore);
                }
                if (scenario.awardsToAccount()) {
                    // Always trigger a reward situation for the new stakee when they are
                    // losing an indirect staker, even if it doesn't change their total stake
                    final var roundedFinalBalance = roundedToHbar(originalAccount.tinybarBalance());
                    updateStakedToMeFor(modifiedAccountStakedAccountId, roundedFinalBalance, writableAccountStore);
                }
            }
        }
    }

    private void updateStakedToMeFor(
            @NonNull final AccountID stakee,
            final long roundedFinalBalance,
            @NonNull final WritableAccountStore writableAccountStore) {
        final var account = writableAccountStore.get(stakee);
        final var initialStakedToMe = account.stakedToMe();
        final var copy = account.copyBuilder()
                .stakedToMe(initialStakedToMe + roundedFinalBalance)
                .build();
        writableAccountStore.put(copy);
    }

    private long roundedToHbar(final long value) {
        return (value / HBARS_TO_TINYBARS) * HBARS_TO_TINYBARS;
    }

    public long payRewardsIfPending(
            @NonNull final Set<AccountID> possibleRewardReceivers,
            @NonNull final WritableAccountStore writableAccountStore,
            final WritableNetworkStakingRewardsStore stakingRewardsStore) {
        requireNonNull(possibleRewardReceivers);
        var totalRewardPaid = 0;
        for (final var receiver : possibleRewardReceivers) {
            var receiverAccount = writableAccountStore.get(receiver);
            final var reward = rewardCalculator.computePendingReward(receiverAccount);
            if (reward <= 0) {
                continue;
            }
            stakingRewardHelper.decreasePendingRewardsBy(stakingRewardsStore, reward);
            if (receiverAccount.deleted()) {
                // TODO: If the account is deleted transfer reward to beneficiary.
                // Will be a loop for ContractCalls. Need to check if it is a contract call.
            }
            if (!receiverAccount.declineReward()) {
                applyReward(reward, receiverAccount, writableAccountStore);
                totalRewardPaid += reward;
            }
        }
        return totalRewardPaid;
    }

    private void decreaseNewFundingBalanceBy(
            final int totalRewardPaid, final WritableAccountStore writableAccountStore) {
        final var newFundingAccount = writableAccountStore.get(newFundingAccount());
        final var newFundingAccountCopy = newFundingAccount
                .copyBuilder()
                .tinybarBalance(newFundingAccount.tinybarBalance() - totalRewardPaid)
                .build();
        writableAccountStore.put(newFundingAccountCopy);
    }

    private void applyReward(
            final long reward, final Account receiver, final WritableAccountStore writableAccountStore) {
        final var finalBalance = receiver.tinybarBalance() + reward;
        final var copy = receiver.copyBuilder();
        copy.tinybarBalance(finalBalance);
        writableAccountStore.put(copy.build());
    }
}
