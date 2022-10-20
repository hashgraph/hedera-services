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
package com.hedera.services.ledger.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakeChangesInterceptorTest {
    @Mock private StakeChangeManager stakeChangeManager;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private RewardCalculator rewardCalculator;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private StakePeriodManager stakePeriodManager;
    @Mock private StakeInfoManager stakeInfoManager;
    @Mock private AccountNumbers accountNumbers;
    @Mock private TransactionContext txnCtx;
    @Mock private UsageLimits usageLimits;

    private EntityChangeSet<AccountID, HederaAccount, AccountProperty> changes;
    private StakingAccountsCommitInterceptor subject;

    @BeforeEach
    void setUp() {
        changes = new EntityChangeSet<>();
        subject =
                new StakingAccountsCommitInterceptor(
                        sideEffectsTracker,
                        () -> networkCtx,
                        dynamicProperties,
                        rewardCalculator,
                        stakeChangeManager,
                        stakePeriodManager,
                        stakeInfoManager,
                        accountNumbers,
                        txnCtx,
                        usageLimits);
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    }

    @Test
    void resizesAuxArraysIfNeeded() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        for (int i = 0; i < 256; i++) {
            changes.include(
                    AccountID.newBuilder().setAccountNum(i + 1).build(),
                    new MerkleAccount(),
                    new EnumMap<>(AccountProperty.class));
        }

        subject.preview(changes);

        assertEquals(3 * 256 + 1, subject.getRewardsEarned().length);
        assertEquals(3 * 256 + 1, subject.getStakeChangeScenarios().length);
    }

    @Test
    void onlyUpdatesNodeStakeFromAbsentToNode() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, unstakedWith(oneBalance), decliningRewardAfter(changingToNode(aNodeNum)));

        subject.preview(changes);

        verify(stakeChangeManager).awardStake(aNodeNum, oneBalance, true);
    }

    @Test
    void withdrawsAccountStakeAndAwardsNodeStakeFromAccountToNode() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(
                alice,
                accountStakedWith(oneBalance, bob),
                decliningRewardAfter(changingToNode(aNodeNum)));
        willAnswer(
                        invocationOnMock -> {
                            changes.include(
                                    bob, unstakedWith(twoBalance, oneBalance), newChanges());
                            return 1;
                        })
                .given(stakeChangeManager)
                .findOrAdd(bob.getAccountNum(), changes);

        subject.preview(changes);

        verify(stakeChangeManager).awardStake(aNodeNum, oneBalance, true);
        assertEquals(0L, subject.getStakedToMeUpdates()[1]);
    }

    @Test
    void withdrawsNodeStakeAndAwardsNodeStakeSwitchingNodes() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(
                alice,
                nodeStakedWith(oneBalance, aNodeNum),
                decliningRewardAfter(changingToNode(bNodeNum)));

        subject.preview(changes);

        verify(stakeChangeManager).withdrawStake(aNodeNum, oneBalance, false);
        verify(stakeChangeManager).awardStake(bNodeNum, oneBalance, true);
    }

    @Test
    void withdrawsAndReAwardsStakeWithNewDeclineRewardOnlyChange() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, nodeStakedWith(oneBalance, aNodeNum), decliningRewardAfter(newChanges()));

        subject.preview(changes);

        verify(stakeChangeManager).withdrawStake(aNodeNum, oneBalance, false);
        verify(stakeChangeManager).awardStake(aNodeNum, oneBalance, true);
    }

    @Test
    void awardsAccountStakeWhenComingFromAbsent() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, unstakedWith(oneBalance), changingToAccount(bob));
        willAnswer(
                        invocationOnMock -> {
                            changes.include(bob, unstakedWith(twoBalance, 0), newChanges());
                            return 1;
                        })
                .given(stakeChangeManager)
                .findOrAdd(bob.getAccountNum(), changes);

        subject.preview(changes);

        assertEquals(oneBalance, subject.getStakedToMeUpdates()[1]);
    }

    @Test
    void withdrawsFromNodeAndAwardsAccountStakeWhenSwitching() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, nodeStakedWith(oneBalance, aNodeNum, true), changingToAccount(bob));
        willAnswer(
                        invocationOnMock -> {
                            changes.include(bob, unstakedWith(twoBalance, 0), newChanges());
                            return 1;
                        })
                .given(stakeChangeManager)
                .findOrAdd(bob.getAccountNum(), changes);

        subject.preview(changes);

        verify(stakeChangeManager).withdrawStake(aNodeNum, oneBalance, true);
        assertEquals(oneBalance, subject.getStakedToMeUpdates()[1]);
    }

    @Test
    void deductsFromAccountAndAwardsToNewAccountWhenSwitching() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, accountStakedWith(oneBalance, bob), changingToAccount(carol));
        willAnswer(
                        invocationOnMock -> {
                            changes.include(
                                    bob, unstakedWith(twoBalance, oneBalance), newChanges());
                            return 1;
                        })
                .given(stakeChangeManager)
                .findOrAdd(bob.getAccountNum(), changes);
        willAnswer(
                        invocationOnMock -> {
                            changes.include(carol, unstakedWith(twoBalance, 0), newChanges());
                            return 2;
                        })
                .given(stakeChangeManager)
                .findOrAdd(carol.getAccountNum(), changes);

        subject.preview(changes);

        assertEquals(0L, subject.getStakedToMeUpdates()[1]);
        assertEquals(oneBalance, subject.getStakedToMeUpdates()[2]);
    }

    @Test
    void justAdjustsStakedToMeWhenBalanceChanges() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, accountStakedWith(oneBalance, bob), toNewBalance(twoBalance));
        willAnswer(
                        invocationOnMock -> {
                            changes.include(
                                    bob, unstakedWith(twoBalance, oneBalance), newChanges());
                            return 1;
                        })
                .given(stakeChangeManager)
                .findOrAdd(bob.getAccountNum(), changes);

        subject.preview(changes);

        assertEquals(twoBalance, subject.getStakedToMeUpdates()[1]);
    }

    @Test
    void noEffectOnStakedToMeWhenNoBalanceChanges() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, accountStakedWith(oneBalance, bob), toNewBalance(oneBalance));

        subject.preview(changes);

        assertEquals(1, changes.size());
    }

    @Test
    void deductsFromAccountWhenOptingOut() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, accountStakedWith(oneBalance, bob), noLongerStaking());
        willAnswer(
                        invocationOnMock -> {
                            changes.include(
                                    bob, unstakedWith(twoBalance, oneBalance), newChanges());
                            return 1;
                        })
                .given(stakeChangeManager)
                .findOrAdd(bob.getAccountNum(), changes);

        subject.preview(changes);

        assertEquals(0L, subject.getStakedToMeUpdates()[1]);
    }

    @Test
    void withdrawsNodeStakeWhenOptingOut() {
        given(accountNumbers.stakingRewardAccount()).willReturn(800L);
        addChange(alice, nodeStakedWith(oneBalance, aNodeNum), noLongerStaking());

        subject.preview(changes);

        verify(stakeChangeManager).withdrawStake(aNodeNum, oneBalance, false);
    }

    @Test
    void canAwardStakedToMeSideEffectAccount() {
        addChange(
                alice,
                accountStakedWith(oneBalance, bob),
                decliningRewardAfter(changingToNode(aNodeNum)));
        willAnswer(
                        invocationOnMock -> {
                            changes.include(
                                    bob, nodeStakedWith(twoBalance, aNodeNum), newChanges());
                            changes.entity(1).setStakePeriodStart(123L);
                            changes.entity(1).setEntityNum(EntityNum.fromAccountId(bob));
                            return 1;
                        })
                .given(stakeChangeManager)
                .findOrAdd(bob.getAccountNum(), changes);
        given(rewardCalculator.applyReward(anyLong(), any(), any())).willReturn(true);
        given(rewardCalculator.computePendingReward(any())).willReturn(123L);

        subject.setRewardsActivated(true);
        subject.preview(changes);

        verify(sideEffectsTracker).trackRewardPayment(bob.getAccountNum(), 123L);
    }

    private Map<AccountProperty, Object> newChanges() {
        return new EnumMap<>(AccountProperty.class);
    }

    private void addChange(
            final AccountID id,
            final MerkleAccount account,
            final Map<AccountProperty, Object> changeSet) {
        changes.include(id, account, changeSet);
    }

    private MerkleAccount unstakedWith(final long balance) {
        return MerkleAccountFactory.newAccount().balance(balance).get();
    }

    private MerkleAccount unstakedWith(final long balance, final long stakedToMe) {
        return MerkleAccountFactory.newAccount().balance(balance).stakedToMe(stakedToMe).get();
    }

    private MerkleAccount accountStakedWith(final long balance, final AccountID to) {
        return MerkleAccountFactory.newAccount()
                .stakedId(to.getAccountNum())
                .balance(balance)
                .get();
    }

    private MerkleAccount nodeStakedWith(final long balance, final long nodeNum) {
        return nodeStakedWith(balance, nodeNum, false);
    }

    private MerkleAccount nodeStakedWith(
            final long balance, final long nodeNum, final boolean declineReward) {
        return MerkleAccountFactory.newAccount()
                .declineReward(declineReward)
                .stakedId(-nodeNum - 1)
                .balance(balance)
                .get();
    }

    private Map<AccountProperty, Object> decliningRewardAfter(
            final Map<AccountProperty, Object> otherChanges) {
        otherChanges.put(AccountProperty.DECLINE_REWARD, true);
        return otherChanges;
    }

    private Map<AccountProperty, Object> changingToNode(final long nodeNum) {
        final var ans = new EnumMap<>(AccountProperty.class);
        ans.put(AccountProperty.STAKED_ID, -nodeNum - 1);
        return ans;
    }

    private Map<AccountProperty, Object> changingToAccount(final AccountID id) {
        final var ans = new EnumMap<>(AccountProperty.class);
        ans.put(AccountProperty.STAKED_ID, id.getAccountNum());
        return ans;
    }

    private Map<AccountProperty, Object> toNewBalance(final long newBalance) {
        final var ans = new EnumMap<>(AccountProperty.class);
        ans.put(AccountProperty.BALANCE, newBalance);
        return ans;
    }

    private Map<AccountProperty, Object> noLongerStaking() {
        final var ans = new EnumMap<>(AccountProperty.class);
        ans.put(AccountProperty.STAKED_ID, 0L);
        return ans;
    }

    private static final long aNodeNum = 1;
    private static final long bNodeNum = 2;
    private static final AccountID alice = AccountID.newBuilder().setAccountNum(1234).build();
    private static final AccountID bob = AccountID.newBuilder().setAccountNum(5678).build();
    private static final AccountID carol = AccountID.newBuilder().setAccountNum(9012).build();
    private static final long oneBalance = 100_000_000;
    private static final long twoBalance = 200_000_000;
}
