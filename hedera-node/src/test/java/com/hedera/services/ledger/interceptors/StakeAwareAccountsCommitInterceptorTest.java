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

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.zoneUTC;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
	private BootstrapProperties bootstrapProperties;
	@Mock
	private RewardCalculator rewardCalculator;
	@Mock
	private StakeChangeManager stakeChangeManager;
	@Mock
	private AddressBook addressBook;
	@Mock
	private Address address1 = mock(Address.class);
	@Mock
	private Address address2 = mock(Address.class);
	@Mock
	private StakePeriodManager stakePeriodManager;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleAccount merkleAccount;
	@Mock
	private AccountNumbers accountNumbers;

	private StakeInfoManager stakeInfoManager;

	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
	private StakeAwareAccountsCommitsInterceptor subject;

	private static final long stakePeriodStart = LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L),
			zoneUTC).toEpochDay() - 1;

	private final EntityNum node0Id = EntityNum.fromLong(0L);
	private final EntityNum node1Id = EntityNum.fromLong(1L);
	private final long stakingRewardAccountNum = 800L;

	@BeforeEach
	void setUp() throws NegativeAccountBalanceException {
		stakingInfo = buildsStakingInfoMap();
		stakeInfoManager = new StakeInfoManager(() -> stakingInfo);
		subject = new StakeAwareAccountsCommitsInterceptor(sideEffectsTracker, () -> networkCtx, dynamicProperties,
				rewardCalculator, stakeChangeManager, stakePeriodManager, stakeInfoManager, accountNumbers);
		reset();
	}

	@Test
	void noChangesAreNoop() {
		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();

		subject.preview(changes);

		verifyNoInteractions(sideEffectsTracker);
		verifyNoInteractions(stakeChangeManager);
		verifyNoInteractions(stakePeriodManager);
		verifyNoInteractions(rewardCalculator);
		verifyNoInteractions(networkCtx);
		verifyNoInteractions(dynamicProperties);
	}

	@Test
	void calculatesRewardIfNeeded() {
		final var amount = 1L;

		final var changes = buildChanges();
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		given(networkCtx.areRewardsActivated()).willReturn(true);
		given(rewardCalculator.computeAndApplyReward(counterparty, changes.changes(1))).willReturn(1L);
		given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(stakePeriodStart - 1);
		willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());

		subject.preview(changes);

		verify(rewardCalculator).computeAndApplyReward(counterparty, changes.changes(1));
		verify(sideEffectsTracker).trackRewardPayment(counterpartyId.getAccountNum(), 1L);

		verify(stakeChangeManager).awardStake(Math.abs((long) changes.changes(0).get(AccountProperty.STAKED_ID) + 1),
				(long) changes.changes(0).get(AccountProperty.BALANCE),
				false);
		verify(stakeChangeManager).withdrawStake(Math.abs(counterparty.getStakedId() + 1),
				changes.entity(1).getBalance() + changes.entity(1).getStakedToMe(),
				false);
		verify(stakeChangeManager).awardStake(Math.abs((long) changes.changes(1).get(AccountProperty.STAKED_ID) + 1),
				(long) changes.changes(1).get(AccountProperty.BALANCE),
				false);

		verify(sideEffectsTracker).trackHbarChange(partyId.getAccountNum(), +amount);
		verify(sideEffectsTracker).trackHbarChange(counterpartyId.getAccountNum(), -amount);
		assertFalse(subject.getHasBeenRewarded()[0]);
		assertTrue(subject.getHasBeenRewarded()[1]);
	}

	@Test
	void checksIfRewardsToBeActivatedEveryHandle() {
		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty, randomStakedNodeChanges(counterpartyBalance - amount - 100L));

		stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] { 5, 5 }));
		subject.setRewardsActivated(true);
		given(dynamicProperties.getStakingStartThreshold()).willReturn(100L);

		//rewards are activated,so can't activate again
		subject.preview(changes);
		verify(networkCtx, never()).setStakingRewardsActivated(true);
		verify(stakeChangeManager, never()).initializeAllStakingStartsTo(anyLong());

		//rewards are not activated, threshold is less but balance for 0.0.800 is not increased
		subject.setRewardsActivated(false);
		given(dynamicProperties.getStakingStartThreshold()).willReturn(20L);
		given(accountNumbers.stakingRewardAccount()).willReturn(stakingRewardAccountNum);

		subject.preview(changes);

		verify(networkCtx, never()).setStakingRewardsActivated(true);
		assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
		assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
		assertEquals(5L, stakingInfo.get(node0Id).getRewardSumHistory()[1]);
		assertEquals(5L, stakingInfo.get(node1Id).getRewardSumHistory()[1]);

		//rewards are not activated, and balance increased
		changes.include(stakingFundId, stakingFund, Map.of(AccountProperty.BALANCE, 100L));

		subject.preview(changes);

		verify(networkCtx).setStakingRewardsActivated(true);
		verify(stakeChangeManager).initializeAllStakingStartsTo(anyLong());
		assertEquals(0L, stakingInfo.get(node0Id).getRewardSumHistory()[0]);
		assertEquals(0L, stakingInfo.get(node1Id).getRewardSumHistory()[0]);
		assertEquals(0L, stakingInfo.get(node0Id).getRewardSumHistory()[1]);
		assertEquals(0L, stakingInfo.get(node1Id).getRewardSumHistory()[1]);
	}

	@Test
	void checksIfRewardableIfChangesHaveStakingFields() {
		counterparty.setStakePeriodStart(-1);
		counterparty.setStakedId(-1);
		final var changes = randomStakedNodeChanges(100L);

		subject.setRewardsActivated(true);
		given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(stakePeriodStart - 1);
		willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());

		// invalid stake period start
		assertFalse(subject.isRewardSituation(counterparty, -1, changes));

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
		final long randomFee = 3L;
		final long rewardsPaid = 1L;
		final var inorder = inOrder(sideEffectsTracker);
		counterparty.setStakePeriodStart(-1L);
		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();

		changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty,
				randomStakedNodeChanges(counterpartyBalance - amount - randomFee));
		changes.include(stakingFundId, stakingFund, onlyBalanceChanges(randomFee));

		willCallRealMethod().given(networkCtx).areRewardsActivated();
		willCallRealMethod().given(networkCtx).setStakingRewardsActivated(true);
		given(rewardCalculator.rewardsPaidInThisTxn()).willReturn(rewardsPaid);
		given(dynamicProperties.getStakingStartThreshold()).willReturn(rewardsPaid);

		stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] { 5, 5 }));
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
		inorder.verify(sideEffectsTracker).trackHbarChange(counterpartyId.getAccountNum(), -amount - randomFee);
		inorder.verify(sideEffectsTracker).trackHbarChange(stakingFundId.getAccountNum(),
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
		final var inorderST = inOrder(sideEffectsTracker);
		final var inorderM = inOrder(stakeChangeManager);

		final var pendingChanges = buildPendingNodeStakeChanges();
		final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);

		given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(stakePeriodStart - 1);
		willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());

		given(rewardCalculator.computeAndApplyReward(any(), any())).willReturn(10L);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
		stakingFund.setStakePeriodStart(-1);
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		subject.preview(pendingChanges);

		inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, 10L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(321L, -455L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(800L, 99L);

		inorderM.verify(stakeChangeManager).withdrawStake(
				0L, counterpartyBalance + counterparty.getStakedToMe(), false);
		inorderM.verify(stakeChangeManager).awardStake(1L, 100, false);
	}

	@Test
	void stakingEffectsWorkAsExpectedWhenStakingToAccount() {
		final Consumer<MerkleAccount> noopFinisherOne = a -> {};
		final Consumer<MerkleAccount> noopFinisherTwo = a -> {};
		final var inorderSP = inOrder(stakePeriodManager);
		final var inorderST = inOrder(sideEffectsTracker);
		final var inorderM = inOrder(stakeChangeManager);

		final var pendingChanges = buildPendingAccountStakeChanges();
		final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);

		given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(stakePeriodStart - 1);
		given(stakePeriodManager.finisherFor(-1L, 2L, 100L, true))
				.willReturn(noopFinisherOne);
		given(stakePeriodManager.finisherFor(0L, 0L, -1L, false))
				.willReturn(noopFinisherTwo);

		willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());

		given(rewardCalculator.computeAndApplyReward(any(), any())).willReturn(10L);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
		stakingFund.setStakePeriodStart(-1);
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		subject.preview(pendingChanges);

		inorderST.verify(sideEffectsTracker).trackRewardPayment(321L, 10L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(321L, -455L);
		inorderST.verify(sideEffectsTracker).trackHbarChange(800L, 99L);

		inorderM.verify(stakeChangeManager).findOrAdd(anyLong(), any());
		inorderM.verify(stakeChangeManager).withdrawStake(
				0L, counterpartyBalance + counterparty.getStakedToMe(), false);
		inorderM.verify(stakeChangeManager, never()).awardStake(2L, 2100, false);

		inorderSP.verify(stakePeriodManager).finisherFor(-1L, 2L, 100L, true);
		inorderSP.verify(stakePeriodManager).finisherFor(0L, 0L, -1L, false);

		assertSame(noopFinisherOne, subject.finisherFor(0));
		assertSame(noopFinisherTwo, subject.finisherFor(1));
	}

	@Test
	void updatesStakedToMeSideEffects() {
		counterparty.setStakedId(1L);
		stakingFund.setStakePeriodStart(-1);
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
		final var pendingChanges = buildPendingAccountStakeChanges();
		pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
		given(accounts.get(EntityNum.fromLong(1L))).willReturn(merkleAccount);
		given(merkleAccount.getStakedToMe()).willReturn(0L);

		given(accounts.get(EntityNum.fromLong(2L))).willReturn(merkleAccount);
		given(merkleAccount.getStakedToMe()).willReturn(0L);

		subject = new StakeAwareAccountsCommitsInterceptor(sideEffectsTracker, () -> networkCtx, dynamicProperties,
				rewardCalculator, new StakeChangeManager(stakeInfoManager, () -> accounts), stakePeriodManager,
				stakeInfoManager, accountNumbers);

		final var hasBeenRewarded = new boolean[64];
		hasBeenRewarded[1] = true;
		hasBeenRewarded[2] = true;
		subject.setHasBeenRewarded(hasBeenRewarded);
		assertEquals(2, pendingChanges.size());

		subject.setCurStakedId(1L);
		subject.setNewStakedId(2L);
		Arrays.fill(subject.getStakedToMeUpdates(), -1);
		subject.updateStakedToMeSideEffects(
				counterparty,
				StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT,
				pendingChanges.changes(0),
				pendingChanges);
		assertEquals(-555L, subject.getStakedToMeUpdates()[2]);
		assertEquals(100L, subject.getStakedToMeUpdates()[3]);
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

		var pendingChanges = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		pendingChanges.include(partyId, party, stakingFundChanges);
		pendingChanges.include(stakingFundId, stakingFund, new HashMap<>(stakingFundChanges));
		pendingChanges.include(counterpartyId, counterparty, map);

		subject = new StakeAwareAccountsCommitsInterceptor(sideEffectsTracker, () -> networkCtx, dynamicProperties,
				rewardCalculator, new StakeChangeManager(stakeInfoManager, () -> accounts), stakePeriodManager,
				stakeInfoManager, accountNumbers);

		final var hasBeenRewarded = new boolean[64];
		hasBeenRewarded[0] = false;
		hasBeenRewarded[1] = false;
		subject.setHasBeenRewarded(hasBeenRewarded);
		subject.setCurStakedId(partyId.getAccountNum());
		subject.setNewStakedId(partyId.getAccountNum());
		assertEquals(3, pendingChanges.size());
		final var stakedToMeUpdates = subject.getStakedToMeUpdates();
		stakedToMeUpdates[0] = 2000L;
		stakedToMeUpdates[1] = 2000L;
		stakedToMeUpdates[2] = -1L;
		subject.updateStakedToMeSideEffects(
				counterparty,
				StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT,
				pendingChanges.changes(0),
				pendingChanges);

		assertEquals(1545L, stakedToMeUpdates[0]);
		assertEquals(2000L, stakedToMeUpdates[1]);
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
		given(bootstrapProperties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(2_000_000_000L);
		given(bootstrapProperties.getIntProperty("staking.rewardHistory.numStoredPeriods")).willReturn(2);
		given(addressBook.getSize()).willReturn(2);
		given(addressBook.getAddress(0)).willReturn(address1);
		given(address1.getId()).willReturn(0L);
		given(addressBook.getAddress(1)).willReturn(address2);
		given(address2.getId()).willReturn(1L);

		final var info = buildStakingInfoMap(addressBook, bootstrapProperties);
		info.forEach((a, b) -> {
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

	private EntityChangeSet<AccountID, MerkleAccount, AccountProperty> buildChanges() {
		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomStakedNodeChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty, randomStakedNodeChanges(counterpartyBalance - amount));
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
