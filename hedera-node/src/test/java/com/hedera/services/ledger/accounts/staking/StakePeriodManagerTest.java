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
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.utils.Units;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.ZONE_UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StakePeriodManagerTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private PropertySource properties;

	private StakePeriodManager subject;

	@Test
	void doesntUpdatePendingRewardsIfNothingOffered() {
		givenAgnosticManager();
		final var account = MerkleAccountFactory.newAccount().get();
		subject.updatePendingRewardsGiven(-1, -1, -1, -1, account);
		verifyNoInteractions(networkCtx);
	}

	@Test
	void updatesPendingRewardsIfSomethingOffered() {
		givenAgnosticManager();
		final var account = MerkleAccountFactory.newAccount().get();
		subject.updatePendingRewardsGiven(123, -1, -1, -1, account);
		verify(networkCtx).decreasePendingRewards(123);
	}

	@Test
	void updatesBalanceAtStartOfLastRewardedPeriodIfOffered() {
		givenAgnosticManager();
		final var account = MerkleAccountFactory.newAccount().get();
		subject.updatePendingRewardsGiven(-1, -1, -1, 123, account);
		assertEquals(123, account.getBalanceAtStartOfLastRewardedPeriod());
	}

	@Test
	void stakePeriodStartForProdSettingIsMidnightOfUtcCalendarDay() {
		givenProdManager();

		final var then = Instant.parse("2021-06-07T23:59:58.369613Z");
		final var dateThen = LocalDate.ofInstant(then, ZONE_UTC);
		final var prodPeriod = dateThen.toEpochDay();
		final var midnight = dateThen.atStartOfDay().toEpochSecond(ZoneOffset.UTC);

		assertEquals(midnight, subject.epochSecondAtStartOfPeriod(prodPeriod));
	}

	@Test
	void stakePeriodStartForNonProdSettingIsPeriodTimesSeconds() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(2L);
		subject = new StakePeriodManager(txnCtx, () -> networkCtx, properties);

		final var somePeriod = 1_234_567L;

		assertEquals(somePeriod * 2 * Units.MINUTES_TO_SECONDS, subject.epochSecondAtStartOfPeriod(somePeriod));
	}

	@Test
	void noPeriodStartChangeIfNotStakingToANode() {
		givenAgnosticManager();
		assertEquals(-1, subject.startUpdateFor(0, 0, false, false));
	}

	@Test
	void resetToCurrentPeriodIfStakeMetaChanges() {
		givenProdManager();
		// UTC day 14
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(1_234_567));

		assertEquals(14, subject.startUpdateFor(-1, -1, false, true));
	}

	@Test
	void noStartPeriodChangeIfStakeMetaUntouched() {
		givenProdManager();

		assertEquals(-1, subject.startUpdateFor(-1, -1, false, false));
	}

	@Test
	void stakePeriodIsCurrentIfNowStakingToNode() {
		givenProdManager();
		// UTC day 14
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(1_234_567));

		assertEquals(14, subject.startUpdateFor(0, -1, false, true));
	}

	@Test
	void resetsToPreviousPeriodIfRewarded() {
		givenProdManager();
		// UTC day 14
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(1_234_567));

		assertEquals(13, subject.startUpdateFor(-1, -1, true, true));
	}

	@Test
	void givesCurrentStakePeriod() {
		givenProdManager();
		final var instant = Instant.ofEpochSecond(12345L);
		given(txnCtx.consensusTime()).willReturn(instant);
		final var period = subject.currentStakePeriod();
		final var expectedPeriod = LocalDate.ofInstant(instant, ZONE_UTC).toEpochDay();
		assertEquals(expectedPeriod, period);

		var firstNonRewardable = subject.firstNonRewardableStakePeriod();
		assertEquals(Long.MIN_VALUE, firstNonRewardable);

		given(networkCtx.areRewardsActivated()).willReturn(true);
		firstNonRewardable = subject.firstNonRewardableStakePeriod();
		assertEquals(expectedPeriod - 1, firstNonRewardable);
	}

	@Test
	void estimatesBasedOnInstantNowForNonProdProperty() {
		givenDevManager();
		final var approx = Instant.now().getEpochSecond() / 60;
		assertTrue(Math.abs(approx - subject.estimatedCurrentStakePeriod()) <= 1);
	}

	@Test
	void calculatesIfRewardShouldBeEarned() {
		givenProdManager();
		final var instant = Instant.ofEpochSecond(123456789L);
		given(txnCtx.consensusTime()).willReturn(instant);
		final var todayNumber = subject.currentStakePeriod() - 1;
		given(networkCtx.areRewardsActivated()).willReturn(true);

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
		givenProdManager();
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
		givenProdManager();
		final var instant = Instant.ofEpochSecond(12345678910L);
		given(txnCtx.consensusTime()).willReturn(instant);
		given(networkCtx.areRewardsActivated()).willReturn(true);
		final long stakePeriodStart = subject.currentStakePeriod();

		assertTrue(subject.isRewardable(stakePeriodStart - 365));
		assertFalse(subject.isRewardable(-1));
		assertFalse(subject.isRewardable(stakePeriodStart));
	}

	@Test
	void givesEffectivePeriodCorrectly() {
		givenProdManager();
		final var delta = 500;
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(12345678910L));

		final var stakePeriod = subject.currentStakePeriod();
		final var period = subject.effectivePeriod(stakePeriod - delta);

		final var expectedEffectivePeriod = LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L),
				ZONE_UTC).toEpochDay();
		assertEquals(expectedEffectivePeriod - 365, period);
		assertEquals(expectedEffectivePeriod - 10, subject.effectivePeriod(stakePeriod - 10));
	}

	@Test
	void calculatesCurrentStakingPeriodForCustomStakingPeriodProperty() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(2880L);
		subject = new StakePeriodManager(txnCtx, () -> networkCtx, properties);
		final var instant = Instant.ofEpochSecond(12345L);
		final var expectedPeriod = LocalDate.ofInstant(instant, ZONE_UTC).toEpochDay() / 2;
		given(txnCtx.consensusTime()).willReturn(instant);
		final var period = subject.currentStakePeriod();
		assertEquals(expectedPeriod, period);

		given(properties.getLongProperty("staking.periodMins")).willReturn(10L);
		subject = new StakePeriodManager(txnCtx, () -> networkCtx, properties);
		given(txnCtx.consensusTime()).willReturn(instant.plusSeconds(12345L));
		assertEquals(41L, subject.currentStakePeriod());
	}

	private void givenAgnosticManager() {
		subject = new StakePeriodManager(txnCtx, () -> networkCtx, properties);
	}

	private void givenProdManager() {
		given(properties.getIntProperty("staking.rewardHistory.numStoredPeriods")).willReturn(365);
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		subject = new StakePeriodManager(txnCtx, () -> networkCtx, properties);
	}

	private void givenDevManager() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1L);
		subject = new StakePeriodManager(txnCtx, () -> networkCtx, properties);
	}
}
