package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.isWithinRange;
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

		var latesteRewardable = subject.latestRewardableStakePeriodStart();
		assertEquals(Long.MIN_VALUE, latesteRewardable);

		given(networkContext.areRewardsActivated()).willReturn(true);
		latesteRewardable = subject.latestRewardableStakePeriodStart();
		assertEquals(expectedPeriod - 1, latesteRewardable);
	}

	@Test
	void calculatesIfRewardShouldBeEarned() {
		final var instant = Instant.ofEpochSecond(123456789L);
		given(txnCtx.consensusTime()).willReturn(instant);
		final var todayNumber = subject.currentStakePeriod() - 1;

		var stakePeriodStart = todayNumber - 366;
		assertTrue(isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = -1;
		assertFalse(isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 365;
		assertTrue(isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 1;
		assertTrue(isWithinRange(stakePeriodStart, todayNumber));

		stakePeriodStart = todayNumber - 2;
		assertTrue(isWithinRange(stakePeriodStart, todayNumber));
	}
}
