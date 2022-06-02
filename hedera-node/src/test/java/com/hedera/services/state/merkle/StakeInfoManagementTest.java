package com.hedera.services.state.merkle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StakeInfoManagementTest {
	@Test
	void updatedStakeCannotExceedMaxStake() {
		final var subject = subjectWith(1L, 10L, 9L, 2L);
		subject.reviewElectionsFromLastPeriodAndRecomputeStakes();
		assertEquals(10L, subject.getStake());
	}

	@Test
	void updatedStakeIsZeroIfBelowMinStake() {
		final var subject = subjectWith(12L, 20L, 9L, 2L);
		subject.reviewElectionsFromLastPeriodAndRecomputeStakes();
		assertEquals(0L, subject.getStake());
	}

	@Test
	void updatedStakeIsSumOfRewardedAndNotRewardedIfWithinBounds() {
		final var subject = subjectWith(12L, 20L, 9L, 5L);
		subject.reviewElectionsFromLastPeriodAndRecomputeStakes();
		assertEquals(14L, subject.getStake());
	}


	@Test
	void stakeRewardStartIsMinimumOfStakedToRewardAndNewStake() {
		final var subject = subjectWith(12L, 20L, 9L, 5L, 16L);
		final var newStakeRewardStart = subject.reviewElectionsFromLastPeriodAndRecomputeStakes();
		assertEquals(9L, newStakeRewardStart);
		assertEquals(9L, subject.getStakeRewardStart());
	}

	private MerkleStakingInfo subjectWith(
			final long minStake,
			final long maxStake,
			final long stakedToReward,
			final long stakedToNotReward
	) {
		return subjectWith(minStake, maxStake, stakedToReward, stakedToNotReward, -1);
	}

	private MerkleStakingInfo subjectWith(
			final long minStake,
			final long maxStake,
			final long stakedToReward,
			final long stakedToNotReward,
			final long stakeRewardStart
	) {
		final var info = new MerkleStakingInfo();
		info.setMaxStake(maxStake);
		info.setMinStake(minStake);
		info.setStakeToReward(stakedToReward);
		info.setStakeToNotReward(stakedToNotReward);
		if (stakeRewardStart != -1) {
			info.setStakeRewardStart(stakeRewardStart);
		}
		return info;
	}
}