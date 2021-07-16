package com.hedera.services.grpc.marshalling;

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

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FractionalFeeAssessorTest {
	private final List<FcAssessedCustomFee> accumulator = new ArrayList<>();

	private FractionalFeeAssessor subject = new FractionalFeeAssessor();

	@Mock
	private BalanceChangeManager changeManager;

	@Test
	void appliesFeesAsExpected() {
		// setup:
		final var fees = List.of(firstFractionalFee, secondFractionalFee);
		final var firstCollectorChange = BalanceChange.tokenAdjust(
				firstFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
		final var secondCollectorChange = BalanceChange.tokenAdjust(
				secondFractionalFeeCollector.asId(), tokenWithFractionalFee, 0L);
		// and:
		final var firstExpectedFee =
				subject.amountOwedGiven(vanillaTriggerAmount, firstFractionalFee.getFractionalFeeSpec());
		final var secondExpectedFee =
				subject.amountOwedGiven(vanillaTriggerAmount, secondFractionalFee.getFractionalFeeSpec());
		final var totalExpectedFees = firstExpectedFee + secondExpectedFee;
		// and:
		final var expFirstAssess = new FcAssessedCustomFee(
				firstFractionalFeeCollector,
				tokenWithFractionalFee.asEntityId(),
				firstExpectedFee);
		final var expSecondAssess = new FcAssessedCustomFee(
				secondFractionalFeeCollector,
				tokenWithFractionalFee.asEntityId(),
				secondExpectedFee);

		given(changeManager.changeFor(firstFractionalFeeCollector.asId(), tokenWithFractionalFee))
				.willReturn(firstCollectorChange);
		given(changeManager.changeFor(secondFractionalFeeCollector.asId(), tokenWithFractionalFee))
				.willReturn(secondCollectorChange);

		// when:
		final var result =
				subject.assessAllFractional(vanillaTrigger, fees, changeManager, accumulator);

		// then:
		assertEquals(OK, result);
		assertEquals(-vanillaTriggerAmount + totalExpectedFees, vanillaTrigger.units());
		assertEquals(firstExpectedFee, firstCollectorChange.units());
		assertEquals(secondExpectedFee, secondCollectorChange.units());
		// and:
		assertEquals(2, accumulator.size());
		assertEquals(expFirstAssess, accumulator.get(0));
		assertEquals(expSecondAssess, accumulator.get(1));
	}

	@Test
	void cannotOverflowWithCrazyFraction() {
		// setup:
		final var fees = List.of(nonsenseFee, secondFractionalFee);

		// when:
		final var result =
				subject.assessAllFractional(vanillaTrigger, fees, changeManager, accumulator);

		// then:
		assertEquals(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE, result);
	}

	@Test
	void failsWithInsufficientBalanceWhenAppropos() {
		// setup:
		final var fees = List.of(firstFractionalFee, secondFractionalFee);

		// when:
		final var result =
				subject.assessAllFractional(wildlyInsufficientChange, fees, changeManager, accumulator);

		// then:
		assertEquals(INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE, result);
	}

	@Test
	void failsFastOnPositiveAdjustment() {
		// given:
		vanillaTrigger.adjustUnits(Long.MAX_VALUE);

		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> subject.assessAllFractional(vanillaTrigger, List.of(), changeManager, accumulator));
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

	@Test
	void computesVanillaFine() {
		// expect:
		assertEquals(
				vanillaTriggerAmount / firstDenominator,
				subject.amountOwedGiven(vanillaTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
	}

	@Test
	void enforcesMax() {
		// expect:
		assertEquals(
				firstMaxAmountOfFractionalFee,
				subject.amountOwedGiven(maxApplicableTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
	}

	@Test
	void enforcesMin() {
		// expect:
		assertEquals(
				firstMinAmountOfFractionalFee,
				subject.amountOwedGiven(minApplicableTriggerAmount, firstFractionalFee.getFractionalFeeSpec()));
	}

	private final Id payer = new Id(0, 1, 2);
	private final long vanillaTriggerAmount = 5000L;
	private final long minApplicableTriggerAmount = 50L;
	private final long maxApplicableTriggerAmount = 50_000L;
	private final long firstMinAmountOfFractionalFee = 2L;
	private final long firstMaxAmountOfFractionalFee = 100L;
	private final long firstNumerator = 1L;
	private final long firstDenominator = 100L;
	private final long secondMinAmountOfFractionalFee = 10L;
	private final long secondMaxAmountOfFractionalFee = 1000L;
	private final long secondNumerator = 1L;
	private final long secondDenominator = 10L;
	private final long nonsenseNumerator = Long.MAX_VALUE;
	private final long nonsenseDenominator = 1L;
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
	private final FcCustomFee nonsenseFee = FcCustomFee.fractionalFee(
			nonsenseNumerator,
			nonsenseDenominator,
			0,
			0,
			secondFractionalFeeCollector);
	private final BalanceChange vanillaTrigger = BalanceChange.tokenAdjust(
			payer, tokenWithFractionalFee, -vanillaTriggerAmount);
	private final BalanceChange wildlyInsufficientChange = BalanceChange.tokenAdjust(
			payer, tokenWithFractionalFee, -1);
}
