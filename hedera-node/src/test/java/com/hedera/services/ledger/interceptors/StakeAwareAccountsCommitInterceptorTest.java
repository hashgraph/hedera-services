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

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
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
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StakeAwareAccountsCommitInterceptorTest {
	@Mock
	private SideEffectsTracker sideEffectsTracker;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
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

	private StakeAwareAccountsCommitsInterceptor subject;
	private static final long stakePeriodStart = LocalDate.now(zoneUTC).toEpochDay() - 1;

	@BeforeEach
	void setUp() {
		subject = new StakeAwareAccountsCommitsInterceptor(sideEffectsTracker, () -> networkCtx, () -> stakingInfo,
				dynamicProperties, () -> accounts,
				rewardCalculator, manager);
		stakingInfo = buildsStakingInfoMap();
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
		changes.include(partyId, party, randomStakeFieldChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty, randomStakeFieldChanges(counterpartyBalance - amount));
		counterparty.setStakePeriodStart(stakePeriodStart - 2);

		given(networkCtx.areRewardsActivated()).willReturn(true);
		given(rewardCalculator.updateRewardChanges(counterparty, changes.changes(1))).willReturn(1L);
		given(rewardCalculator.latestRewardableStakePeriodStart()).willReturn(stakePeriodStart - 1);

		subject.preview(changes);

		verify(sideEffectsTracker).trackHbarChange(partyId.getAccountNum(), +amount);
		verify(sideEffectsTracker).trackHbarChange(counterpartyId.getAccountNum(), -amount);
		verify(rewardCalculator).updateRewardChanges(counterparty, changes.changes(1));
		verify(sideEffectsTracker).trackRewardPayment(counterpartyId.getAccountNum(), 1L);
	}

	@Test
	void checksIfRewardsToBeActivatedEveryHandle() {
		subject.setRewardsActivated(false);
		subject.setRewardBalanceChanged(true);
		subject.setNewRewardBalance(10L);
		assertTrue(subject.isRewardBalanceChanged());
		assertEquals(10L, subject.getNewRewardBalance());
		given(dynamicProperties.getStakingStartThreshold()).willReturn(20L);
		assertFalse(subject.shouldActivateStakingRewards());

		subject.activateRewardsIfValid();
		verify(networkCtx, never()).setStakingRewards(true);

		subject.setNewRewardBalance(20L);
		assertEquals(20L, subject.getNewRewardBalance());
		assertTrue(subject.shouldActivateStakingRewards());

		subject.activateRewardsIfValid();
		verify(networkCtx).setStakingRewards(true);
		verify(accounts).forEach(any());
		verify(stakingInfo).forEach(any());
	}

	@Test
	void checksIfRewardable() {
		given(networkCtx.areRewardsActivated()).willReturn(true);
		counterparty.setStakePeriodStart(-1);
		assertFalse(subject.isRewardable(counterparty, randomStakeFieldChanges(100L), stakePeriodStart));

		counterparty.setStakePeriodStart(stakePeriodStart - 2);
		assertTrue(subject.isRewardable(counterparty, randomStakeFieldChanges(100L), stakePeriodStart));

		given(networkCtx.areRewardsActivated()).willReturn(false);
		assertFalse(subject.isRewardable(counterparty, randomStakeFieldChanges(100L), stakePeriodStart));

		counterparty.setDeclineReward(true);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		assertFalse(subject.isRewardable(counterparty, randomStakeFieldChanges(100L), stakePeriodStart));
	}

	@Test
	void returnsIfRewardsShouldBeActivated() {
		subject.setRewardsActivated(true);
		assertTrue(subject.isRewardsActivated());
		assertFalse(subject.shouldActivateStakingRewards());

		subject.setNewRewardBalance(10L);
		assertFalse(subject.shouldActivateStakingRewards());

		subject.setRewardsActivated(false);
		assertFalse(subject.isRewardsActivated());
		assertFalse(subject.isRewardBalanceChanged());
		assertFalse(subject.shouldActivateStakingRewards());

		subject.setRewardBalanceChanged(true);
		assertTrue(subject.isRewardBalanceChanged());
		assertEquals(10L, subject.getNewRewardBalance());
		given(dynamicProperties.getStakingStartThreshold()).willReturn(20L);
		assertFalse(subject.shouldActivateStakingRewards());

		subject.setNewRewardBalance(20L);
		assertEquals(20L, subject.getNewRewardBalance());
		assertTrue(subject.shouldActivateStakingRewards());
	}

	@Test
	void activatesStakingRewardsAndClearsRewardSumHistoryAsExpected() {
		final long stakingFee = 2L;
		final var inorder = inOrder(sideEffectsTracker);
		given(dynamicProperties.getStakingStartThreshold()).willReturn(1L);

		final var changes = new EntityChangeSet<AccountID, MerkleAccount, AccountProperty>();
		changes.include(partyId, party, randomStakeFieldChanges(partyBalance + amount));
		changes.include(counterpartyId, counterparty,
				randomStakeFieldChanges(counterpartyBalance - amount - stakingFee));
		changes.include(stakingFundId, stakingFund, randomStakeFieldChanges(stakingFee));
		willCallRealMethod().given(networkCtx).areRewardsActivated();
		willCallRealMethod().given(networkCtx).setStakingRewards(true);
		willCallRealMethod().given(accounts).forEach(any());
		given(accounts.entrySet()).willReturn(Map.of(
				EntityNum.fromAccountId(counterpartyId), counterparty,
				EntityNum.fromAccountId(partyId), party,
				EntityNum.fromAccountId(stakingFundId), stakingFund).entrySet());

		stakingInfo.forEach((a, b) -> b.setRewardSumHistory(new long[] { 5, 5 }));

		final var mockLocalDate = mock(LocalDate.class);
		final var mockedStatic = mockStatic(LocalDate.class);
		mockedStatic.when(() -> LocalDate.now(zoneUTC)).thenReturn(mockLocalDate);
		when(mockLocalDate.toEpochDay()).thenReturn(19131L);

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

		// rewardsSumHistory is cleared
		assertEquals(0, stakingInfo.get(EntityNum.fromLong(3L)).getRewardSumHistory()[0]);
		assertEquals(0, stakingInfo.get(EntityNum.fromLong(4L)).getRewardSumHistory()[0]);
		assertEquals(19131, counterparty.getStakePeriodStart());
		assertEquals(-1, party.getStakePeriodStart());
	}


	private MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
		given(addressBook.getSize()).willReturn(2);
		given(addressBook.getAddress(0)).willReturn(address1);
		given(address1.getMemo()).willReturn("0.0.3");
		given(addressBook.getAddress(1)).willReturn(address2);
		given(address2.getMemo()).willReturn("0.0.4");

		return buildStakingInfoMap(addressBook);
	}

	private Map<AccountProperty, Object> randomStakeFieldChanges(final long newBalance) {
		return Map.of(
				AccountProperty.BALANCE, newBalance,
				AccountProperty.STAKED_ID, -2L);
	}

	private Map<AccountProperty, Object> randomNotStakeFieldChanges(final long newBalance) {
		return Map.of(
				AccountProperty.ALIAS, ByteString.copyFromUtf8("testing"));
	}

	private Map<AccountProperty, Object> randomStakeFieldDeclineRewardChanges(final long newBalance) {
		return Map.of(
				AccountProperty.DECLINE_REWARD, false,
				AccountProperty.STAKED_ID, -2L);
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
