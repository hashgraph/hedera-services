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

import static com.hedera.node.app.service.mono.ledger.accounts.staking.StakingUtils.NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakeIdChangeType.FROM_ACCOUNT_TO_ACCOUNT;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.getPossibleRewardReceivers;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.hasStakeMetaChanges;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.roundedToHbar;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.totalStake;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.records.CryptoDeleteRecordBuilder;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.config.data.AccountsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class StakingRewardsHandlerImpl implements StakingRewardsHandler {
    private static final Logger log = LogManager.getLogger(StakingRewardsHandlerImpl.class);
    private final StakingRewardsDistributor rewardsPayer;
    private final StakePeriodManager stakePeriodManager;
    private final StakeInfoHelper stakeInfoHelper;

    @Inject
    public StakingRewardsHandlerImpl(
            @NonNull final StakingRewardsDistributor rewardsPayer,
            @NonNull final StakePeriodManager stakePeriodManager,
            @NonNull final StakeInfoHelper stakeInfoHelper) {
        this.rewardsPayer = requireNonNull(rewardsPayer);
        this.stakePeriodManager = requireNonNull(stakePeriodManager);
        this.stakeInfoHelper = requireNonNull(stakeInfoHelper);
    }

    /** {@inheritDoc} */
    @Override
    public Map<AccountID, Long> applyStakingRewards(final FinalizeContext context) {
        final var writableStore = context.writableStore(WritableAccountStore.class);
        final var stakingRewardsStore = context.writableStore(WritableNetworkStakingRewardsStore.class);
        final var stakingInfoStore = context.writableStore(WritableStakingInfoStore.class);
        final var accountsConfig = context.configuration().getConfigData(AccountsConfig.class);
        final var stakingRewardAccountId = asAccount(accountsConfig.stakingRewardAccount());
        final var consensusNow = context.consensusTime();

        // TODO: confirm if the getDeletedAccountBeneficiaries should be in
        //  SingleTransactionRecordBuilder interface instead
        final var recordBuilder = context.userTransactionRecordBuilder(CryptoDeleteRecordBuilder.class);

        // Apply all changes related to stakedId changes, and adjust stakedToMe
        // for all accounts staking to an account
        adjustStakedToMeForAccountStakees(writableStore);
        // Get list of possible reward receivers and pay rewards to them
        final var rewardReceivers = getPossibleRewardReceivers(writableStore);
        // Pay rewards to all possible reward receivers, returns all rewards paid
        final var rewardsPaid = rewardsPayer.payRewardsIfPending(
                rewardReceivers, writableStore, stakingRewardsStore, stakingInfoStore, consensusNow, recordBuilder);
        // Adjust stakes for nodes
        adjustStakeMetadata(writableStore, stakingInfoStore, stakingRewardsStore, consensusNow, rewardsPaid);
        // Decrease staking reward account balance by rewardPaid amount
        decreaseStakeRewardAccountBalance(rewardsPaid, stakingRewardAccountId, writableStore);
        return rewardsPaid;

        // TODO: Confirm if we need logic for activating staking ?
    }

    /**
     * Iterates through all modifications in state and sees if any account is staked to an account.
     * If there is an account X that staked to account Y. If account Y is staked to a node, then
     * change in X balance will contribute to Y's stakedToMe balance. This function will update
     * Y's stakedToMe balance which will add Y to the state modifications. In adjustStakeMetadata step, we will
     * assess if Y is staked to a node, and if so, we will update the node stake metadata.
     *
     * @param writableStore The store to write to for updated values
     */
    public void adjustStakedToMeForAccountStakees(@NonNull final WritableAccountStore writableStore) {
        final var modifiedAccounts = writableStore.modifiedAccountsInState();

        for (final var id : modifiedAccounts) {
            final var originalAccount = writableStore.getOriginalValue(id);
            final var modifiedAccount = writableStore.get(id);

            // check if stakedId has changed
            final var scenario = StakeIdChangeType.forCase(originalAccount, modifiedAccount);

            // If the stakedId is changed from account or to account. Then we need to update the
            // stakedToMe balance of new account. This is needed in order to trigger next level rewards
            // if the account is staked to node
            if (scenario.equals(FROM_ACCOUNT_TO_ACCOUNT)
                    && originalAccount.stakedAccountId().equals(modifiedAccount.stakedAccountId())) {
                final var roundedFinalBalance = roundedToHbar(modifiedAccount.tinybarBalance());
                final var roundedInitialBalance = roundedToHbar(originalAccount.tinybarBalance());
                final var delta = roundedFinalBalance - roundedInitialBalance;
                // Even if the stakee's total stake hasn't changed, we still want to
                // trigger a reward situation whenever the staker balance changes
                if (roundedFinalBalance != roundedInitialBalance) {
                    updateStakedToMeFor(modifiedAccount.stakedAccountId(), delta, writableStore);
                }
            } else {
                if (scenario.withdrawsFromAccount()) {
                    final var curStakedAccountId = originalAccount.stakedAccountId();
                    final var roundedInitialBalance = roundedToHbar(originalAccount.tinybarBalance());
                    // Always trigger a reward situation for the old stakee when they are
                    // losing an indirect staker, even if it doesn't change their total stake
                    updateStakedToMeFor(curStakedAccountId, -roundedInitialBalance, writableStore);
                }
                if (scenario.awardsToAccount()) {
                    final var newStakedAccountId = modifiedAccount.stakedAccountId();
                    final var balance = originalAccount == null ? 0 : originalAccount.tinybarBalance();
                    // Always trigger a reward situation for the new stakee when they are
                    // gaining an indirect staker, even if it doesn't change their total stake
                    final var roundedFinalBalance = roundedToHbar(balance);
                    updateStakedToMeFor(newStakedAccountId, roundedFinalBalance, writableStore);
                }
            }
        }
    }

    /**
     * If the account is updated to be staking to a node or withdraws staking from node, adjusts the stakes for those
     * nodes. It also updates stakeAtStartOfLastRewardedPeriod and stakePeriodStart for accounts.
     *
     * @param writableStore writable account store
     * @param stakingInfoStore writable staking info store
     * @param stakingRewardStore writable staking reward store
     * @param consensusNow consensus time
     * @param paidRewards map of account to rewards paid
     */
    private void adjustStakeMetadata(
            final WritableAccountStore writableStore,
            final WritableStakingInfoStore stakingInfoStore,
            final WritableNetworkStakingRewardsStore stakingRewardStore,
            final Instant consensusNow,
            final Map<AccountID, Long> paidRewards) {
        for (final var id : writableStore.modifiedAccountsInState()) {
            final var originalAccount = writableStore.getOriginalValue(id);
            final var modifiedAccount = writableStore.get(id);

            final var scenario = StakeIdChangeType.forCase(originalAccount, modifiedAccount);
            final var containStakeMetaChanges = hasStakeMetaChanges(originalAccount, modifiedAccount);

            // If this scenario is changing StakedId from a node or to a node, change stake of those nodes
            if ((scenario.withdrawsFromNode() || scenario.awardsToNode())) {
                adjustNodeStakes(
                        scenario,
                        originalAccount,
                        modifiedAccount,
                        stakingInfoStore,
                        stakingRewardStore,
                        containStakeMetaChanges,
                        consensusNow);
            }

            // If the account is rewarded. The reward can also be zero, if the account has zero stake
            final var isRewarded = paidRewards.containsKey(id);
            final var reward = paidRewards.getOrDefault(id, 0L);

            // If account chose to change decline reward field or stakeId field, we don't need
            // to update stakeAtStartOfLastRewardedPeriod because it is not rewarded for that period
            // Check if the stakeAtStartOfLastRewardedPeriod needs to be updated
            // If the account is autoCreated containStakeMetaChanges will not be true
            if (!containStakeMetaChanges
                    && shouldUpdateStakeStart(originalAccount, isRewarded, reward, stakingRewardStore, consensusNow)) {
                final var copy = modifiedAccount.copyBuilder();
                copy.stakeAtStartOfLastRewardedPeriod(roundedToHbar(totalStake(originalAccount)));
                writableStore.put(copy.build());
            }

            // Update stakePeriodStart if account is rewarded or if reward is zero and account
            // has zero stake
            // If the account is autoCreated it will not be rewarded
            final var wasRewarded = isRewarded
                    && (reward > 0
                            || (reward == 0
                                    && earnedZeroRewardsBecauseOfZeroStake(
                                            originalAccount, stakingRewardStore, consensusNow)));
            final var stakePeriodStart = stakePeriodManager.startUpdateFor(
                    originalAccount, modifiedAccount, wasRewarded, containStakeMetaChanges, consensusNow);
            if (stakePeriodStart != -1) {
                final var copy = modifiedAccount.copyBuilder();
                copy.stakePeriodStart(stakePeriodStart);
                writableStore.put(copy.build());
            }
        }
    }

    /**
     * Given an existing account that was in a reward situation and earned zero rewards, checks if
     * this was because the account had effective stake of zero whole hbars during the rewardable
     * periods. (The alternative is that it had zero rewardable periods; i.e., it started staking
     * this period, or the last.)
     *
     * <p>This distinction matters because in the case of zero stake, we still want to update the
     * account's {@code stakePeriodStart} and {@code stakeAtStartOfLastRewardedPeriod}. Otherwise,
     * we don't want to update {@code stakePeriodStart}; and only want to update {@code
     * stakeAtStartOfLastRewardedPeriod} if the account began staking in exactly the last period.
     *
     * @param account an account presumed to have just earned zero rewards
     * @return whether the zero rewards were due to having zero stake
     */
    private boolean earnedZeroRewardsBecauseOfZeroStake(
            @NonNull final Account account,
            @NonNull final ReadableNetworkStakingRewardsStore stakingRewardStore,
            @NonNull final Instant consensusNow) {
        return Objects.requireNonNull(account).stakePeriodStart()
                < stakePeriodManager.firstNonRewardableStakePeriod(stakingRewardStore, consensusNow);
    }

    private void adjustNodeStakes(
            final StakeIdChangeType scenario,
            final Account originalAccount,
            final Account modifiedAccount,
            final WritableStakingInfoStore stakingInfoStore,
            final WritableNetworkStakingRewardsStore stakingRewardStore,
            final boolean containStakeMetaChanges,
            final Instant consensusNow) {
        if (scenario.withdrawsFromNode()) {
            final var currentStakedNodeId = originalAccount.stakedNodeId();

            stakeInfoHelper.withdrawStake(currentStakedNodeId, originalAccount, stakingInfoStore);
            if (containStakeMetaChanges) {
                // Pending rewards are calculated midnight each day for every account.
                // If this account has changed to a different stakeId or choose to decline reward
                // in mid of the day, it will not receive rewards for that day.
                // So, it will be leaving some rewards from its current node unclaimed.
                // We need to record that, so we don't include them in the pendingRewards
                // calculation later
                final var effectiveStakeRewardStart = rewardableStakeStartFor(
                        stakingRewardStore.isStakingRewardsActivated(), originalAccount, consensusNow);
                stakeInfoHelper.increaseUnclaimedStakeRewards(
                        currentStakedNodeId, effectiveStakeRewardStart, stakingInfoStore);
            }
        }
        // If account chose to stake to a node, the new node's stake will be increased
        // by the account's stake amount
        if (scenario.awardsToNode() && !modifiedAccount.deleted()) {
            final var modifiedStakedNodeId = modifiedAccount.stakedNodeId();
            // We need the latest updates to balance and stakedToMe for the account in modifications also
            // to be reflected in stake awarded. So use the modifiedAccount instead of originalAccount
            stakeInfoHelper.awardStake(modifiedStakedNodeId, modifiedAccount, stakingInfoStore);
        }
    }

    public boolean shouldUpdateStakeStart(
            @Nullable final Account account,
            final boolean isRewarded,
            final long reward,
            @NonNull final ReadableNetworkStakingRewardsStore stakingRewardStore,
            @NonNull final Instant consensusNow) {
        if (account == null || account.hasStakedAccountId() || account.declineReward()) {
            // If the account is created in this transaction, or it is not staking to a node,
            // or it has chosen to decline reward, we don't need to remember stakeStart,
            // because it can't receive reward today
            return false;
        }
        if (!isRewarded) {
            // If the account is not rewarded in current transaction, it mean stake of node will not be changed
            return false;
        } else if (reward > 0) {
            // Alice earned a reward without changing her stake metadata, so she is still eligible
            // for a reward today; since her total stake will change this txn, we remember its
            // current value to reward her correctly for today no matter what happens later on
            return true;
        } else {
            // At this point, Alice is an account staking to a node, accepting rewards, and in
            // a reward situation---who nonetheless received zero rewards. There are essentially
            // four scenarios:
            //   1. Alice's stakePeriodStart is before the first non-rewardable period, but
            //   she was either staking zero whole hbars during those periods (or the reward rate
            //   was zero).
            //   2. Alice's stakePeriodStart is the first non-rewardable period because she
            //   was already rewarded earlier today.
            //   3. Alice's stakePeriodStart is the first non-rewardable period, but she was not
            //   rewarded today.
            //   4. Alice's stakePeriodStart is the current period.
            // We need to record her current stake as totalStakeAtStartOfLastRewardedPeriod in
            // scenarios 1 and 3, but not 2 and 4. (As noted below, in scenario 2 we want to
            // preserve an already-recorded memory of her stake at the beginning of this period.
            // In scenario 4 there is no point in recording anything---her stake will go unused.)
            if (earnedZeroRewardsBecauseOfZeroStake(account, stakingRewardStore, consensusNow)) {
                return true;
            }
            if (account.stakeAtStartOfLastRewardedPeriod() != NOT_REWARDED_SINCE_LAST_STAKING_META_CHANGE) {
                // Alice was in a reward situation, but did not earn anything because she already
                // received a reward earlier today; we preserve our memory of her stake from then
                return false;
            }
            // Alice was in a reward situation for the first time today, but received nothing---
            // either because she is staking < 1 hbar, or because she started staking only
            // today or yesterday; we don't care about the exact reason, we just remember
            // her total stake as long as she didn't begin staking today exactly
            return account.stakePeriodStart() < stakePeriodManager.currentStakePeriod(consensusNow);
        }
    }

    private long rewardableStakeStartFor(
            final boolean rewardsActivated, @NonNull final Account account, @NonNull final Instant consensusNow) {
        if (!rewardsActivated || account.declineReward()) {
            return 0;
        }
        final var startPeriod = account.stakePeriodStart();
        final var currentPeriod = stakePeriodManager.currentStakePeriod(consensusNow);
        if (startPeriod >= currentPeriod) {
            return 0;
        } else {
            if (isRewardedSinceLastStakeMetaChange(account) && (startPeriod == currentPeriod - 1)) {
                // Special case---this account was already rewarded today, so its current stake
                // has almost certainly changed from what it was at the start of the day
                return account.stakeAtStartOfLastRewardedPeriod();
            } else {
                return roundedToHbar(totalStake(account));
            }
        }
    }

    private void decreaseStakeRewardAccountBalance(
            final Map<AccountID, Long> rewardsPaid,
            final AccountID stakingRewardAccountId,
            final WritableAccountStore writableAccountStore) {
        if (!rewardsPaid.isEmpty()) {
            long totalPaidRewards = 0L;
            for (final var value : rewardsPaid.values()) {
                totalPaidRewards += value;
            }

            final var stakingRewardAccount = writableAccountStore.get(stakingRewardAccountId);
            final var finalBalance = stakingRewardAccount.tinybarBalance() - totalPaidRewards;
            if (finalBalance < 0) {
                log.warn("Staking reward account balance is negative after reward distribution, set it to 0");
            }
            // At this place it is not possible for the staking reward account balance to be less than total rewards
            // paid.
            // Because EndOfStakingPeriodCalculator, sets the reward rate based on 0.0.800 balance.
            final var copy = stakingRewardAccount.copyBuilder();
            copy.tinybarBalance(finalBalance);
            writableAccountStore.put(copy.build());
        }
    }

    private void updateStakedToMeFor(
            @NonNull final AccountID stakee,
            final long roundedFinalBalance,
            @NonNull final WritableAccountStore writableStore) {
        final var account = writableStore.get(stakee);
        final var initialStakedToMe = account.stakedToMe();
        final var finalStakedToMe = initialStakedToMe + roundedFinalBalance;
        if (finalStakedToMe < 0) {
            log.warn("StakedToMe for account {} is negative after reward distribution, set it to 0", stakee);
        }
        final var copy = account.copyBuilder()
                .stakedToMe(finalStakedToMe < 0 ? 0 : finalStakedToMe)
                .build();
        writableStore.put(copy);
    }
}
