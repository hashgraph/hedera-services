package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.utils.EntityNum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerkleStakingInfoTest {
	private MerkleStakingInfo subject;

	private final int number = 34;
	private final long minStake = 100L;
	private final long maxStake = 10_000L;
	private final long stakeToReward = 345L;
	private final long stakeToNotReward = 155L;
	private final long stakeRewardStart = 1234L;
	private final long stake = 500L;
	private final long[] rewardSumHistory = new long[]{1, 2};

	@BeforeEach
	void setUp() {
		subject = new MerkleStakingInfo(minStake, maxStake, stakeToReward, stakeToNotReward, stakeRewardStart, stake, rewardSumHistory);
		subject.setKey(EntityNum.fromInt(number));
	}

	@Test
	void objectContractsWork() {
		final long otherMinStake = 101L;
		final long otherMaxStake = 10_001L;
		final long otherStakeToReward = 344L;
		final long otherStakeToNotReward = 156L;
		final long otherStakeRewardStart = 1235L;
		final long otherStake = 501L;
		final long[] otherRewardSumHistory = new long[] {3L, 2L};
		final var subject2 = new MerkleStakingInfo(otherMinStake, maxStake, stakeToReward, stakeToNotReward, stakeRewardStart, stake, rewardSumHistory);
		final var subject3 = new MerkleStakingInfo(minStake, otherMaxStake, stakeToReward, stakeToNotReward, stakeRewardStart, stake, rewardSumHistory);
		final var subject4 = new MerkleStakingInfo(minStake, maxStake, otherStakeToReward, stakeToNotReward, stakeRewardStart, stake, rewardSumHistory);
		final var subject5 = new MerkleStakingInfo(minStake, maxStake, stakeToReward, otherStakeToNotReward, stakeRewardStart, stake, rewardSumHistory);
		final var subject6 = new MerkleStakingInfo(minStake, maxStake, stakeToReward, stakeToNotReward, otherStakeRewardStart, stake, rewardSumHistory);
		final var subject7 = new MerkleStakingInfo(minStake, maxStake, stakeToReward, stakeToNotReward, stakeRewardStart, otherStake, rewardSumHistory);
		final var subject8 = new MerkleStakingInfo(minStake, maxStake, stakeToReward, stakeToNotReward, stakeRewardStart, stake, otherRewardSumHistory);
		final var identical = new MerkleStakingInfo(minStake, maxStake, stakeToReward, stakeToNotReward, stakeRewardStart, stake, rewardSumHistory);

		assertNotEquals(subject, new Object());
		assertNotEquals(subject, subject2);
		assertNotEquals(subject, subject3);
		assertNotEquals(subject, subject4);
		assertNotEquals(subject, subject5);
		assertNotEquals(subject, subject6);
		assertNotEquals(subject, subject7);
		assertNotEquals(subject, subject8);
		assertEquals(subject, identical);
		assertEquals(subject, subject);

		assertNotEquals(subject.hashCode(), subject2.hashCode());
		assertEquals(subject.hashCode(), identical.hashCode());
	}

	@Test
	void toStringWorks() {
		final var expected = "MerkleStakingInfo{id=0.0.0.34, minStake=100, maxStake=10000, stakeToReward=345, " +
				"stakeToNotReward=155, stakeRewardStart=1234, stake=500, rewardSumHistory=[1, 2]}";

		assertEquals(expected, subject.toString());
	}

	@Test
	void gettersAndSettersWork() {
		var subject = new MerkleStakingInfo();

		subject.setKey(EntityNum.fromInt(number));
		subject.setMinStake(minStake);
		subject.setMaxStake(maxStake);
		subject.setStakeToReward(stakeToReward);
		subject.setStakeToNotReward(stakeToNotReward);
		subject.setStakeRewardStart(stakeRewardStart);
		subject.setStake(stake);
		subject.setRewardSumHistory(rewardSumHistory);

		assertEquals(number, subject.getKey().intValue());
		assertEquals(minStake, subject.getMinStake());
		assertEquals(maxStake, subject.getMaxStake());
		assertEquals(stakeToReward, subject.getStakeToReward());
		assertEquals(stakeToNotReward, subject.getStakeToNotReward());
		assertEquals(stakeRewardStart, subject.getStakeRewardStart());
		assertEquals(stake, subject.getStake());
		assertArrayEquals(rewardSumHistory, subject.getRewardSumHistory());
	}

	@Test
	void copyWorks() {
		var copy = subject.copy();

		assertTrue(subject.isImmutable());
		assertEquals(copy, subject);
	}

}
