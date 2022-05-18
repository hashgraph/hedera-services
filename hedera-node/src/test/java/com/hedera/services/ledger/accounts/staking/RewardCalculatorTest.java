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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static com.hedera.services.ledger.accounts.staking.RewardCalculator.zoneUTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class RewardCalculatorTest {
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
	@Mock
	private MerkleStakingInfo merkleStakingInfo;
	@Mock
	private MerkleAccount account;

	private RewardCalculator subject;
	private static final long todayNumber = LocalDate.now(zoneUTC).toEpochDay();
	private static final long[] rewardHistory = new long[366];

	@BeforeEach
	void setUp() {
		subject = new RewardCalculator(() -> accounts, () -> stakingInfo);
		rewardHistory[0] = 5;
	}

	@Test
	void calculatesIfRewardShouldBeEarned() {
		var stakePeriodStart = LocalDate.now(zoneUTC).toEpochDay() - 2;
		assertFalse(subject.noRewardToBeEarned(stakePeriodStart, todayNumber));

		stakePeriodStart = -1;
		assertFalse(subject.noRewardToBeEarned(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 365;
		assertTrue(subject.noRewardToBeEarned(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 1;
		assertTrue(subject.noRewardToBeEarned(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 2;
		assertFalse(subject.noRewardToBeEarned(stakePeriodStart, todayNumber));
	}

	@Test
	void computesAndAppliesRewards() {
		final var accountNum = EntityNum.fromLong(2000L);

		given(accounts.getForModify(accountNum)).willReturn(account);
		given(stakingInfo.get(EntityNum.fromLong(3L))).willReturn(merkleStakingInfo);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 2);
		given(account.getStakedId()).willReturn(3L);
		given(account.isDeclinedReward()).willReturn(false);
		given(account.getBalance()).willReturn(100L);

		final var reward = subject.computeAndApplyRewards(accountNum);

		verify(account).setStakePeriodStart(todayNumber - 1);
		assertEquals(500, reward);
	}

	@Test
	void doesntComputeReturnsZeroReward() {
		final var accountNum = EntityNum.fromLong(2000L);
		given(accounts.getForModify(accountNum)).willReturn(account);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 365);

		final var reward = subject.computeAndApplyRewards(accountNum);

		verify(account, never()).setStakePeriodStart(anyLong());
		assertEquals(0, reward);

		given(account.getStakePeriodStart()).willReturn(todayNumber - 1);
		assertEquals(0, subject.computeAndApplyRewards(accountNum));
	}

	@Test
	void adjustsStakePeriodStartIfBeforeAnYear() throws NegativeAccountBalanceException {
		final var accountNum = EntityNum.fromLong(2000L);
		final var today = 18763L;

		final var merkleAccount = new MerkleAccount();
		merkleAccount.setStakePeriodStart(today - 500);
		merkleAccount.setStakedId(3L);
		merkleAccount.setBalance(100L);
		given(accounts.getForModify(accountNum)).willReturn(merkleAccount);
		given(stakingInfo.get(EntityNum.fromLong(3L))).willReturn(merkleStakingInfo);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);

		final var reward = subject.computeAndApplyRewards(accountNum);

		assertEquals(19129L, merkleAccount.getStakePeriodStart());
		assertEquals(500, reward);
	}
}
