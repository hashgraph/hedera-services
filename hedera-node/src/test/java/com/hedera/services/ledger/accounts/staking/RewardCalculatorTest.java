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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
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
	private static final long todayNumber = LocalDate.ofInstant(Instant.ofEpochSecond(12345678L), zoneUTC).toEpochDay();
	private static final long[] rewardHistory = new long[366];

	@BeforeEach
	void setUp() {
		subject = new RewardCalculator(stakePeriodManager, stakeInfoManager);
		rewardHistory[0] = 5;
	}

	@Test
	void computesAndAppliesRewards() {
		given(stakeInfoManager.mutableStakeInfoFor(3L)).willReturn(merkleStakingInfo);
		given(stakePeriodManager.currentStakePeriod()).willReturn(todayNumber);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 2);
		given(account.getStakedId()).willReturn(3L);
		given(account.isDeclinedReward()).willReturn(false);
		given(account.getBalance()).willReturn(100L);

		subject.computeRewards(account);

		assertEquals(todayNumber - 1, subject.getAccountUpdatedStakePeriodStart());
		assertEquals(500, subject.getAccountReward());
	}

	@Test
	void doesntComputeReturnsZeroReward() {
		given(account.getStakePeriodStart()).willReturn(todayNumber - 1);

		subject.computeRewards(account);

		verify(account, never()).setStakePeriodStart(anyLong());
		assertEquals(0, subject.getAccountReward());

		given(account.getStakePeriodStart()).willReturn(todayNumber - 1);
		subject.computeRewards(account);
		assertEquals(todayNumber - 1, subject.getAccountUpdatedStakePeriodStart());
		assertEquals(0, subject.getAccountReward());
	}

	@Test
	void adjustsStakePeriodStartIfBeforeAnYear() throws NegativeAccountBalanceException {
		final var expectedStakePeriodStart = 19365L;

		final var merkleAccount = new MerkleAccount();
		merkleAccount.setStakePeriodStart(expectedStakePeriodStart - 500);
		merkleAccount.setStakedId(3L);
		merkleAccount.setBalance(100L);

		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(stakePeriodManager.currentStakePeriod()).willReturn(expectedStakePeriodStart);
		given(stakeInfoManager.mutableStakeInfoFor(3L)).willReturn(merkleStakingInfo);

		subject.computeRewards(merkleAccount);

		assertEquals(expectedStakePeriodStart - 1, subject.getAccountUpdatedStakePeriodStart());
		assertEquals(500, subject.getAccountReward());
	}

	@Test
	void updatingRewardsWorks() {
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 2);
		given(account.getStakedId()).willReturn(3L);
		given(account.isDeclinedReward()).willReturn(false);
		given(account.getBalance()).willReturn(100L);
		final var updatableMap = new HashMap<AccountProperty, Object>();
		given(stakePeriodManager.currentStakePeriod()).willReturn(todayNumber);
		given(stakeInfoManager.mutableStakeInfoFor(3L)).willReturn(merkleStakingInfo);

		subject.updateRewardChanges(account, updatableMap);

		assertEquals(todayNumber - 1, (long) updatableMap.get(AccountProperty.STAKE_PERIOD_START));
		assertEquals(600, (long) updatableMap.get(AccountProperty.BALANCE));
		assertEquals(500, subject.getAccountReward());
		assertEquals(500L, subject.rewardsPaidInThisTxn());

		subject.resetRewardsPaid();
		assertEquals(0L, subject.rewardsPaidInThisTxn());
	}

}
