package com.hedera.services.fees;

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

import com.hederahashgraph.api.proto.java.ExchangeRate;
import org.junit.jupiter.api.Test;

import static com.hedera.services.calc.OverflowCheckingCalc.tinycentsToTinybars;
import static com.hedera.services.fees.ContractStoragePriceTiers.THOUSANDTHS_TO_TINY;
import static com.hedera.services.fees.ContractStoragePriceTiers.cappedAddition;
import static com.hedera.services.fees.ContractStoragePriceTiers.cappedMultiplication;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractStoragePriceTiersTest {
	private static final long M = ContractStoragePriceTiers.TIER_MULTIPLIER;
	private static final long P = ContractStoragePriceTiers.THOUSANDTHS_TO_TINY;
	private static final long DEFAULT_PERIOD = 2592000L;
	private static final ContractStoragePriceTiers CANONICAL_TIERS = ContractStoragePriceTiers.from(
			"10@50,50@100,100@150,200@200,500@250,700@300,1000@350,2000@400,5000@450,10000@500");

	@Test
	void doesntChargeZero() {
		final var degenerate = CANONICAL_TIERS.slotPrice(
				someOtherRate, DEFAULT_PERIOD, 1_000_000, 1, DEFAULT_PERIOD);
		assertEquals(1, degenerate);
	}

	@Test
	void getsExpectedForCanonicalPeriod() {
		final var correctPrice = 100 * THOUSANDTHS_TO_TINY;
		final var expected = tinycentsToTinybars(correctPrice, someRate);

		final var actual = CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, 150_000_000, 1, DEFAULT_PERIOD);

		assertEquals(expected, actual);
	}

	@Test
	void getsExpectedForTwoAtHalfCanonicalPeriod() {
		final var correctPrice = 100 * THOUSANDTHS_TO_TINY;
		final var expected = tinycentsToTinybars(correctPrice, someRate);

		final var actual = CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, 150_000_000, 2, DEFAULT_PERIOD / 2);

		assertEquals(expected / 2 * 2, actual);
	}

	@Test
	void canUseFirstTier() {
		final var correctPrice = 10 * THOUSANDTHS_TO_TINY;
		final var expected = tinycentsToTinybars(correctPrice + 1, someRate);

		final var actual = CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, 1_000_000, 1, DEFAULT_PERIOD + 1);

		assertEquals(expected, actual);
	}

	@Test
	void canUseTopTier() {
		final var correctPrice = 10_000 * THOUSANDTHS_TO_TINY;
		final var partialPrice = correctPrice / 2;
		final var expected = tinycentsToTinybars(correctPrice + partialPrice, someRate);

		final var actual = CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, 500_000_000, 1, 3 * DEFAULT_PERIOD / 2);

		assertEquals(expected, actual);
	}

	@Test
	void parsesValidAsExpected() {
		final var input = "10@50,50@100,100@150,200@200,500@250,700@300,1000@350,2000@400,5000@450,10000@500";
		final var expectedUsageTiers = new long[] {
				50 * M, 100 * M, 150 * M, 200 * M, 250 * M, 300 * M, 350 * M, 400 * M, 450 * M, 500 * M
		};
		final var expectedPrices = new long[] {
				10 * P, 50 * P, 100 * P, 200 * P, 500 * P, 700 * P, 1000 * P, 2000 * P, 5000 * P, 10000 * P
		};
		final var subject = ContractStoragePriceTiers.from(input);

		assertArrayEquals(expectedUsageTiers, subject.usageTiers());
		assertArrayEquals(expectedPrices, subject.prices());
	}

	@Test
	void failsOnDecreasingPrice() {
		final var input = "10@50,9@100";

		assertThrows(IllegalArgumentException.class, () -> ContractStoragePriceTiers.from(input));
	}

	@Test
	void failsOnDecreasingUsage() {
		final var input = "10@50,11@49";

		assertThrows(IllegalArgumentException.class, () -> ContractStoragePriceTiers.from(input));
	}

	@Test
	void failsOnEmpty() {
		assertThrows(IllegalArgumentException.class, () -> ContractStoragePriceTiers.from(""));
	}

	@Test
	void objectMethodsAsExpected() {
		final var a = ContractStoragePriceTiers.from("1@10,5@50,1000@100");
		final var b = ContractStoragePriceTiers.from("1@10,5@50,10@100");
		final var c = ContractStoragePriceTiers.from("1@10,5@50,1000@200");
		final var d = a;
		final var e = ContractStoragePriceTiers.from("1@10,5@50,1000@100");
		final var desired = "ContractStoragePriceTiers{usageTiers=[10000000, 50000000, 100000000], " +
				"prices=[100000, 500000, 100000000]}";

		assertEquals(a, d);
		assertNotEquals(a, null);
		assertNotEquals(a, b);
		assertNotEquals(a, c);
		assertEquals(a, e);
		// and:
		assertEquals(a.hashCode(), e.hashCode());
		// and:
		assertEquals(desired, a.toString());
	}

	@Test
	void cappedMultiplicationWorks() {
		assertEquals(2 * 10, cappedMultiplication(2, 10));
		assertEquals(Long.MAX_VALUE, cappedMultiplication(3, Long.MAX_VALUE / 2));
	}

	@Test
	void cappedAdditionWorks() {
		assertEquals(2 + 10, cappedAddition(2, 10));
		assertEquals(Long.MAX_VALUE, cappedAddition(3, Long.MAX_VALUE - 2));
	}

	private static final ExchangeRate someRate = ExchangeRate.newBuilder()
			.setHbarEquiv(12)
			.setCentEquiv(123)
			.build();
	private static final ExchangeRate someOtherRate = ExchangeRate.newBuilder()
			.setHbarEquiv(1)
			.setCentEquiv(Integer.MAX_VALUE)
			.build();
}
