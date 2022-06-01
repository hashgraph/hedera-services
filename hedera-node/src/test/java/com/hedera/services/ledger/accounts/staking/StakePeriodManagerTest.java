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
import com.hedera.services.state.merkle.MerkleNetworkContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.zoneUTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StakePeriodManagerTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private MerkleNetworkContext networkContext;

	private StakePeriodManager subject;

	@BeforeEach
	public void setUp() {
		subject = new StakePeriodManager(txnCtx, () -> networkContext);
	}

	@Test
	void givesCurrentStakePeriod() {
		final var instant = Instant.ofEpochSecond(12345L);
		given(txnCtx.consensusTime()).willReturn(instant);
		final var period = subject.currentStakePeriod();
		final var expectedPeriod = LocalDate.ofInstant(instant, zoneUTC).toEpochDay();
		assertEquals(expectedPeriod, period);

		var latesteRewardable = subject.firstNonRewardableStakePeriod();
		assertEquals(Long.MIN_VALUE, latesteRewardable);

		given(networkContext.areRewardsActivated()).willReturn(true);
		latesteRewardable = subject.firstNonRewardableStakePeriod();
		assertEquals(expectedPeriod - 1, latesteRewardable);
	}

	@Test
	void calculatesIfRewardShouldBeEarned() {
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
		var consensusTime = Instant.ofEpochSecond(12345678L);
		var expectedStakePeriod = LocalDate.ofInstant(consensusTime, zoneUTC).toEpochDay();

		given(txnCtx.consensusTime()).willReturn(consensusTime);
		assertEquals(expectedStakePeriod, subject.currentStakePeriod());
		assertEquals(consensusTime.getEpochSecond(), subject.getPrevConsensusSecs());

		final var newConsensusTime = Instant.ofEpochSecond(12345679L);
		given(txnCtx.consensusTime()).willReturn(newConsensusTime);
		expectedStakePeriod = LocalDate.ofInstant(newConsensusTime, zoneUTC).toEpochDay();

		assertEquals(expectedStakePeriod, subject.currentStakePeriod());
		assertEquals(newConsensusTime.getEpochSecond(), subject.getPrevConsensusSecs());
	}

	@Test
	void validatesIfStartPeriodIsWithinRange() {
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
		final var delta = 500;
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(12345678910L));

		final var stakePeriod = subject.currentStakePeriod();
		final var period = subject.effectivePeriod(stakePeriod - delta);

		final var expectedEffectivePeriod = LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L),
				zoneUTC).toEpochDay();
		assertEquals(expectedEffectivePeriod - 365, period);
		assertEquals(expectedEffectivePeriod - 10, subject.effectivePeriod(stakePeriod - 10));
	}

}
