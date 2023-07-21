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

package com.hedera.node.app.service.token.impl.test.handlers.staking;

import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableStakingInfoStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.handlers.staking.RewardsHelper;
import com.hedera.node.app.service.token.impl.handlers.staking.RewardsPayer;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeInfoHelper;
import com.hedera.node.app.service.token.impl.handlers.staking.StakePeriodManager;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeRewardCalculatorImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandler;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandlerImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakingRewardsHandlerImplTest extends CryptoTokenHandlerTestBase {
    private static final Long NO_LAST_PERIOD_STAKE = null;
    private static final Long NO_BALANCE_DIFFERENCE = null;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    private StakingRewardsHandler subject;
    private StakePeriodManager stakePeriodManager;
    private final EntityNum node0Id = EntityNum.fromLong(0L);
    private final EntityNum node1Id = EntityNum.fromLong(1L);
    private final long stakingRewardAccountNum = 800L;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();

        given(configProvider.getConfiguration()).willReturn(versionedConfig);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        givenStoresAndConfig(handleContext);

        final var stakingRewardHelper = new RewardsHelper();
        stakePeriodManager = new StakePeriodManager(configProvider);
        final var stakeRewardCalculator = new StakeRewardCalculatorImpl(stakePeriodManager);
        final var rewardsPayer = new RewardsPayer(stakingRewardHelper, stakeRewardCalculator);
        final var stakeInfoHelper = new StakeInfoHelper();
        subject = new StakingRewardsHandlerImpl(rewardsPayer, stakingRewardHelper, stakePeriodManager, stakeInfoHelper);
    }

    @Test
    void changingKeyOnlyIsNotRewardSituation() {
        final var stakedToMeBefore = account.stakedToMe();
        final var stakePeriodStartBefore = account.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodBefore = account.stakeAtStartOfLastRewardedPeriod();

        noStakeChanges();

        final var rewards = subject.applyStakingRewards(handleContext);

        assertThat(rewards).isEmpty();
        final var modifiedAccount = writableAccountStore.get(payerId);
        final var stakedToMeAfter = modifiedAccount.stakedToMe();
        final var stakePeriodStartAfter = modifiedAccount.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodAfter = modifiedAccount.stakeAtStartOfLastRewardedPeriod();

        assertThat(stakedToMeAfter).isEqualTo(stakedToMeBefore);
        assertThat(stakePeriodStartAfter).isEqualTo(stakePeriodStartBefore);
        assertThat(stakeAtStartOfLastRewardedPeriodAfter).isEqualTo(stakeAtStartOfLastRewardedPeriodBefore);
    }

    @Test
    void rewardsWhenStakingFieldsModified() {
        final var stakedToMeBefore = account.stakedToMe();
        final var stakePeriodStartBefore = account.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodBefore = account.stakeAtStartOfLastRewardedPeriod();

        randomStakeNodeChanges();

        final var rewards = subject.applyStakingRewards(handleContext);

        // earned zero rewards due to zero stake
        assertThat(rewards).hasSize(1);
        assertThat(rewards).containsEntry(payerId, 0L);

        final var modifiedAccount = writableAccountStore.get(payerId);
        // stakedToMe will not change as this is not staked by another account
        final var stakedToMeAfter = modifiedAccount.stakedToMe();
        // These should change as staking is triggered
        final var stakePeriodStartAfter = modifiedAccount.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodAfter = modifiedAccount.stakeAtStartOfLastRewardedPeriod();

        final var expectedStakePeriodStart = stakePeriodManager.currentStakePeriod(consensusInstant);
        assertThat(stakedToMeAfter).isEqualTo(stakedToMeBefore);
        assertThat(stakePeriodStartAfter).isNotEqualTo(stakePeriodStartBefore);
        assertThat(stakePeriodStartAfter).isEqualTo(expectedStakePeriodStart);
        assertThat(stakeAtStartOfLastRewardedPeriodAfter).isEqualTo(stakeAtStartOfLastRewardedPeriodBefore);
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndHasntBeenRewardedUnclaimsStakeWhenChangingElection() {
        storesWithAccountProps(555L * HBARS_TO_TINYBARS, NO_LAST_PERIOD_STAKE);

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = originalInstant.plus(1, ChronoUnit.DAYS);

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(writableAccountStore.get(payerId).tinybarBalance()).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedDaysAgoUnclaimsStakeWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        storesWithAccountProps(newBalance, newBalance / 5);

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = originalInstant.plus(2, ChronoUnit.DAYS);

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(newBalance).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedTodayUnclaimsStakeStartWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        storesWithAccountProps(newBalance, newBalance / 5);

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = originalInstant.plus(1, ChronoUnit.DAYS);

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(newBalance / 5).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountDoesNotUnclaimRewardsIfStakingNotActivated() {
        stakingNotActivated();
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        storesWithAccountProps(newBalance, newBalance / 5);

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = originalInstant.plus(1, ChronoUnit.DAYS);

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(10).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatStartedStakingAtCurrentPeriodDoesntUnclaimStakeWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        storesWithAccountProps(newBalance, -1L);

        // Change node, so to trigger rewards
        writableAccountStore.put(
                writableAccountStore.get(payerId).copyBuilder().stakedNodeId(0L).build());

        given(handleContext.consensusNow()).willReturn(originalInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }
    //
    //    @Test
    //    void anAccountThatDeclineRewardsDoesntUnclaimStakeWhenChangingElection() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
    //        final var node0Info = stakingInfo.get(node0Id);
    //        node0Info.setStakeRewardStart(2 * counterpartyBalance);
    //
    //        final Map<AccountProperty, Object> nodeChange = Map.of(AccountProperty.STAKED_ID, -2L);
    //        changes.include(counterpartyId, counterparty, nodeChange);
    //        counterparty.setStakePeriodStart(stakePeriodStart);
    //        counterparty.setStakeAtStartOfLastRewardedPeriod(-1);
    //        counterparty.setDeclineReward(true);
    //
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //
    //        subject.preview(changes);
    //
    //        assertEquals(0, node0Info.getUnclaimedStakeRewardStart());
    //        verify(rewardCalculator).reset();
    //    }
    //
    //    @Test
    //    void aNewAccountShouldNotHaveStakeStartUpdated() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
    //        final Map<AccountProperty, Object> keyOnlyChanges = Map.of(BALANCE, 2 * counterpartyBalance);
    //        changes.include(counterpartyId, null, keyOnlyChanges);
    //        counterparty.setStakePeriodStart(stakePeriodStart);
    //        counterparty.setStakeAtStartOfLastRewardedPeriod(counterpartyBalance - 1);
    //
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //
    //        subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0] = NA;
    //
    //        subject.preview(changes);
    //
    //        assertEquals(NA, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //    }
    //
    //    @Test
    //    void earningZeroRewardsWithStartBeforeLastNonRewardableStillUpdatesSASOLARP() {
    //        final var account = mock(HederaAccount.class);
    //        given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(3L);
    //        given(account.getStakePeriodStart()).willReturn(2L);
    //
    //        assertTrue(subject.shouldRememberStakeStartFor(account, -1, 0));
    //    }
    //
    //    @Test
    //    void anAccountWithAlreadyCollectedRewardShouldNotHaveStakeStartUpdated() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
    //        final Map<AccountProperty, Object> keyOnlyChanges = Map.of(BALANCE, 2 * counterpartyBalance);
    //        changes.include(counterpartyId, counterparty, keyOnlyChanges);
    //        counterparty.setStakePeriodStart(stakePeriodStart);
    //        counterparty.setStakeAtStartOfLastRewardedPeriod(counterpartyBalance - 1);
    //
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //
    //        subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0] = NA;
    //
    //        subject.preview(changes);
    //
    //        assertEquals(NA, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //    }
    //
    //    @Test
    //    void calculatesRewardIfNeeded() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var changes = buildChanges();
    //        final var rewardPayment = 1L;
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(counterparty)).willReturn(rewardPayment);
    //        given(rewardCalculator.applyReward(rewardPayment, counterparty, changes.changes(1)))
    //                .willReturn(true);
    //
    //        subject.preview(changes);
    //
    //        verify(rewardCalculator).applyReward(rewardPayment, counterparty, changes.changes(1));
    //        verify(sideEffectsTracker).trackRewardPayment(counterpartyId.getAccountNum(), rewardPayment);
    //
    //        verify(stakeChangeManager).awardStake(1, (long) changes.changes(0).get(AccountProperty.BALANCE), false);
    //
    //        verify(stakeChangeManager).withdrawStake(0, changes.entity(1).getBalance(), false);
    //
    //        verify(stakeChangeManager).awardStake(1, (long) changes.changes(1).get(AccountProperty.BALANCE), false);
    //
    //        assertFalse(subject.hasBeenRewarded(0));
    //        assertTrue(subject.hasBeenRewarded(1));
    //        // both have stakeMeta changes
    //        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[1]);
    //    }
    //
    //    @Test
    //    void doesNotAwardStakeFromDeletedAccount() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var changes = buildChanges();
    //        final var rewardPayment = 1L;
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        counterparty.setDeleted(true);
    //
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(counterparty)).willReturn(rewardPayment);
    //        given(rewardCalculator.applyReward(rewardPayment, counterparty, changes.changes(1)))
    //                .willReturn(true);
    //
    //        subject.preview(changes);
    //
    //        verify(rewardCalculator).applyReward(rewardPayment, counterparty, changes.changes(1));
    //        verify(sideEffectsTracker).trackRewardPayment(counterpartyId.getAccountNum(), rewardPayment);
    //
    //        verify(stakeChangeManager).awardStake(1, (long) changes.changes(0).get(AccountProperty.BALANCE), false);
    //
    //        verify(stakeChangeManager).withdrawStake(0, changes.entity(1).getBalance(), false);
    //
    //        verify(stakeChangeManager, never())
    //                .awardStake(1, (long) changes.changes(1).get(AccountProperty.BALANCE), false);
    //
    //        assertFalse(subject.hasBeenRewarded(0));
    //        assertTrue(subject.hasBeenRewarded(1));
    //        // both have stakeMeta changes
    //        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[1]);
    //    }
    //
    //    @Test
    //    void checksIfRewardsToBeActivatedEveryHandle() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
    //        changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
    //        changes.include(counterpartyId, counterparty, randomStakedNodeChanges(counterpartyBalance - amount -
    // 100L));
    //
    //        stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] {5, 5}));
    //        subject.setRewardsActivated(true);
    //        given(dynamicProperties.getStakingStartThreshold()).willReturn(100L);
    //
    //        // rewards are activated,so can't activate again
    //        subject.preview(changes);
    //        verify(networkCtx, never()).setStakingRewardsActivated(true);
    //        verify(stakeChangeManager, never()).initializeAllStakingStartsTo(anyLong());
    //
    //        // rewards are not activated, threshold is less but balance for 0.0.800 is not increased
    //        subject.setRewardsActivated(false);
    //        given(dynamicProperties.getStakingStartThreshold()).willReturn(20L);
    //        given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccountNum);
    //
    //        subject.preview(changes);
    //
    //        verify(networkCtx, never()).setStakingRewardsActivated(true);
    //        assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
    //        assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
    //        assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[1]);
    //        assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[1]);
    //
    //        // rewards are not activated, and balance increased
    //        changes.include(stakingFundId, stakingFund, Map.of(AccountProperty.BALANCE, 100L));
    //
    //        subject.preview(changes);
    //
    //        verify(networkCtx).setStakingRewardsActivated(true);
    //        verify(stakeChangeManager).initializeAllStakingStartsTo(anyLong());
    //        assertEquals(0L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
    //        assertEquals(0L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
    //        assertEquals(0L, stakingInfo.get(node0Id).getRewardSumHistory()[1]);
    //        assertEquals(0L, stakingInfo.get(node1Id).getRewardSumHistory()[1]);
    //        verify(rewardCalculator, times(3)).reset();
    //    }
    //
    //    @Test
    //    void checksIfRewardableIfChangesHaveStakingFields() {
    //        counterparty.setStakePeriodStart(-1);
    //        counterparty.setStakedId(-1);
    //        final var changes = randomStakedNodeChanges(100L);
    //
    //        subject.setRewardsActivated(true);
    //
    //        // has changes to StakeMeta,
    //        assertTrue(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // valid fields, plus a ledger-managed staking field change
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        assertTrue(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // valid fields, plus a stakedToMe updated
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        assertTrue(subject.isRewardSituation(counterparty, 1_234_567, Collections.emptyMap()));
    //
    //        // rewards not activated
    //        subject.setRewardsActivated(false);
    //        assertFalse(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // declined reward on account, but changes have it as false
    //        counterparty.setDeclineReward(true);
    //        subject.setRewardsActivated(true);
    //        assertTrue(subject.isRewardSituation(counterparty, -1, changes));
    //
    //        // staked to account
    //        counterparty.setStakedId(2L);
    //        assertFalse(subject.isRewardSituation(counterparty, -1, changes));
    //    }
    //
    //    @Test
    //    void activatesStakingRewardsAndClearsRewardSumHistoryAsExpected() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final long randomFee = 3L;
    //        final long rewardsPaid = 1L;
    //        final var inorder = inOrder(sideEffectsTracker);
    //        counterparty.setStakePeriodStart(-1L);
    //        final var changes = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
    //
    //        changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
    //        changes.include(
    //                counterpartyId, counterparty, randomStakedNodeChanges(counterpartyBalance - amount - randomFee));
    //        changes.include(stakingFundId, stakingFund, onlyBalanceChanges(randomFee));
    //
    //        willCallRealMethod().given(networkCtx).areRewardsActivated();
    //        willCallRealMethod().given(networkCtx).setStakingRewardsActivated(true);
    //        given(rewardCalculator.rewardsPaidInThisTxn()).willReturn(rewardsPaid);
    //        given(dynamicProperties.getStakingStartThreshold()).willReturn(rewardsPaid);
    //
    //        stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] {5, 5}));
    //        given(stakePeriodManager.currentStakePeriod()).willReturn(19132L);
    //        given(stakeChangeManager.findOrAdd(eq(800L), any())).willReturn(2);
    //        given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccountNum);
    //
    //        // rewardsSumHistory is not cleared
    //        assertEquals(5, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
    //        assertEquals(5, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
    //        assertEquals(-1, counterparty.getStakePeriodStart());
    //        assertEquals(-1, party.getStakePeriodStart());
    //
    //        subject.preview(changes);
    //
    //        inorder.verify(sideEffectsTracker).trackHbarChange(partyId.getAccountNum(), +amount);
    //        inorder.verify(sideEffectsTracker).trackHbarChange(counterpartyId.getAccountNum(), -amount - randomFee);
    //        inorder.verify(sideEffectsTracker)
    //                .trackHbarChange(stakingFundId.getAccountNum(), randomFee - stakingFund.getBalance() -
    // rewardsPaid);
    //        verify(networkCtx).setStakingRewardsActivated(true);
    //        verify(stakeChangeManager).initializeAllStakingStartsTo(19132L);
    //
    //        // rewardsSumHistory is cleared
    //        assertEquals(0, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
    //        assertEquals(0, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
    //        assertEquals(-1, party.getStakePeriodStart());
    //    }
    //
    //    @Test
    //    void stakingEffectsWorkAsExpectedWhenStakingToNode() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var inorderST = inOrder(sideEffectsTracker);
    //        final var inorderM = inOrder(stakeChangeManager);
    //
    //        final var pendingChanges = buildPendingNodeStakeChanges();
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //
    //        given(rewardCalculator.computePendingReward(any())).willReturn(10L);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.applyReward(10L, counterparty, pendingChanges.changes(0)))
    //                .willReturn(true);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        subject.preview(pendingChanges);
    //
    //        inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, 10L);
    //
    //        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
    //        inorderM.verify(stakeChangeManager).awardStake(1L, 0, false);
    //        // StakingMeta changes
    //        assertEquals(-1, subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //    }
    //
    //    @Test
    //    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChanges() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var inorderST = inOrder(sideEffectsTracker);
    //        final var inorderM = inOrder(stakeChangeManager);
    //
    //        final var pendingChanges = changesWithNoStakingMetaUpdates();
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //
    //        given(rewardCalculator.computePendingReward(any())).willReturn(10L);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.applyReward(10L, counterparty, pendingChanges.changes(0)))
    //                .willReturn(true);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        subject.preview(pendingChanges);
    //
    //        inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, 10L);
    //
    //        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
    //        inorderM.verify(stakeChangeManager).awardStake(0L, 0, false);
    //        // StakingMeta changes
    //        assertEquals(
    //                counterpartyBalance + counterparty.getStakedToMe(),
    //                subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //    }
    //
    //    @Test
    //    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChangesAndNoReward() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var inorderST = inOrder(sideEffectsTracker);
    //        final var inorderM = inOrder(stakeChangeManager);
    //
    //        final var pendingChanges = changesWithNoStakingMetaUpdates();
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var stakePeriodStart = 12345678L;
    //        counterparty.setStakePeriodStart(stakePeriodStart);
    //
    //        given(rewardCalculator.computePendingReward(any())).willReturn(0L);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(stakePeriodStart - 1);
    //        given(stakePeriodManager.startUpdateFor(-1L, -1L, true, false)).willReturn(stakePeriodStart);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        subject.preview(pendingChanges);
    //
    //        inorderST.verify(sideEffectsTracker, never()).trackRewardPayment(anyLong(), anyLong());
    //
    //        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
    //        inorderM.verify(stakeChangeManager).awardStake(0L, 0, false);
    //        // StakingMeta changes
    //        assertEquals(
    //                counterpartyBalance + counterparty.getStakedToMe(),
    //                subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //        assertEquals(stakePeriodStart, subject.getStakePeriodStartUpdates()[0]);
    //    }
    //
    //    @Test
    //    void sasolarpMgmtWorksAsExpectedWhenStakingToNodeWithNoStakingMetaChangesAndNoReward() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var inorderST = inOrder(sideEffectsTracker);
    //        final var inorderM = inOrder(stakeChangeManager);
    //
    //        final var pendingChanges = changesWithNoStakingMetaUpdates();
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var stakePeriodStart = 12345678L;
    //        counterparty.setStakePeriodStart(stakePeriodStart);
    //
    //        given(rewardCalculator.computePendingReward(any())).willReturn(0L);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(stakePeriodManager.currentStakePeriod()).willReturn(stakePeriodStart + 1);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        subject.preview(pendingChanges);
    //
    //        inorderST.verify(sideEffectsTracker, never()).trackRewardPayment(anyLong(), anyLong());
    //
    //        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
    //        inorderM.verify(stakeChangeManager).awardStake(0L, 0, false);
    //        // StakingMeta changes
    //        assertEquals(
    //                counterpartyBalance + counterparty.getStakedToMe(),
    //                subject.getStakeAtStartOfLastRewardedPeriodUpdates()[0]);
    //    }
    //
    //    @Test
    //    void stakingEffectsWorkAsExpectedWhenStakingToAccount() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var inorderST = inOrder(sideEffectsTracker);
    //        final var inorderM = inOrder(stakeChangeManager);
    //        final var rewardPayment = HBARS_TO_TINYBARS + 100;
    //
    //        final var pendingChanges = buildPendingAccountStakeChanges();
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //
    //        given(stakePeriodManager.startUpdateFor(-1L, 2L, true, true)).willReturn(13L);
    //        given(stakePeriodManager.startUpdateFor(0L, 0L, false, false)).willReturn(666L);
    //        given(rewardCalculator.applyReward(anyLong(), any(), any())).willReturn(true);
    //
    //        given(rewardCalculator.computePendingReward(any())).willReturn(rewardPayment);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        subject.preview(pendingChanges);
    //
    //        inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, rewardPayment);
    //
    //        inorderM.verify(stakeChangeManager).findOrAdd(anyLong(), any());
    //        inorderM.verify(stakeChangeManager).withdrawStake(0L, counterpartyBalance, false);
    //        inorderM.verify(stakeChangeManager, never()).awardStake(2L, 0L, false);
    //
    //        final var updatedStakePeriodStarts = subject.getStakePeriodStartUpdates();
    //        assertEquals(13L, updatedStakePeriodStarts[0]);
    //        assertEquals(666L, updatedStakePeriodStarts[1]);
    //    }
    //
    //    @Test
    //    void rewardsUltimateBeneficiaryInsteadOfDeletedAccount() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var tbdReward = 1_234L;
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        beneficiary.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var pendingChanges = buildPendingNodeStakeChanges();
    //        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
    //        pendingChanges.changes(0).put(BALANCE, 0L);
    //
    //        final Map<AccountProperty, Object> firstBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
    //        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
    //        pendingChanges.include(partyId, party, firstBeneficiaryChanges);
    //
    //        final Map<AccountProperty, Object> secondBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        secondBeneficiaryChanges.put(AccountProperty.BALANCE, partyBalance + counterpartyBalance +
    // beneficiaryBalance);
    //        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);
    //
    //        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum())).willReturn(partyId.getAccountNum());
    //        given(txnCtx.getBeneficiaryOfDeleted(partyId.getAccountNum())).willReturn(beneficiaryId.getAccountNum());
    //        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(2);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(counterparty)).willReturn(tbdReward);
    //        given(rewardCalculator.computePendingReward(beneficiary)).willReturn(tbdReward);
    //        given(rewardCalculator.applyReward(tbdReward, beneficiary, pendingChanges.changes(2)))
    //                .willReturn(true);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.preview(pendingChanges);
    //        verify(rewardCalculator, times(2)).applyReward(tbdReward, beneficiary, pendingChanges.changes(2));
    //        verify(sideEffectsTracker, times(2)).trackRewardPayment(beneficiaryId.getAccountNum(), tbdReward);
    //    }
    //
    //    @Test
    //    void doesntTrackAnythingIfRedirectBeneficiaryDeclinedReward() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var tbdReward = 1_234L;
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        beneficiary.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var pendingChanges = buildPendingNodeStakeChanges();
    //        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
    //        pendingChanges.changes(0).put(BALANCE, 0L);
    //
    //        final Map<AccountProperty, Object> firstBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
    //        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
    //        pendingChanges.include(partyId, party, firstBeneficiaryChanges);
    //
    //        final Map<AccountProperty, Object> secondBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        secondBeneficiaryChanges.put(AccountProperty.BALANCE, partyBalance + counterpartyBalance +
    // beneficiaryBalance);
    //        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);
    //
    //        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum())).willReturn(partyId.getAccountNum());
    //        given(txnCtx.getBeneficiaryOfDeleted(partyId.getAccountNum())).willReturn(beneficiaryId.getAccountNum());
    //        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(2);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(counterparty)).willReturn(tbdReward);
    //        given(rewardCalculator.computePendingReward(beneficiary)).willReturn(tbdReward);
    //        given(rewardCalculator.applyReward(tbdReward, beneficiary, pendingChanges.changes(2)))
    //                .willReturn(false)
    //                .willReturn(true);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.preview(pendingChanges);
    //        verify(rewardCalculator, times(2)).applyReward(tbdReward, beneficiary, pendingChanges.changes(2));
    //        verify(sideEffectsTracker, times(1)).trackRewardPayment(beneficiaryId.getAccountNum(), tbdReward);
    //    }
    //
    //    @Test
    //    void failsHardIfMoreRedirectsThanDeletedEntitiesAreNeeded() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        beneficiary.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var pendingChanges = buildPendingNodeStakeChanges();
    //        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
    //        pendingChanges.changes(0).put(BALANCE, 0L);
    //
    //        final Map<AccountProperty, Object> firstBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
    //        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
    //        pendingChanges.include(partyId, party, firstBeneficiaryChanges);
    //
    //        final Map<AccountProperty, Object> secondBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        secondBeneficiaryChanges.put(AccountProperty.BALANCE, partyBalance + counterpartyBalance +
    // beneficiaryBalance);
    //        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);
    //
    //        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum())).willReturn(partyId.getAccountNum());
    //        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(1);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(any())).willReturn(123L);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        assertThrows(IllegalStateException.class, () -> subject.preview(pendingChanges));
    //    }
    //
    //    @Test
    //    void updatesStakedToMeSideEffects() {
    //        counterparty.setStakedId(1L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var pendingChanges = buildPendingAccountStakeChanges();
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        given(accounts.get(EntityNum.fromLong(1L))).willReturn(merkleAccount);
    //        given(merkleAccount.getStakedToMe()).willReturn(0L);
    //
    //        given(accounts.get(EntityNum.fromLong(2L))).willReturn(merkleAccount);
    //        given(merkleAccount.getStakedToMe()).willReturn(0L);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[1] = 0;
    //        subject.getRewardsEarned()[2] = 1;
    //        assertEquals(2, pendingChanges.size());
    //
    //        subject.setCurStakedId(1L);
    //        subject.setNewStakedId(2L);
    //        Arrays.fill(subject.getStakedToMeUpdates(), NA);
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //        assertEquals(-555L * HBARS_TO_TINYBARS, subject.getStakedToMeUpdates()[2]);
    //        assertEquals(100L * HBARS_TO_TINYBARS, subject.getStakedToMeUpdates()[3]);
    //    }
    //
    //    @Test
    //    void includesIndirectStakeeInChangesEvenIfTotalStakeUnchanged() {
    //        counterparty.setStakedId(1L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var pendingChanges = buildPendingAccountStakeChanges(counterpartyBalance + 1);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        given(accounts.get(EntityNum.fromLong(1L))).willReturn(merkleAccount);
    //        given(merkleAccount.getStakedToMe()).willReturn(counterpartyBalance);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[1] = 0;
    //        subject.getRewardsEarned()[2] = 1;
    //        assertEquals(2, pendingChanges.size());
    //
    //        subject.setCurStakedId(1L);
    //        subject.setNewStakedId(1L);
    //        Arrays.fill(subject.getStakedToMeUpdates(), NA);
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //        assertEquals(counterpartyBalance, subject.getStakedToMeUpdates()[2]);
    //    }
    //
    //    @Test
    //    void doesntUpdateStakedToMeIfStakerBalanceIsExactlyTheSame() {
    //        counterparty.setStakedId(1L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var pendingChanges = buildPendingAccountStakeChanges(counterpartyBalance);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[1] = 0;
    //        subject.getRewardsEarned()[2] = 1;
    //        assertEquals(2, pendingChanges.size());
    //
    //        subject.setCurStakedId(1L);
    //        subject.setNewStakedId(1L);
    //        Arrays.fill(subject.getStakedToMeUpdates(), NA);
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //        assertEquals(NA, subject.getStakedToMeUpdates()[2]);
    //    }
    //
    //    @Test
    //    void updatesStakedToMeSideEffectsPaysRewardsIfRewardable() {
    //        counterparty.setStakedId(123L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var stakingFundChanges = new HashMap<AccountProperty, Object>();
    //        stakingFundChanges.put(AccountProperty.BALANCE, 100L);
    //
    //        final var map = new HashMap<AccountProperty, Object>();
    //        map.put(AccountProperty.BALANCE, 100L);
    //        map.put(AccountProperty.STAKED_ID, 123L);
    //        map.put(AccountProperty.DECLINE_REWARD, false);
    //
    //        final var pendingChanges = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
    //        pendingChanges.include(partyId, party, stakingFundChanges);
    //        pendingChanges.include(stakingFundId, stakingFund, new HashMap<>(stakingFundChanges));
    //        pendingChanges.include(counterpartyId, counterparty, map);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[0] = -1;
    //        subject.getRewardsEarned()[1] = -1;
    //        subject.setCurStakedId(partyId.getAccountNum());
    //        subject.setNewStakedId(partyId.getAccountNum());
    //        assertEquals(3, pendingChanges.size());
    //        final var stakedToMeUpdates = subject.getStakedToMeUpdates();
    //        stakedToMeUpdates[0] = counterpartyBalance + 2 * HBARS_TO_TINYBARS;
    //        stakedToMeUpdates[1] = counterpartyBalance + 2 * HBARS_TO_TINYBARS;
    //        stakedToMeUpdates[2] = -1L;
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //
    //        assertEquals(2 * HBARS_TO_TINYBARS, stakedToMeUpdates[0]);
    //        assertEquals(counterpartyBalance + 2 * HBARS_TO_TINYBARS, stakedToMeUpdates[1]);
    //    }
    //
    //    @Test
    //    void rewardAccountStakePeriodStartAlwaysReset() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //
    //        final var changes = buildChanges();
    //        final var rewardPayment = 1L;
    //        final var expectedFundingI = 2;
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(counterparty)).willReturn(rewardPayment);
    //        given(rewardCalculator.applyReward(rewardPayment, counterparty, changes.changes(1)))
    //                .willReturn(true);
    //        given(rewardCalculator.rewardsPaidInThisTxn()).willReturn(rewardPayment);
    //        given(stakePeriodManager.startUpdateFor(anyLong(), anyLong(), anyBoolean(), anyBoolean()))
    //                .willReturn(NA);
    //        given(stakeChangeManager.findOrAdd(anyLong(), any())).willAnswer(invocation -> {
    //            changes.include(
    //                    stakingFundId,
    //                    MerkleAccountFactory.newAccount().balance(123).get(),
    //                    new HashMap<>());
    //            return expectedFundingI;
    //        });
    //        subject.getStakePeriodStartUpdates()[expectedFundingI] = 666L;
    //
    //        subject.preview(changes);
    //        assertEquals(NA, subject.getStakePeriodStartUpdates()[expectedFundingI]);
    //    }

    private void randomStakeAccountChanges() {
        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(100L)
                .stakedAccountId(treasuryId)
                .declineReward(true)
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void randomStakeNodeChanges() {
        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(100L)
                .stakedNodeId(0L)
                .declineReward(true)
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void noStakeChanges() {
        writableAccountStore.put(account.copyBuilder().key(kycKey).build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void storesWithAccountProps(final Long amount, final Long stakeAtStartOfLastRewardPeriod) {
        final var copy = account.copyBuilder();
        if (amount != null) {
            copy.tinybarBalance(amount);
        }
        if (stakeAtStartOfLastRewardPeriod != null) {
            copy.stakeAtStartOfLastRewardedPeriod(stakeAtStartOfLastRewardPeriod);
        }
        readableAccounts = emptyReadableAccountStateBuilder()
                .value(payerId, copy.build())
                .value(stakingRewardId, stakingRewardAccount)
                .build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        writableAccounts = emptyWritableAccountStateBuilder()
                .value(payerId, copy.build())
                .value(stakingRewardId, stakingRewardAccount)
                .build();
        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        writableAccountStore = new WritableAccountStore(writableStates);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void stakingNotActivated() {
        configuration = HederaTestConfigBuilder.create()
                .withValue("staking.isEnabled", false)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
    }
}
