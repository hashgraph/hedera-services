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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Consumer;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.ZONE_UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StakePeriodManagerTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private MerkleNetworkContext networkContext;
	@Mock
	private PropertySource properties;

	private StakePeriodManager subject;

	@BeforeEach
	public void setUp() {
		subject = new StakePeriodManager(txnCtx, () -> networkContext, properties);
	}

	@Test
	void canFinalizeJustStakeToMeUpdate() {
		final var finisher = subject.finisherFor(0, 0, 123, false);

		final var result = getResultOf(finisher);

		assertEquals(-1, result.getStakePeriodStart());
		assertEquals(123, result.getStakedToMe());
	}

	@Test
	void canBeginStakePeriodStart() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		// UTC day 14
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(1_234_567));

		final var finisher = subject.finisherFor(0, -1, -1, false);

		final var result = getResultOf(finisher, 123L);

		assertEquals(14, result.getStakePeriodStart());
		assertEquals(123, result.getStakedToMe());
	}

	@Test
	void canPreserveStakePeriodStartIfNotRewardedAndStakingToSameNode() {
		final var finisher = subject.finisherFor(-1, -1, 321, false);

		final var result = getResultOf(finisher, 123L, 12);

		assertEquals(12, result.getStakePeriodStart());
		assertEquals(321, result.getStakedToMe());
	}

	@Test
	void canResetStakePeriodStartIfNotRewardedAndStakingToNewNode() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		// UTC day 14
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(1_234_567));

		final var finisher = subject.finisherFor(-2, -1, 321, false);

		final var result = getResultOf(finisher, 123L, 13);

		assertEquals(14, result.getStakePeriodStart());
		assertEquals(321, result.getStakedToMe());
	}

	@Test
	void canUpdateStakePeriodStartToYesterdayIfRewarded() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		// UTC day 14
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(1_234_567));

		final var finisher = subject.finisherFor(-1, -1, -1, true);

		final var result = getResultOf(finisher, 123L, 12);

		assertEquals(13, result.getStakePeriodStart());
		assertEquals(123, result.getStakedToMe());
	}

	@Test
	void returnsNullIfNothingToFinish() {
		assertNull(subject.finisherFor(0, 0, -1, false));
	}

	@Test
	void givesCurrentStakePeriod() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		final var instant = Instant.ofEpochSecond(12345L);
		given(txnCtx.consensusTime()).willReturn(instant);
		final var period = subject.currentStakePeriod();
		final var expectedPeriod = LocalDate.ofInstant(instant, ZONE_UTC).toEpochDay();
		assertEquals(expectedPeriod, period);
		assertEquals(expectedPeriod, subject.estimatedCurrentStakePeriod());

		var latesteRewardable = subject.firstNonRewardableStakePeriod();
		assertEquals(Long.MIN_VALUE, latesteRewardable);

		given(networkContext.areRewardsActivated()).willReturn(true);
		latesteRewardable = subject.firstNonRewardableStakePeriod();
		assertEquals(expectedPeriod - 1, latesteRewardable);
	}

	@Test
	void calculatesIfRewardShouldBeEarned() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		final var instant = Instant.ofEpochSecond(123456789L);
		given(txnCtx.consensusTime()).willReturn(instant);
		final var todayNumber = subject.currentStakePeriod() - 1;
		given(networkContext.areRewardsActivated()).willReturn(true);

		var stakePeriodStart = todayNumber - 366;
		assertTrue(subject.isRewardable(stakePeriodStart));

		stakePeriodStart = -1;
		assertFalse(subject.isRewardable(stakePeriodStart));

		stakePeriodStart = todayNumber - 365;
		assertTrue(subject.isRewardable(stakePeriodStart));

		stakePeriodStart = todayNumber - 1;
		assertTrue(subject.isRewardable(stakePeriodStart));

		stakePeriodStart = todayNumber - 2;
		assertTrue(subject.isRewardable(stakePeriodStart));
	}

	@Test
	void calculatesOnlyOncePerSecond() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		var consensusTime = Instant.ofEpochSecond(12345678L);
		var expectedStakePeriod = LocalDate.ofInstant(consensusTime, ZONE_UTC).toEpochDay();

		given(txnCtx.consensusTime()).willReturn(consensusTime);
		assertEquals(expectedStakePeriod, subject.currentStakePeriod());
		assertEquals(consensusTime.getEpochSecond(), subject.getPrevConsensusSecs());

		final var newConsensusTime = Instant.ofEpochSecond(12345679L);
		given(txnCtx.consensusTime()).willReturn(newConsensusTime);
		expectedStakePeriod = LocalDate.ofInstant(newConsensusTime, ZONE_UTC).toEpochDay();

		assertEquals(expectedStakePeriod, subject.currentStakePeriod());
		assertEquals(newConsensusTime.getEpochSecond(), subject.getPrevConsensusSecs());
	}

	@Test
	void validatesIfStartPeriodIsWithinRange() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		final var instant = Instant.ofEpochSecond(12345678910L);
		given(txnCtx.consensusTime()).willReturn(instant);
		given(networkContext.areRewardsActivated()).willReturn(true);
		final long stakePeriodStart = subject.currentStakePeriod();

		assertTrue(subject.isRewardable(stakePeriodStart - 365));
		assertFalse(subject.isRewardable(-1));
		assertFalse(subject.isRewardable(stakePeriodStart));
	}

	@Test
	void givesEffectivePeriodCorrectly() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		final var delta = 500;
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(12345678910L));

		final var stakePeriod = subject.currentStakePeriod();
		final var period = subject.effectivePeriod(stakePeriod - delta);

		final var expectedEffectivePeriod = LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L),
				ZONE_UTC).toEpochDay();
		assertEquals(expectedEffectivePeriod - 365, period);
		assertEquals(expectedEffectivePeriod - 10, subject.effectivePeriod(stakePeriod - 10));
	}

	private MerkleAccount getResultOf(final Consumer<MerkleAccount> finisher) {
		return getResultOf(finisher, -1, -1);
	}
	private MerkleAccount getResultOf(final Consumer<MerkleAccount> finisher, final long initStakedToMe) {
		return getResultOf(finisher, initStakedToMe, -1);
	}

	private MerkleAccount getResultOf(
			final Consumer<MerkleAccount> finisher,
			final long initStakedToMe,
			final long initStakePeriodStart
	) {
		final var ans = new MerkleAccount();
		ans.setStakedToMe(initStakedToMe);
		ans.setStakePeriodStart(initStakePeriodStart);
		finisher.accept(ans);
		return ans;
	}
	@Test
	void calculatesCurrentStakingPeriodForCustomStakingPeriodProperty() {
		final var instant = Instant.ofEpochSecond(12345L);
		final var expectedPeriod = LocalDate.ofInstant(instant, ZONE_UTC).toEpochDay() / 2;

		given(properties.getLongProperty("staking.periodMins")).willReturn(2880L);
		given(txnCtx.consensusTime()).willReturn(instant);

		final var period = subject.currentStakePeriod();

		assertEquals(expectedPeriod, period);

		given(properties.getLongProperty("staking.periodMins")).willReturn(10L);
		given(txnCtx.consensusTime()).willReturn(instant.plusSeconds(12345L));

		assertEquals(41L, subject.currentStakePeriod());
	}
}
