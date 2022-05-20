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
import com.hedera.services.state.merkle.MerkleNetworkContext;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardCalculatorTest {
	@Mock
	private MerkleNetworkContext networkContext;
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
		subject = new RewardCalculator(() -> stakingInfo, () -> networkContext);
		rewardHistory[0] = 5;
	}

	@Test
	void calculatesIfRewardShouldBeEarned() {
		var stakePeriodStart = LocalDate.now(zoneUTC).toEpochDay() - 2;
		assertTrue(subject.isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = -1;
		assertFalse(subject.isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 365;
		assertTrue(subject.isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 1;
		assertFalse(subject.isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 2;
		assertTrue(subject.isWithinRange(stakePeriodStart, todayNumber));
	}

	@Test
	void computesAndAppliesRewards() {
		given(stakingInfo.get(EntityNum.fromLong(3L))).willReturn(merkleStakingInfo);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);
		given(account.getStakePeriodStart()).willReturn(todayNumber - 2);
		given(account.getStakedId()).willReturn(3L);
		given(account.isDeclinedReward()).willReturn(false);
		given(account.getBalance()).willReturn(100L);

		final var result = subject.computeRewards(account);

		assertEquals(todayNumber - 1, result.getRight());
		assertEquals(500, result.getLeft());
	}

	@Test
	void doesntComputeReturnsZeroReward() {
		given(account.getStakePeriodStart()).willReturn(todayNumber - 1);

		final var result = subject.computeRewards(account);

		verify(account, never()).setStakePeriodStart(anyLong());
		assertEquals(0, result.getLeft());

		given(account.getStakePeriodStart()).willReturn(todayNumber - 1);
		assertEquals(0, subject.computeRewards(account).getLeft());
		assertEquals(todayNumber - 1, subject.computeRewards(account).getRight());
	}

	@Test
	void adjustsStakePeriodStartIfBeforeAnYear() throws NegativeAccountBalanceException {
		final var expectedStakePeriodStart = 19365L;

		final var merkleAccount = new MerkleAccount();
		merkleAccount.setStakePeriodStart(expectedStakePeriodStart - 500);
		merkleAccount.setStakedId(3L);
		merkleAccount.setBalance(100L);

		final var mockLocalDate = mock(LocalDate.class);
		final var mockedStatic = mockStatic(LocalDate.class);
		mockedStatic.when(() -> LocalDate.now(zoneUTC)).thenReturn(mockLocalDate);
		when(mockLocalDate.toEpochDay()).thenReturn(expectedStakePeriodStart);

		given(stakingInfo.get(EntityNum.fromLong(3L))).willReturn(merkleStakingInfo);
		given(merkleStakingInfo.getRewardSumHistory()).willReturn(rewardHistory);

		final var result = subject.computeRewards(merkleAccount);

		assertEquals(expectedStakePeriodStart - 1, result.getRight());
		assertEquals(500, result.getLeft());
		mockedStatic.close();
	}
}
