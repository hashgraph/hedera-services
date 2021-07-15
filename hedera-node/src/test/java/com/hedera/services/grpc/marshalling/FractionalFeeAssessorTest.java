package com.hedera.services.grpc.marshalling;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FractionalFeeAssessorTest {
	private FractionalFeeAssessor subject = new FractionalFeeAssessor();

	@Mock
	private BalanceChangeManager changeManager;

	@Test
	void computesVanillaFine() {
		// expect:
		assertEquals(
				vanillaTriggerAmount / firstDenominator,
				subject.computedFee(vanillaTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
	}

	@Test
	void enforcesMax() {
		// expect:
		assertEquals(
				firstMaxAmountOfFractionalFee,
				subject.computedFee(maxApplicableTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
	}

	@Test
	void enforcesMin() {
		// expect:
		assertEquals(
				firstMinAmountOfFractionalFee,
				subject.computedFee(minApplicableTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
	}

	@Test
	void appliesFeesAsExpected() {
		// setup:
		final var fees = List.of(firstFractionalFee, secondFractionalFee);
		final var firstCollectorChange = BalanceChange.tokenAdjust(
				firstFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
		final var secondCollectorChange = BalanceChange.tokenAdjust(
				secondFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
		// and:

		given(changeManager.changeFor(firstFractionalFeeCollector.asId(), tokenWithFractionalFee))
				.willReturn(firstCollectorChange);
		given(changeManager.changeFor(secondFractionalFeeCollector.asId(), tokenWithFractionalFee))
				.willReturn(secondCollectorChange);

		// when:
		subject.assessAllFractional(vanillaTrigger, fees, changeManager);

		// then:
	}

	@Test
	void handlesEasyCase() {
		// given:
		long reasonable = 1_234_567L;
		long n = 10;
		long d = 9;
		// and:
		final var expected = reasonable * n / d;

		// expect:
		assertEquals(expected, subject.safeFractionMultiply(n, d, reasonable));
	}

	@Test
	void fallsBackToArbitraryPrecisionIfNeeded() {
		// given:
		long huge = Long.MAX_VALUE / 2;
		long n = 10;
		long d = 9;
		// and:
		final var expected = BigInteger.valueOf(huge)
				.multiply(BigInteger.valueOf(n))
				.divide(BigInteger.valueOf(d))
				.longValueExact();

		// expect:
		assertEquals(expected, subject.safeFractionMultiply(n, d, huge));
	}

	@Test
	void propagatesArithmeticExceptionOnOverflow() {
		// given:
		long huge = Long.MAX_VALUE - 1;
		long n = 10;
		long d = 9;

		// expect:
		assertThrows(ArithmeticException.class, () -> subject.safeFractionMultiply(n, d, huge));
	}

	private final Id payer = new Id(0, 1, 2);
	private final long vanillaTriggerAmount = 5000L;
	private final long minApplicableTriggerAmount = 50L;
	private final long maxApplicableTriggerAmount = 50_000L;
	private final long firstMinAmountOfFractionalFee = 1L;
	private final long firstMaxAmountOfFractionalFee = 100L;
	private final long firstNumerator = 1L;
	private final long firstDenominator = 100L;
	private final long secondMinAmountOfFractionalFee = 10L;
	private final long secondMaxAmountOfFractionalFee = 1000L;
	private final long secondNumerator = 1L;
	private final long secondDenominator = 10L;
	private final Id tokenWithFractionalFee = new Id(1, 2, 3);
	private final EntityId firstFractionalFeeCollector = new EntityId(4, 5, 6);
	private final EntityId secondFractionalFeeCollector = new EntityId(5, 6, 7);
	private final FcCustomFee firstFractionalFee = FcCustomFee.fractionalFee(
			firstNumerator,
			firstDenominator,
			firstMinAmountOfFractionalFee,
			firstMaxAmountOfFractionalFee,
			firstFractionalFeeCollector);
	private final FcCustomFee secondFractionalFee = FcCustomFee.fractionalFee(
			secondNumerator,
			secondDenominator,
			secondMinAmountOfFractionalFee,
			secondMaxAmountOfFractionalFee,
			secondFractionalFeeCollector);
	private final BalanceChange vanillaTrigger = BalanceChange.tokenAdjust(
			payer, tokenWithFractionalFee, vanillaTriggerAmount);
}