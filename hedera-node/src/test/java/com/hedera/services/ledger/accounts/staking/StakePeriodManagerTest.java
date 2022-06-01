package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.ZONE_UTC;
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
	@Mock
	private PropertySource properties;

	private StakePeriodManager subject;

	@BeforeEach
	public void setUp() {
		given(properties.getLongProperty("staking.periodMins")).willReturn(1440L);
		subject = new StakePeriodManager(txnCtx, () -> networkContext, properties);
	}

	@Test
	void givesCurrentStakePeriod() {
		final var instant = Instant.ofEpochSecond(12345L);
		given(txnCtx.consensusTime()).willReturn(instant);
		final var period = subject.currentStakePeriod();
		final var expectedPeriod = LocalDate.ofInstant(instant, ZONE_UTC).toEpochDay();
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
				ZONE_UTC).toEpochDay();
		assertEquals(expectedEffectivePeriod - 365, period);
		assertEquals(expectedEffectivePeriod - 10, subject.effectivePeriod(stakePeriod - 10));
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
