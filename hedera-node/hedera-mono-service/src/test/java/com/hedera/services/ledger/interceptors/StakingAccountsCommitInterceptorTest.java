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

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.ZONE_UTC;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.NA;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.validation.AccountUsageTracking;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakingAccountsCommitInterceptorTest {
    @Mock private AccountUsageTracking usageTracking;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private BootstrapProperties bootstrapProperties;
    @Mock private RewardCalculator rewardCalculator;
    @Mock private StakeChangeManager stakeChangeManager;
    @Mock private AddressBook addressBook;
    @Mock private Address address1 = mock(Address.class);
    @Mock private Address address2 = mock(Address.class);
    @Mock private StakePeriodManager stakePeriodManager;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private MerkleAccount merkleAccount;
    @Mock private AccountNumbers accountNumbers;
    @Mock private TransactionContext txnCtx;

    private StakeInfoManager stakeInfoManager;

    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
    private StakingAccountsCommitInterceptor subject;

    private static final long stakePeriodStart =
            LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L), ZONE_UTC).toEpochDay() - 1;

    private final EntityNum node0Id = EntityNum.fromLong(0L);
    private final EntityNum node1Id = EntityNum.fromLong(1L);
    private final long stakingRewardAccountNum = 800L;

    @BeforeEach
    void setUp() throws NegativeAccountBalanceException {
        stakingInfo = buildsStakingInfoMap();
        stakeInfoManager = new StakeInfoManager(() -> stakingInfo);
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
                        usageTracking);
        reset();
    }

    @Test
    void stakingInfraIsUnusedIfNotEnabled() {
        given(dynamicProperties.isStakingEnabled()).willReturn(false);

        subject.preview(new EntityChangeSet<>());

        verifyNoInteractions(networkCtx);
        verifyNoInteractions(rewardCalculator);
    }

    @Test
    void setsNothingIfUpdateIsSentinel() {
        subject.getStakedToMeUpdates()[2] = NA;
        subject.getStakePeriodStartUpdates()[2] = NA;
        subject.getStakeAtStartOfLastRewardedPeriodUpdates()[2] = NA;
        final var mockAccount = mock(MerkleAccount.class);

        subject.finish(2, mockAccount);

        verify(mockAccount, never()).setStakedToMe(subject.getStakedToMeUpdates()[2]);
        verify(mockAccount, never()).setStakePeriodStart(subject.getStakePeriodStartUpdates()[2]);
        verify(mockAccount, never())
                .setStakeAtStartOfLastRewardedPeriod(
                        subject.getStakeAtStartOfLastRewardedPeriodUpdates()[2]);
    }

    @Test
    void setsGivenNonSentinelUpdate() {
        subject.getStakedToMeUpdates()[2] = 100 * HBARS_TO_TINYBARS;
        subject.getStakePeriodStartUpdates()[2] = 123;
        final var mockAccount = mock(MerkleAccount.class);

        subject.finish(2, mockAccount);

        verify(mockAccount).setStakedToMe(100 * HBARS_TO_TINYBARS);
        verify(mockAccount).setStakePeriodStart(123);
    }

    @Test
    void changingKeyOnlyIsNotRewardSituation() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final Map<AccountProperty, Object> keyOnlyChanges = Map.of(AccountProperty.KEY, EMPTY_KEY);
        changes.include(counterpartyId, counterparty, keyOnlyChanges);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        given(networkCtx.areRewardsActivated()).willReturn(true);

        subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0] = NA;

        subject.preview(changes);

        assertEquals(NA, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
        verify(rewardCalculator).reset();
    }

    @Test
    void
            anAccountThatStartedStakingBeforeCurrentPeriodAndHasntBeenRewardedUnclaimsStakeWhenChangingElection() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final var node0Info = stakingInfo.get(node0Id);
        node0Info.setStakeRewardStart(2 * counterpartyBalance);

        final Map<AccountProperty, Object> nodeChange = Map.of(AccountProperty.STAKED_ID, -2L);
        changes.include(counterpartyId, counterparty, nodeChange);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(-1);

        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(stakePeriodManager.currentStakePeriod()).willReturn(stakePeriodStart + 1);

        subject.preview(changes);

        assertEquals(counterpartyBalance, node0Info.getUnclaimedStakeRewardStart());
        verify(rewardCalculator).reset();
    }

    @Test
    void
            anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedDaysAgoUnclaimsStakeWhenChangingElection() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final var node0Info = stakingInfo.get(node0Id);
        node0Info.setStakeRewardStart(2 * counterpartyBalance);

        final Map<AccountProperty, Object> nodeChange = Map.of(AccountProperty.STAKED_ID, -2L);
        changes.include(counterpartyId, counterparty, nodeChange);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(counterpartyBalance / 5);

        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(stakePeriodManager.currentStakePeriod()).willReturn(stakePeriodStart + 3);

        subject.preview(changes);

        assertEquals(counterpartyBalance, node0Info.getUnclaimedStakeRewardStart());
        verify(rewardCalculator).reset();
    }

    @Test
    void
            anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedTodayUnclaimsStakeStartWhenChangingElection() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final var node0Info = stakingInfo.get(node0Id);
        node0Info.setStakeRewardStart(2 * counterpartyBalance);

        final Map<AccountProperty, Object> nodeChange = Map.of(AccountProperty.STAKED_ID, -2L);
        changes.include(counterpartyId, counterparty, nodeChange);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(counterpartyBalance / 5);

        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(stakePeriodManager.currentStakePeriod()).willReturn(stakePeriodStart + 1);

        subject.preview(changes);

        assertEquals(counterpartyBalance / 5, node0Info.getUnclaimedStakeRewardStart());
        verify(rewardCalculator).reset();
    }

    @Test
    void anAccountDoesNotUnclaimRewardsIfStakingNotActivated() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final var node0Info = stakingInfo.get(node0Id);
        node0Info.setStakeRewardStart(2 * counterpartyBalance);

        final Map<AccountProperty, Object> nodeChange = Map.of(AccountProperty.STAKED_ID, -2L);
        changes.include(counterpartyId, counterparty, nodeChange);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(counterpartyBalance / 5);

        subject.preview(changes);

        assertEquals(0, node0Info.getUnclaimedStakeRewardStart());
        verify(rewardCalculator).reset();
    }

    @Test
    void anAccountThatStartedStakingAtCurrentPeriodDoesntUnclaimStakeWhenChangingElection() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final var node0Info = stakingInfo.get(node0Id);
        node0Info.setStakeRewardStart(2 * counterpartyBalance);

        final Map<AccountProperty, Object> nodeChange = Map.of(AccountProperty.STAKED_ID, -2L);
        changes.include(counterpartyId, counterparty, nodeChange);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(-1);

        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(stakePeriodManager.currentStakePeriod()).willReturn(stakePeriodStart);

        subject.preview(changes);

        assertEquals(0, node0Info.getUnclaimedStakeRewardStart());
        verify(rewardCalculator).reset();
    }

    @Test
    void anAccountThatDeclineRewardsDoesntUnclaimStakeWhenChangingElection() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final var node0Info = stakingInfo.get(node0Id);
        node0Info.setStakeRewardStart(2 * counterpartyBalance);

        final Map<AccountProperty, Object> nodeChange = Map.of(AccountProperty.STAKED_ID, -2L);
        changes.include(counterpartyId, counterparty, nodeChange);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(-1);
        counterparty.setDeclineReward(true);

        given(networkCtx.areRewardsActivated()).willReturn(true);

        subject.preview(changes);

        assertEquals(0, node0Info.getUnclaimedStakeRewardStart());
        verify(rewardCalculator).reset();
    }

    @Test
    void aNewAccountShouldNotHaveStakeStartUpdated() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final Map<AccountProperty, Object> keyOnlyChanges =
                Map.of(BALANCE, 2 * counterpartyBalance);
        changes.include(counterpartyId, null, keyOnlyChanges);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(counterpartyBalance - 1);

        given(networkCtx.areRewardsActivated()).willReturn(true);

        subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0] = NA;

        subject.preview(changes);

        assertEquals(NA, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    }

    @Test
    void anAccountWithAlreadyCollectedRewardShouldNotHaveStakeStartUpdated() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        final Map<AccountProperty, Object> keyOnlyChanges =
                Map.of(BALANCE, 2 * counterpartyBalance);
        changes.include(counterpartyId, counterparty, keyOnlyChanges);
        counterparty.setStakePeriodStart(stakePeriodStart);
        counterparty.setStakeAtStartOfLastRewardedPeriod(counterpartyBalance - 1);

        given(networkCtx.areRewardsActivated()).willReturn(true);

        subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0] = NA;

        subject.preview(changes);

        assertEquals(NA, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    }

    @Test
    void calculatesRewardIfNeeded() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = buildChanges();
        final var rewardPayment = 1L;
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(rewardCalculator.computePendingReward(counterparty)).willReturn(rewardPayment);
        given(rewardCalculator.applyReward(rewardPayment, counterparty, changes.changes(1)))
                .willReturn(true);

        subject.preview(changes);

        verify(rewardCalculator).applyReward(rewardPayment, counterparty, changes.changes(1));
        verify(sideEffectsTracker)
                .trackRewardPayment(counterpartyId.getAccountNum(), rewardPayment);

        verify(stakeChangeManager)
                .awardStake(1, (long) changes.changes(0).get(AccountProperty.BALANCE), false);

        verify(stakeChangeManager).withdrawStake(0, changes.entity(1).getBalance(), false);

        verify(stakeChangeManager)
                .awardStake(1, (long) changes.changes(1).get(AccountProperty.BALANCE), false);

        assertFalse(subject.hasBeenRewarded(0));
        assertTrue(subject.hasBeenRewarded(1));
        // both have stakeMeta changes
        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[1]);
    }

    @Test
    void checksIfRewardsToBeActivatedEveryHandle() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
        changes.include(
                counterpartyId,
                counterparty,
                randomStakedNodeChanges(counterpartyBalance - amount - 100L));

        stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] {5, 5}));
        subject.setRewardsActivated(true);
        given(dynamicProperties.getStakingStartThreshold()).willReturn(100L);

        // rewards are activated,so can't activate again
        subject.preview(changes);
        verify(networkCtx, never()).setStakingRewardsActivated(true);
        verify(stakeChangeManager, never()).initializeAllStakingStartsTo(anyLong());

        // rewards are not activated, threshold is less but balance for 0.0.800 is not increased
        subject.setRewardsActivated(false);
        given(dynamicProperties.getStakingStartThreshold()).willReturn(20L);
        given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccountNum);

        subject.preview(changes);

        verify(networkCtx, never()).setStakingRewardsActivated(true);
        assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
        assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
        assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[1]);
        assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[1]);

        // rewards are not activated, and balance increased
        changes.include(stakingFundId, stakingFund, Map.of(AccountProperty.BALANCE, 100L));

        subject.preview(changes);

        verify(networkCtx).setStakingRewardsActivated(true);
        verify(stakeChangeManager).initializeAllStakingStartsTo(anyLong());
        assertEquals(0L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
        assertEquals(0L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
        assertEquals(0L, stakingInfo.get(node0Id).getRewardSumHistory()[1]);
        assertEquals(0L, stakingInfo.get(node1Id).getRewardSumHistory()[1]);
        verify(rewardCalculator, times(3)).reset();
    }

    @Test
    void checksIfRewardableIfChangesHaveStakingFields() {
        counterparty.setStakePeriodStart(-1);
        counterparty.setStakedId(-1);
        final var changes = randomStakedNodeChanges(100L);

        subject.setRewardsActivated(true);

        // has changes to StakeMeta,
        assertTrue(subject.isRewardSituation(counterparty, -1, changes));

        // valid fields, plus a ledger-managed staking field change
        counterparty.setStakePeriodStart(stakePeriodStart - 2);
        assertTrue(subject.isRewardSituation(counterparty, -1, changes));

        // valid fields, plus a stakedToMe updated
        counterparty.setStakePeriodStart(stakePeriodStart - 2);
        assertTrue(subject.isRewardSituation(counterparty, 1_234_567, Collections.emptyMap()));

        // rewards not activated
        subject.setRewardsActivated(false);
        assertFalse(subject.isRewardSituation(counterparty, -1, changes));

        // declined reward on account, but changes have it as false
        counterparty.setDeclineReward(true);
        subject.setRewardsActivated(true);
        assertTrue(subject.isRewardSituation(counterparty, -1, changes));

        // staked to account
        counterparty.setStakedId(2L);
        assertFalse(subject.isRewardSituation(counterparty, -1, changes));
    }

    @Test
    void activatesStakingRewardsAndClearsRewardSumHistoryAsExpected() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final long randomFee = 3L;
        final long rewardsPaid = 1L;
        final var inorder = inOrder(sideEffectsTracker);
        counterparty.setStakePeriodStart(-1L);
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();

        changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
        changes.include(
                counterpartyId,
                counterparty,
                randomStakedNodeChanges(counterpartyBalance - amount - randomFee));
        changes.include(stakingFundId, stakingFund, onlyBalanceChanges(randomFee));

        willCallRealMethod().given(networkCtx).areRewardsActivated();
        willCallRealMethod().given(networkCtx).setStakingRewardsActivated(true);
        given(rewardCalculator.rewardsPaidInThisTxn()).willReturn(rewardsPaid);
        given(dynamicProperties.getStakingStartThreshold()).willReturn(rewardsPaid);

        stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] {5, 5}));
        given(stakePeriodManager.currentStakePeriod()).willReturn(19132L);
        given(stakeChangeManager.findOrAdd(eq(800L), any())).willReturn(2);
        given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccountNum);

        // rewardsSumHistory is not cleared
        assertEquals(5, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
        assertEquals(5, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
        assertEquals(-1, counterparty.getStakePeriodStart());
        assertEquals(-1, party.getStakePeriodStart());

        subject.preview(changes);

        inorder.verify(sideEffectsTracker).trackHbarChange(partyId.getAccountNum(), +amount);
        inorder.verify(sideEffectsTracker)
                .trackHbarChange(counterpartyId.getAccountNum(), -amount - randomFee);
        inorder.verify(sideEffectsTracker)
                .trackHbarChange(
                        stakingFundId.getAccountNum(),
                        randomFee - stakingFund.getBalance() - rewardsPaid);
        verify(networkCtx).setStakingRewardsActivated(true);
        verify(stakeChangeManager).initializeAllStakingStartsTo(19132L);

        // rewardsSumHistory is cleared
        assertEquals(0, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
        assertEquals(0, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
        assertEquals(-1, party.getStakePeriodStart());
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToNode() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var inorderST = inOrder(sideEffectsTracker);
        final var inorderM = inOrder(stakeChangeManager);

        final var pendingChanges = buildPendingNodeStakeChanges();
        final Map<AccountProperty, Object> stakingFundChanges =
                Map.of(AccountProperty.BALANCE, 100L);

        given(rewardCalculator.computePendingReward(any())).willReturn(10L);
        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(rewardCalculator.applyReward(10L, counterparty, pendingChanges.changes(0)))
                .willReturn(true);
        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
        stakingFund.setStakePeriodStart(-1);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        subject.preview(pendingChanges);

        inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, 10L);

        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
        inorderM.verify(stakeChangeManager).awardStake(1L, 0, false);
        // StakingMeta changes
        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChanges() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var inorderST = inOrder(sideEffectsTracker);
        final var inorderM = inOrder(stakeChangeManager);

        final var pendingChanges = changesWithNoStakingMetaUpdates();
        final Map<AccountProperty, Object> stakingFundChanges =
                Map.of(AccountProperty.BALANCE, 100L);

        given(rewardCalculator.computePendingReward(any())).willReturn(10L);
        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(rewardCalculator.applyReward(10L, counterparty, pendingChanges.changes(0)))
                .willReturn(true);
        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
        stakingFund.setStakePeriodStart(-1);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        subject.preview(pendingChanges);

        inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, 10L);

        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
        inorderM.verify(stakeChangeManager).awardStake(0L, 0, false);
        // StakingMeta changes
        assertEquals(
                counterpartyBalance + counterparty.getStakedToMe(),
                subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChangesAndNoReward() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var inorderST = inOrder(sideEffectsTracker);
        final var inorderM = inOrder(stakeChangeManager);

        final var pendingChanges = changesWithNoStakingMetaUpdates();
        final Map<AccountProperty, Object> stakingFundChanges =
                Map.of(AccountProperty.BALANCE, 100L);
        final var stakePeriodStart = 12345678L;
        counterparty.setStakePeriodStart(stakePeriodStart);

        given(rewardCalculator.computePendingReward(any())).willReturn(0L);
        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(stakePeriodManager.currentStakePeriod()).willReturn(stakePeriodStart + 1);
        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
        stakingFund.setStakePeriodStart(-1);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        subject.preview(pendingChanges);

        inorderST.verify(sideEffectsTracker, never()).trackRewardPayment(anyLong(), anyLong());

        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
        inorderM.verify(stakeChangeManager).awardStake(0L, 0, false);
        // StakingMeta changes
        assertEquals(
                counterpartyBalance + counterparty.getStakedToMe(),
                subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToAccount() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var inorderST = inOrder(sideEffectsTracker);
        final var inorderM = inOrder(stakeChangeManager);
        final var rewardPayment = HBARS_TO_TINYBARS + 100;

        final var pendingChanges = buildPendingAccountStakeChanges();
        final Map<AccountProperty, Object> stakingFundChanges =
                Map.of(AccountProperty.BALANCE, 100L);

        given(stakePeriodManager.startUpdateFor(-1L, 2L, true, true)).willReturn(13L);
        given(stakePeriodManager.startUpdateFor(0L, 0L, false, false)).willReturn(666L);
        given(rewardCalculator.applyReward(anyLong(), any(), any())).willReturn(true);

        given(rewardCalculator.computePendingReward(any())).willReturn(rewardPayment);
        given(networkCtx.areRewardsActivated()).willReturn(true);
        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
        stakingFund.setStakePeriodStart(-1);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        subject.preview(pendingChanges);

        inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, rewardPayment);

        inorderM.verify(stakeChangeManager).findOrAdd(anyLong(), any());
        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
        inorderM.verify(stakeChangeManager, never()).awardStake(2L, 0L, false);

        final var updatedStakePeriodStarts = subject.getStakePeriodStartUpdates();
        assertEquals(13L, updatedStakePeriodStarts[0]);
        assertEquals(666L, updatedStakePeriodStarts[1]);
    }

    @Test
    void rewardsUltimateBeneficiaryInsteadOfDeletedAccount() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var tbdReward = 1_234L;
        counterparty.setStakePeriodStart(stakePeriodStart - 2);
        beneficiary.setStakePeriodStart(stakePeriodStart - 2);

        final var pendingChanges = buildPendingNodeStakeChanges();
        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
        pendingChanges.changes(0).put(BALANCE, 0L);

        final Map<AccountProperty, Object> firstBeneficiaryChanges =
                new EnumMap<>(AccountProperty.class);
        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
        pendingChanges.include(partyId, party, firstBeneficiaryChanges);

        final Map<AccountProperty, Object> secondBeneficiaryChanges =
                new EnumMap<>(AccountProperty.class);
        secondBeneficiaryChanges.put(
                AccountProperty.BALANCE, partyBalance + counterpartyBalance + beneficiaryBalance);
        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);

        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum()))
                .willReturn(partyId.getAccountNum());
        given(txnCtx.getBeneficiaryOfDeleted(partyId.getAccountNum()))
                .willReturn(beneficiaryId.getAccountNum());
        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(2);
        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(rewardCalculator.computePendingReward(counterparty)).willReturn(tbdReward);
        given(rewardCalculator.computePendingReward(beneficiary)).willReturn(tbdReward);
        given(rewardCalculator.applyReward(tbdReward, beneficiary, pendingChanges.changes(2)))
                .willReturn(true);

        subject =
                new StakingAccountsCommitInterceptor(
                        sideEffectsTracker,
                        () -> networkCtx,
                        dynamicProperties,
                        rewardCalculator,
                        new StakeChangeManager(
                                stakeInfoManager,
                                () -> AccountStorageAdapter.fromInMemory(accounts)),
                        stakePeriodManager,
                        stakeInfoManager,
                        accountNumbers,
                        txnCtx,
                        usageTracking);

        subject.preview(pendingChanges);
        verify(rewardCalculator, times(2))
                .applyReward(tbdReward, beneficiary, pendingChanges.changes(2));
        verify(sideEffectsTracker, times(2))
                .trackRewardPayment(beneficiaryId.getAccountNum(), tbdReward);
    }

    @Test
    void doesntTrackAnythingIfRedirectBeneficiaryDeclinedReward() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        final var tbdReward = 1_234L;
        counterparty.setStakePeriodStart(stakePeriodStart - 2);
        beneficiary.setStakePeriodStart(stakePeriodStart - 2);

        final var pendingChanges = buildPendingNodeStakeChanges();
        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
        pendingChanges.changes(0).put(BALANCE, 0L);

        final Map<AccountProperty, Object> firstBeneficiaryChanges =
                new EnumMap<>(AccountProperty.class);
        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
        pendingChanges.include(partyId, party, firstBeneficiaryChanges);

        final Map<AccountProperty, Object> secondBeneficiaryChanges =
                new EnumMap<>(AccountProperty.class);
        secondBeneficiaryChanges.put(
                AccountProperty.BALANCE, partyBalance + counterpartyBalance + beneficiaryBalance);
        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);

        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum()))
                .willReturn(partyId.getAccountNum());
        given(txnCtx.getBeneficiaryOfDeleted(partyId.getAccountNum()))
                .willReturn(beneficiaryId.getAccountNum());
        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(2);
        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(rewardCalculator.computePendingReward(counterparty)).willReturn(tbdReward);
        given(rewardCalculator.computePendingReward(beneficiary)).willReturn(tbdReward);
        given(rewardCalculator.applyReward(tbdReward, beneficiary, pendingChanges.changes(2)))
                .willReturn(false)
                .willReturn(true);

        subject =
                new StakingAccountsCommitInterceptor(
                        sideEffectsTracker,
                        () -> networkCtx,
                        dynamicProperties,
                        rewardCalculator,
                        new StakeChangeManager(
                                stakeInfoManager,
                                () -> AccountStorageAdapter.fromInMemory(accounts)),
                        stakePeriodManager,
                        stakeInfoManager,
                        accountNumbers,
                        txnCtx,
                        usageTracking);

        subject.preview(pendingChanges);
        verify(rewardCalculator, times(2))
                .applyReward(tbdReward, beneficiary, pendingChanges.changes(2));
        verify(sideEffectsTracker, times(1))
                .trackRewardPayment(beneficiaryId.getAccountNum(), tbdReward);
    }

    @Test
    void failsHardIfMoreRedirectsThanDeletedEntitiesAreNeeded() {
        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);
        beneficiary.setStakePeriodStart(stakePeriodStart - 2);

        final var pendingChanges = buildPendingNodeStakeChanges();
        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
        pendingChanges.changes(0).put(BALANCE, 0L);

        final Map<AccountProperty, Object> firstBeneficiaryChanges =
                new EnumMap<>(AccountProperty.class);
        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
        pendingChanges.include(partyId, party, firstBeneficiaryChanges);

        final Map<AccountProperty, Object> secondBeneficiaryChanges =
                new EnumMap<>(AccountProperty.class);
        secondBeneficiaryChanges.put(
                AccountProperty.BALANCE, partyBalance + counterpartyBalance + beneficiaryBalance);
        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);

        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum()))
                .willReturn(partyId.getAccountNum());
        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(1);
        given(networkCtx.areRewardsActivated()).willReturn(true);
        given(rewardCalculator.computePendingReward(any())).willReturn(123L);

        subject =
                new StakingAccountsCommitInterceptor(
                        sideEffectsTracker,
                        () -> networkCtx,
                        dynamicProperties,
                        rewardCalculator,
                        new StakeChangeManager(
                                stakeInfoManager,
                                () -> AccountStorageAdapter.fromInMemory(accounts)),
                        stakePeriodManager,
                        stakeInfoManager,
                        accountNumbers,
                        txnCtx,
                        usageTracking);

        assertThrows(IllegalStateException.class, () -> subject.preview(pendingChanges));
    }

    @Test
    void updatesStakedToMeSideEffects() {
        counterparty.setStakedId(1L);
        stakingFund.setStakePeriodStart(-1);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        final Map<AccountProperty, Object> stakingFundChanges =
                Map.of(AccountProperty.BALANCE, 100L);
        final var pendingChanges = buildPendingAccountStakeChanges();
        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
        given(accounts.get(EntityNum.fromLong(1L))).willReturn(merkleAccount);
        given(merkleAccount.getStakedToMe()).willReturn(0L);

        given(accounts.get(EntityNum.fromLong(2L))).willReturn(merkleAccount);
        given(merkleAccount.getStakedToMe()).willReturn(0L);

        subject =
                new StakingAccountsCommitInterceptor(
                        sideEffectsTracker,
                        () -> networkCtx,
                        dynamicProperties,
                        rewardCalculator,
                        new StakeChangeManager(
                                stakeInfoManager,
                                () -> AccountStorageAdapter.fromInMemory(accounts)),
                        stakePeriodManager,
                        stakeInfoManager,
                        accountNumbers,
                        txnCtx,
                        usageTracking);

        subject.getRewardsEarned()[1] = 0;
        subject.getRewardsEarned()[2] = 1;
        assertEquals(2, pendingChanges.size());

        subject.setCurStakedId(1L);
        subject.setNewStakedId(2L);
        Arrays.fill(subject.getStakedToMeUpdates(), NA);
        subject.updateStakedToMeSideEffects(
                counterparty,
                StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT,
                pendingChanges.changes(0),
                pendingChanges);
        assertEquals(-555L * HBARS_TO_TINYBARS, subject.getStakedToMeUpdates()[2]);
        assertEquals(100L * HBARS_TO_TINYBARS, subject.getStakedToMeUpdates()[3]);
    }

    @Test
    void updatesStakedToMeSideEffectsPaysRewardsIfRewardable() {
        counterparty.setStakedId(123L);
        stakingFund.setStakePeriodStart(-1);
        counterparty.setStakePeriodStart(stakePeriodStart - 2);

        final var stakingFundChanges = new HashMap<AccountProperty, Object>();
        stakingFundChanges.put(AccountProperty.BALANCE, 100L);

        final var map = new HashMap<AccountProperty, Object>();
        map.put(AccountProperty.BALANCE, 100L);
        map.put(AccountProperty.STAKED_ID, 123L);
        map.put(AccountProperty.DECLINE_REWARD, false);

        var pendingChanges = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        pendingChanges.include(partyId, party, stakingFundChanges);
        pendingChanges.include(stakingFundId, stakingFund, new HashMap<>(stakingFundChanges));
        pendingChanges.include(counterpartyId, counterparty, map);

        subject =
                new StakingAccountsCommitInterceptor(
                        sideEffectsTracker,
                        () -> networkCtx,
                        dynamicProperties,
                        rewardCalculator,
                        new StakeChangeManager(
                                stakeInfoManager,
                                () -> AccountStorageAdapter.fromInMemory(accounts)),
                        stakePeriodManager,
                        stakeInfoManager,
                        accountNumbers,
                        txnCtx,
                        usageTracking);

        subject.getRewardsEarned()[0] = -1;
        subject.getRewardsEarned()[1] = -1;
        subject.setCurStakedId(partyId.getAccountNum());
        subject.setNewStakedId(partyId.getAccountNum());
        assertEquals(3, pendingChanges.size());
        final var stakedToMeUpdates = subject.getStakedToMeUpdates();
        stakedToMeUpdates[0] = counterpartyBalance + 2 * HBARS_TO_TINYBARS;
        stakedToMeUpdates[1] = counterpartyBalance + 2 * HBARS_TO_TINYBARS;
        stakedToMeUpdates[2] = -1L;
        subject.updateStakedToMeSideEffects(
                counterparty,
                StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT,
                pendingChanges.changes(0),
                pendingChanges);

        assertEquals(2 * HBARS_TO_TINYBARS, stakedToMeUpdates[0]);
        assertEquals(counterpartyBalance + 2 * HBARS_TO_TINYBARS, stakedToMeUpdates[1]);
    }

    public EntityChangeSet<AccountID, HederaAccount, AccountProperty>
            changesWithNoStakingMetaUpdates() {
        final var changes = new HashMap<AccountProperty, Object>();
        changes.put(AccountProperty.BALANCE, 10L);
        var pendingChanges = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        pendingChanges.include(counterpartyId, counterparty, changes);
        return pendingChanges;
    }

    public EntityChangeSet<AccountID, HederaAccount, AccountProperty>
            buildPendingNodeStakeChanges() {
        var changes = randomStakedNodeChanges(0L);
        var pendingChanges = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        pendingChanges.include(counterpartyId, counterparty, changes);
        return pendingChanges;
    }

    public EntityChangeSet<AccountID, HederaAccount, AccountProperty>
            buildPendingAccountStakeChanges() {
        var changes = randomStakeAccountChanges(100L * HBARS_TO_TINYBARS);
        var pendingChanges = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        pendingChanges.include(counterpartyId, counterparty, changes);
        return pendingChanges;
    }

    public MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT))
                .willReturn(2_000_000_000L);
        given(bootstrapProperties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .willReturn(2);
        given(addressBook.getSize()).willReturn(2);
        given(addressBook.getAddress(0)).willReturn(address1);
        given(address1.getId()).willReturn(0L);
        given(addressBook.getAddress(1)).willReturn(address2);
        given(address2.getId()).willReturn(1L);

        final var info = buildStakingInfoMap(addressBook, bootstrapProperties);
        info.forEach(
                (a, b) -> {
                    b.setStakeToReward(300L);
                    b.setStake(1000L);
                    b.setStakeToNotReward(400L);
                });
        return info;
    }

    private Map<AccountProperty, Object> randomStakedNodeChanges(final long newBalance) {
        final var map = new HashMap<AccountProperty, Object>();
        map.put(AccountProperty.BALANCE, newBalance);
        map.put(AccountProperty.STAKED_ID, -2L);
        map.put(AccountProperty.DECLINE_REWARD, false);
        return map;
    }

    private Map<AccountProperty, Object> randomStakeAccountChanges(final long newBalance) {
        final var map = new HashMap<AccountProperty, Object>();
        map.put(AccountProperty.BALANCE, newBalance);
        map.put(AccountProperty.STAKED_ID, 2L);
        map.put(AccountProperty.DECLINE_REWARD, false);
        return map;
    }

    private EntityChangeSet<AccountID, HederaAccount, AccountProperty> buildChanges() {
        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
        changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
        changes.include(
                counterpartyId,
                counterparty,
                randomStakedNodeChanges(counterpartyBalance - amount));
        return changes;
    }

    public static Map<AccountProperty, Object> onlyBalanceChanges(final long balance) {
        final var map = new HashMap<AccountProperty, Object>();
        map.put(AccountProperty.BALANCE, balance);
        return map;
    }

    private void reset() throws NegativeAccountBalanceException {
        counterparty.setStakedId(-1);
        counterparty.setBalance(counterpartyBalance);
        counterparty.setStakedToMe(0L);
        counterparty.setDeclineReward(false);
    }

    private static final long amount = HBARS_TO_TINYBARS;
    private static final long partyBalance = 111 * HBARS_TO_TINYBARS;
    private static final long counterpartyBalance = 555L * HBARS_TO_TINYBARS;
    private static final long beneficiaryBalance = 666L * HBARS_TO_TINYBARS;
    private static final AccountID partyId = AccountID.newBuilder().setAccountNum(123).build();
    private static final AccountID counterpartyId =
            AccountID.newBuilder().setAccountNum(321).build();
    private static final AccountID beneficiaryId =
            AccountID.newBuilder().setAccountNum(456).build();
    private static final AccountID stakingFundId =
            AccountID.newBuilder().setAccountNum(800).build();
    private final MerkleAccount party =
            MerkleAccountFactory.newAccount()
                    .number(EntityNum.fromAccountId(partyId))
                    .balance(partyBalance)
                    .get();
    private final MerkleAccount counterparty =
            MerkleAccountFactory.newAccount()
                    .stakedId(-1)
                    .number(EntityNum.fromAccountId(counterpartyId))
                    .balance(counterpartyBalance)
                    .get();

    private final MerkleAccount beneficiary =
            MerkleAccountFactory.newAccount()
                    .stakedId(-1)
                    .number(EntityNum.fromAccountId(beneficiaryId))
                    .balance(beneficiaryBalance)
                    .get();
    private final MerkleAccount stakingFund =
            MerkleAccountFactory.newAccount()
                    .number(EntityNum.fromAccountId(stakingFundId))
                    .balance(amount)
                    .get();
}
