package com.hedera.services.ledger.accounts.staking;

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

import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.Units;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.zoneUTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RewardCalculatorTest {
	@Mock
	private StakePeriodManager stakePeriodManager;
	@Mock
	private StakeInfoManager stakeInfoManager;
	@Mock
	private MerkleStakingInfo merkleStakingInfo;
	@Mock
	private MerkleAccount account;

	private RewardCalculator subject;
	private static final long todayNumber = LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L),
			zoneUTC).toEpochDay();
	private static final long[] rewardHistory = new long[366];

	@BeforeEach
	void setUp() {
		subject = new RewardCalculator(stakePeriodManager, stakeInfoManager);
		rewardHistory[0] = 5;
	}

	@Test
	void updatesRewardsAsExpected() {
		final var changes = new HashMap<AccountProperty, Object>();
		setUpMocks();
		given(stakeInfoManager.mutableStakeInfoFor(0L)).willReturn(merkleStakingInfo);
		given(stakePeriodManager.currentStakePeriod()).willReturn(todayNumber);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 2);
		given(account.getStakedNodeAddressBookId()).willReturn(0L);
		given(account.isDeclinedReward()).willReturn(false);
		given(account.getBalance()).willReturn(100 * Units.HBARS_TO_TINYBARS);

		subject.setRewardsPaidInThisTxn(100L);
		final var reward = subject.computeAndApplyReward(account, changes);

		assertEquals(500, reward);
		assertEquals(account.getBalance() + reward, changes.get(AccountProperty.BALANCE));
		assertEquals(100 + reward, subject.rewardsPaidInThisTxn());

		// resets all fields
		subject.reset();
		assertEquals(0, subject.rewardsPaidInThisTxn());
	}

	@Test
	void returnsZeroRewardsIfRewardSumHistoryIsEmpty() {
		final var changes = new HashMap<AccountProperty, Object>();

		setUpMocks();
		given(stakePeriodManager.currentStakePeriod()).willReturn(todayNumber);
		given(stakeInfoManager.mutableStakeInfoFor(0L)).willReturn(merkleStakingInfo);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 1);

		subject.setRewardsPaidInThisTxn(100L);
		final var reward = subject.computeAndApplyReward(account, changes);

		verify(account, never()).setStakePeriodStart(anyLong());
		assertEquals(0, reward);
		assertFalse(changes.containsKey(AccountProperty.BALANCE));
		assertEquals(100, subject.rewardsPaidInThisTxn());
	}

	@Test
	void doesntComputeRewardReturnsZeroIfNotInRange() {
		final var changes = new HashMap<AccountProperty, Object>();

		given(stakePeriodManager.currentStakePeriod()).willReturn(todayNumber);
		given(stakePeriodManager.effectivePeriod(anyLong())).willReturn(todayNumber - 1);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 1);
		willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());

		final var reward = subject.computeAndApplyReward(account, changes);

		verify(account, never()).setStakePeriodStart(anyLong());
		assertEquals(0, reward);

		assertFalse(changes.containsKey(AccountProperty.BALANCE));
		assertEquals(0, subject.rewardsPaidInThisTxn());
	}

	@Test
	void adjustsEffectiveStartIfBeforeAnYear() throws NegativeAccountBalanceException {
		setUpMocks();
		final var changes = new HashMap<AccountProperty, Object>();

		final var expectedStakePeriodStart = 19365L;

		final var merkleAccount = new MerkleAccount();
		merkleAccount.setStakePeriodStart(expectedStakePeriodStart - 500);
		merkleAccount.setStakedId(-3L);
		merkleAccount.setBalance(100 * Units.HBARS_TO_TINYBARS);

		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(stakePeriodManager.currentStakePeriod()).willReturn(expectedStakePeriodStart);
		given(stakePeriodManager.effectivePeriod(anyLong())).willReturn(expectedStakePeriodStart - 365);
		given(stakeInfoManager.mutableStakeInfoFor(2L)).willReturn(merkleStakingInfo);

		final var reward = subject.computeAndApplyReward(merkleAccount, changes);

		assertEquals(500, reward);
		assertEquals(merkleAccount.getBalance() + reward, changes.get(AccountProperty.BALANCE));
		assertEquals(reward, subject.rewardsPaidInThisTxn());
	}

	@Test
	void estimatesPendingRewardsForStateView() {
		final var changes = new HashMap<AccountProperty, Object>();
		final var todayNum = 300L;

		given(stakePeriodManager.estimatedCurrentStakePeriod()).willReturn(todayNum);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(account.getStakePeriodStart()).willReturn(todayNum - 2);
		given(account.isDeclinedReward()).willReturn(false);
		given(account.getBalance()).willReturn(100 * Units.HBARS_TO_TINYBARS);
		given(stakePeriodManager.effectivePeriod(todayNum - 2)).willReturn(todayNum - 2);
		given(stakePeriodManager.isRewardable(todayNum - 2)).willReturn(true);

		subject.setRewardsPaidInThisTxn(100L);
		final long reward = subject.estimatePendingRewards(account, merkleStakingInfo);

		assertEquals(500, reward);

		// no changes to state
		assertFalse(changes.containsKey(AccountProperty.BALANCE));
		assertEquals(100, subject.rewardsPaidInThisTxn());

		// if declinedReward
		given(account.isDeclinedReward()).willReturn(true);
		assertEquals(0L, subject.estimatePendingRewards(account, merkleStakingInfo));
	}

	@Test
	void onlyEstimatesPendingRewardsIfRewardable() {
		final var todayNum = 300L;

		given(account.getStakePeriodStart()).willReturn(todayNum - 2);
		given(stakePeriodManager.effectivePeriod(todayNum - 2)).willReturn(todayNum - 2);

		final long reward = subject.estimatePendingRewards(account, merkleStakingInfo);

		assertEquals(0, reward);
	}

	private void setUpMocks() {
		given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(todayNumber);
		willCallRealMethod().given(stakePeriodManager).effectivePeriod(anyLong());
		willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());
	}

}
