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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

import static com.hedera.services.calc.OverflowCheckingCalc.tinycentsToTinybars;
import static com.hedera.services.fees.ContractStoragePriceTiers.THOUSANDTHS_TO_TINY;
import static com.hedera.services.fees.ContractStoragePriceTiers.cappedAddition;
import static com.hedera.services.fees.ContractStoragePriceTiers.cappedMultiplication;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractStoragePriceTiersTest {
	private static final long M = ContractStoragePriceTiers.TIER_MULTIPLIER;
	private static final long P = ContractStoragePriceTiers.THOUSANDTHS_TO_TINY;
	private static final long DEFAULT_PERIOD = 2592000L;
	private static final ContractStoragePriceTiers CANONICAL_TIERS = ContractStoragePriceTiers.from(
			"10@50M,50@100M,100@150M,200@200M,500@250M,700@300M,1000@350M,2000@400M,5000@450M,10000@500M");

	@Test
	void chargesBytecodeAsAtLeastOneKvPair() {
		final var codeLen = 63;
		final var code = Bytes.wrap(new byte[codeLen]);
		final var numKvPairs = 77_777_777L;
		final var reqLifetime = DEFAULT_PERIOD / 4 * 5;

		final var codePrice = CANONICAL_TIERS.codePrice(
				someRate, DEFAULT_PERIOD, numKvPairs, code, reqLifetime);
		final var expectedPrice = CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, numKvPairs, 1, reqLifetime);

		assertEquals(expectedPrice, codePrice);
	}

	@Test
	void chargesBytecodeAsMultipleOfKvPairs() {
		final var codeLen = 1024;
		final var code = Bytes.wrap(new byte[codeLen]);
		final var numKvPairs = 77_777_777L;
		final var reqLifetime = DEFAULT_PERIOD / 4 * 5;

		final var codePrice = CANONICAL_TIERS.codePrice(
				someRate, DEFAULT_PERIOD, numKvPairs, code, reqLifetime);
		final var expectedPrice = CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, numKvPairs, codeLen / 64, reqLifetime);

		assertEquals(expectedPrice, codePrice);
	}

	@Test
	void doesntChargeZero() {
		final var degenerate = CANONICAL_TIERS.slotPrice(
				someOtherRate, DEFAULT_PERIOD, 1_000_000, 1, DEFAULT_PERIOD);
		assertEquals(1, degenerate);
	}

	@Test
	void failsOnZeroSlotsRequested() {
		assertThrows(IllegalArgumentException.class, () -> CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, 666, 0, DEFAULT_PERIOD));
	}

	@Test
	void failsOnNegativeLifetime() {
		assertThrows(IllegalArgumentException.class, () -> CANONICAL_TIERS.slotPrice(
				someRate, DEFAULT_PERIOD, 666, 1, -DEFAULT_PERIOD));
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
		final var input = "10@50M,50@100M,100@150M,200@200M,500@250M,700@300M,1000@350M,2000@400M,5000@450M,10000@500M";
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
		final var desired = "ContractStoragePriceTiers{usageTiers=[10, 50, 100], " +
				"prices=[100000, 500000, 100000000]}";

		assertEquals(a, d);
		assertNotNull(a);
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
