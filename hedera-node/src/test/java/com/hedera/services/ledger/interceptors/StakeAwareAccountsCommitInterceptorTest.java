package com.hedera.services.ledger.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.system.Address;
import com.swirlds.common.system.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.zoneUTC;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

@ExtendWith(MockitoExtension.class)
class StakeAwareAccountsCommitInterceptorTest {
	@Mock
	private SideEffectsTracker sideEffectsTracker;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private RewardCalculator rewardCalculator;
	@Mock
	private StakeChangeManager manager;
	@Mock
	private AddressBook addressBook;
	@Mock
	private Address address1 = mock(Address.class);
	@Mock
	private Address address2 = mock(Address.class);
	@Mock
	private StakePeriodManager stakePeriodManager;

	private StakeInfoManager stakeInfoManager;

	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
	private StakeAwareAccountsCommitsInterceptor subject;

	private static final long stakePeriodStart = LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L),
			zoneUTC).toEpochDay() - 1;

	@BeforeEach
	void setUp() {
		stakingInfo = buildsStakingInfoMap();
		stakeInfoManager = new StakeInfoManager(() -> stakingInfo);
		subject = new StakeAwareAccountsCommitsInterceptor(sideEffectsTracker, () -> networkCtx, dynamicProperties,
				rewardCalculator, manager, stakePeriodManager, stakeInfoManager);
	}

	@Test
	void noChangesAreNoop() {
		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();

		subject.preview(changes);

		verifyNoInteractions(sideEffectsTracker);
	}

	@Test
	void calculatesRewardIfNeeded() {
		final var amount = 5L;

		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty, randomStakedNodeChanges(counterpartyBalance - amount));
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		given(networkCtx.areRewardsActivated()).willReturn(true);
		given(rewardCalculator.getAccountReward()).willReturn(1L);
		given(stakePeriodManager.latestRewardableStakePeriodStart()).willReturn(stakePeriodStart - 1);

		subject.preview(changes);

		verify(sideEffectsTracker).trackHbarChange(partyId.getAccountNum(), +amount);
		verify(sideEffectsTracker).trackHbarChange(counterpartyId.getAccountNum(), -amount);
		verify(rewardCalculator).updateRewardChanges(counterparty, changes.changes(1));
		verify(sideEffectsTracker).trackRewardPayment(counterpartyId.getAccountNum(), 1L);
	}

	@Test
	void checksIfRewardsToBeActivatedEveryHandle() {
		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty, randomStakedNodeChanges(counterpartyBalance - amount - 100L));

		stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] { 5, 5 }));
		subject.setRewardsActivated(false);
		subject.setRewardBalanceIncreased(true);
		subject.calculateNewRewardBalance(changes);
		assertTrue(subject.isRewardBalanceIncreased());

		given(dynamicProperties.getStakingStartThreshold()).willReturn(20L);
		assertFalse(subject.shouldActivateStakingRewards(10L));

		subject.checkStakingRewardsActivation(changes);
		verify(networkCtx, never()).setStakingRewards(true);


		assertTrue(subject.shouldActivateStakingRewards(20L));
		assertEquals(5L, stakingInfo.get(EntityNum.fromLong(3L)).getRewardSumHistory()[0]);
		assertEquals(5L, stakingInfo.get(EntityNum.fromLong(4L)).getRewardSumHistory()[0]);
		assertEquals(5L, stakingInfo.get(EntityNum.fromLong(3L)).getRewardSumHistory()[1]);
		assertEquals(5L, stakingInfo.get(EntityNum.fromLong(4L)).getRewardSumHistory()[1]);

		changes.include(stakingFundId, stakingFund, Map.of(AccountProperty.BALANCE, 100L));
		subject.checkStakingRewardsActivation(changes);
		verify(networkCtx).setStakingRewards(true);
		verify(manager).setStakePeriodStart(anyLong());
		assertEquals(0L, stakingInfo.get(EntityNum.fromLong(3L)).getRewardSumHistory()[0]);
		assertEquals(0L, stakingInfo.get(EntityNum.fromLong(4L)).getRewardSumHistory()[0]);
		assertEquals(0L, stakingInfo.get(EntityNum.fromLong(3L)).getRewardSumHistory()[1]);
		assertEquals(0L, stakingInfo.get(EntityNum.fromLong(4L)).getRewardSumHistory()[1]);
	}

	@Test
	void checksIfRewardable() {
		given(networkCtx.areRewardsActivated()).willReturn(true);
		counterparty.setStakePeriodStart(-1);
		counterparty.setStakedId(-1);
		assertFalse(subject.isRewardable(counterparty, randomStakedNodeChanges(100L), stakePeriodStart));

		counterparty.setStakePeriodStart(stakePeriodStart - 2);
		assertTrue(subject.isRewardable(counterparty, randomStakedNodeChanges(100L), stakePeriodStart));

		given(networkCtx.areRewardsActivated()).willReturn(false);
		assertFalse(subject.isRewardable(counterparty, randomStakedNodeChanges(100L), stakePeriodStart));

		counterparty.setDeclineReward(true);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		assertTrue(subject.isRewardable(counterparty, randomStakedNodeChanges(100L), stakePeriodStart));
	}

	@Test
	void returnsIfRewardsShouldBeActivated() {
		subject.setRewardsActivated(true);
		assertTrue(subject.isRewardsActivated());
		var newRewardBalance = 10L;
		assertFalse(subject.shouldActivateStakingRewards(newRewardBalance));

		assertFalse(subject.shouldActivateStakingRewards(newRewardBalance));

		subject.setRewardsActivated(false);
		assertFalse(subject.isRewardsActivated());
		assertFalse(subject.isRewardBalanceIncreased());
		assertFalse(subject.shouldActivateStakingRewards(newRewardBalance));

		subject.setRewardBalanceIncreased(true);
		assertTrue(subject.isRewardBalanceIncreased());
		given(dynamicProperties.getStakingStartThreshold()).willReturn(20L);
		assertFalse(subject.shouldActivateStakingRewards(newRewardBalance));

		newRewardBalance = 20L;
		assertTrue(subject.shouldActivateStakingRewards(newRewardBalance));
	}

	@Test
	void activatesStakingRewardsAndClearsRewardSumHistoryAsExpected() {
		final long stakingFee = 2L;
		final var inorder = inOrder(sideEffectsTracker);
		given(dynamicProperties.getStakingStartThreshold()).willReturn(1L);

		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty,
				randomStakedNodeChanges(counterpartyBalance - amount - stakingFee));
		changes.include(stakingFundId, stakingFund, randomStakedNodeChanges(stakingFee));
		willCallRealMethod().given(networkCtx).areRewardsActivated();
		willCallRealMethod().given(networkCtx).setStakingRewards(true);

		given(rewardCalculator.rewardsPaidInThisTxn()).willReturn(1L);
		counterparty.setStakePeriodStart(-1L);

		stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] { 5, 5 }));
		given(stakePeriodManager.latestRewardableStakePeriodStart()).willReturn(19131L);
		given(stakePeriodManager.currentStakePeriod()).willReturn(19132L);

		// rewardsSumHistory is not cleared
		assertEquals(5, stakingInfo.get(EntityNum.fromLong(3L)).getRewardSumHistory()[0]);
		assertEquals(5, stakingInfo.get(EntityNum.fromLong(4L)).getRewardSumHistory()[0]);
		assertEquals(-1, counterparty.getStakePeriodStart());
		assertEquals(-1, party.getStakePeriodStart());

		subject.preview(changes);

		inorder.verify(sideEffectsTracker).trackHbarChange(partyId.getAccountNum(), +amount);
		inorder.verify(sideEffectsTracker).trackHbarChange(counterpartyId.getAccountNum(), -amount - stakingFee);
		inorder.verify(sideEffectsTracker).trackHbarChange(stakingFundId.getAccountNum(), 1L);
		verify(networkCtx).setStakingRewards(true);
		verify(manager).setStakePeriodStart(19132L);

		// rewardsSumHistory is cleared
		assertEquals(0, stakingInfo.get(EntityNum.fromLong(3L)).getRewardSumHistory()[0]);
		assertEquals(0, stakingInfo.get(EntityNum.fromLong(4L)).getRewardSumHistory()[0]);
		assertEquals(-1, party.getStakePeriodStart());
	}

	@Test
	void paysRewardIfRewardable() {
		final var hasBeenRewarded = new boolean[100];
		subject.setHasBeenRewarded(hasBeenRewarded);

		final var pendingChanges = buildPendingNodeStakeChanges();
		assertEquals(1, pendingChanges.size());

		subject.payRewardIfRewardable(pendingChanges, 0, stakePeriodStart - 2);
		verify(rewardCalculator, never()).updateRewardChanges(counterparty, pendingChanges.changes(0));

		given(networkCtx.areRewardsActivated()).willReturn(true);
		counterparty.setStakePeriodStart(stakePeriodStart - 2);
		subject.payRewardIfRewardable(pendingChanges, 0, stakePeriodStart - 1);
		verify(rewardCalculator).updateRewardChanges(counterparty, pendingChanges.changes(0));
		verify(sideEffectsTracker).trackRewardPayment(eq(counterpartyId.getAccountNum()), anyLong());
		assertTrue(hasBeenRewarded[0]);
	}

	@Test
	void stakingEffectsWorkAsExpectedWhenStakingToNode() {
		final var inorderST = inOrder(sideEffectsTracker);
		final var inorderM = inOrder(manager);

		final var pendingChanges = buildPendingNodeStakeChanges();
		final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
		given(stakePeriodManager.latestRewardableStakePeriodStart()).willReturn(stakePeriodStart - 1);
		given(rewardCalculator.getAccountReward()).willReturn(10l);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
		stakingFund.setStakePeriodStart(-1);
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		willCallRealMethod().given(manager).getNodeStakeeNum(any());
		willCallRealMethod().given(manager).getAccountStakeeNum(any());
		willCallRealMethod().given(manager).finalStakedToMeGiven(any(), any());
		willCallRealMethod().given(manager).finalDeclineRewardGiven(any(), any());

		subject.preview(pendingChanges);

		inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, 10L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(321L, -455L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(800L, 99L);

		inorderM.verify(manager).getAccountStakeeNum(pendingChanges.changes(0));
		inorderM.verify(manager, never()).updateStakedToMe(anyInt(), anyLong(), any());
		inorderM.verify(manager).getAccountStakeeNum(pendingChanges.changes(1));
		inorderM.verify(manager).getNodeStakeeNum(pendingChanges.changes(0));
		inorderM.verify(manager).finalDeclineRewardGiven(counterparty, pendingChanges.changes(0));
		inorderM.verify(manager).withdrawStake(0L, counterpartyBalance + counterparty.getStakedToMe(), false);
		inorderM.verify(manager).finalStakedToMeGiven(counterparty, pendingChanges.changes(0));
		inorderM.verify(manager).awardStake(1L, 2100, false);
	}

	@Test
	void stakingEffectsWorkAsExpectedWhenStakingToAccount() {
		final var inorderST = inOrder(sideEffectsTracker);
		final var inorderM = inOrder(manager);

		final var pendingChanges = buildPendingAccountStakeChanges();
		final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
		given(stakePeriodManager.latestRewardableStakePeriodStart()).willReturn(stakePeriodStart - 1);
		given(rewardCalculator.getAccountReward()).willReturn(10L);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
		stakingFund.setStakePeriodStart(-1);
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		willCallRealMethod().given(manager).getNodeStakeeNum(any());
		willCallRealMethod().given(manager).getAccountStakeeNum(any());
		willCallRealMethod().given(manager).finalDeclineRewardGiven(any(), any());

		subject.preview(pendingChanges);

		inorderST.verify(sideEffectsTracker, times(2)).trackRewardPayment(321L, 10L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(321L, -455L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(800L, 99L);


		inorderM.verify(manager).getAccountStakeeNum(pendingChanges.changes(0));
		inorderM.verify(manager).updateStakedToMe(0, 100, pendingChanges);
		inorderM.verify(manager).getAccountStakeeNum(pendingChanges.changes(1));

		inorderM.verify(manager).withdrawStake(0L, counterpartyBalance + counterparty.getStakedToMe(), false);
		inorderM.verify(manager, never()).awardStake(2L, 2100, false);
	}

	@Test
	void updatesStakedToMeSideEffects() {
		counterparty.setStakedId(1L);
		stakingFund.setStakePeriodStart(-1);
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
		final var pendingChanges = buildPendingAccountStakeChanges();
		pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);

		given(manager.findOrAdd(anyLong(), eq(pendingChanges))).willReturn(pendingChanges.size());
		willCallRealMethod().given(manager).getAccountStakeeNum(any());

		final var hasBeenRewarded = new boolean[100];
		hasBeenRewarded[1] = true;
		hasBeenRewarded[2] = true;
		subject.setHasBeenRewarded(hasBeenRewarded);

		assertEquals(3, subject.updateStakedToMeSideEffects(0, pendingChanges,
				stakePeriodStart - 1));
	}

	public EntityChangeSet<AccountID, MerkleAccount, AccountProperty> buildPendingNodeStakeChanges() {
		var changes = randomStakedNodeChanges(100L);
		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);
		return pendingChanges;
	}

	public EntityChangeSet<AccountID, MerkleAccount, AccountProperty> buildPendingAccountStakeChanges() {
		var changes = randomStakeAccountChanges(100L);
		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(counterpartyId, counterparty, changes);
		return pendingChanges;
	}

	public MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
		given(addressBook.getSize()).willReturn(2);
		given(addressBook.getAddress(0)).willReturn(address1);
		given(address1.getMemo()).willReturn("0.0.3");
		given(addressBook.getAddress(1)).willReturn(address2);
		given(address2.getMemo()).willReturn("0.0.4");

		final var info = buildStakingInfoMap(addressBook);
		info.forEach((a, b) -> {
			b.setStakeToReward(300L);
			b.setStake(1000L);
			b.setStakeToNotReward(400L);
		});
		return info;
	}

	private Map<AccountProperty, Object> randomStakedNodeChanges(final long newBalance) {
		return Map.of(
				AccountProperty.BALANCE, newBalance,
				AccountProperty.STAKED_ID, -2L,
				AccountProperty.DECLINE_REWARD, false,
				AccountProperty.STAKED_TO_ME, 2000L);
	}

	private Map<AccountProperty, Object> randomStakeAccountChanges(final long newBalance) {
		return Map.of(
				AccountProperty.BALANCE, newBalance,
				AccountProperty.STAKED_ID, 2L,
				AccountProperty.DECLINE_REWARD, false,
				AccountProperty.STAKED_TO_ME, 2000L);
	}

	private static final long amount = 1L;
	private static final long partyBalance = 111L;
	private static final long counterpartyBalance = 555L;
	private static final AccountID partyId = AccountID.newBuilder().setAccountNum(123).build();
	private static final AccountID counterpartyId = AccountID.newBuilder().setAccountNum(321).build();
	private static final AccountID stakingFundId = AccountID.newBuilder().setAccountNum(800).build();
	private static final MerkleAccount party = MerkleAccountFactory.newAccount()
			.number(EntityNum.fromAccountId(partyId))
			.balance(partyBalance)
			.get();
	private static final MerkleAccount counterparty = MerkleAccountFactory.newAccount()
			.stakedId(-1)
			.number(EntityNum.fromAccountId(counterpartyId))
			.balance(counterpartyBalance)
			.get();
	private static final MerkleAccount stakingFund = MerkleAccountFactory.newAccount()
			.number(EntityNum.fromAccountId(stakingFundId))
			.balance(amount)
			.get();
}
