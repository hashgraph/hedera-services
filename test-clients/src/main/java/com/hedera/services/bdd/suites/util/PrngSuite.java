package com.hedera.services.bdd.suites.util;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiPrng;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class PrngSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(PrngSuite.class);

	public static void main(String... args) {
		new PrngSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return List.of(
				happyPathWorksForRangeAndBitString(),
				failsInPreCheckForNegativeRange(),
				usdFeeAsExpected(),
				featureFlagWorks()
		);
	}

	private HapiApiSpec featureFlagWorks() {
		return defaultHapiSpec("featureFlagWorks")
				.given(
						overridingAllOf(Map.of(
								"prng.isEnabled", "false"
						)),
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
						TxnVerbs.hapiPrng()
								.payingWith("bob")
								.via("baseTxn")
								.blankMemo()
								.logged(),
						getTxnRecord("baseTxn")
								.hasNoPseudoRandomData()
								.logged()
				).when(
						hapiPrng(10)
								.payingWith("bob")
								.via("plusRangeTxn")
								.blankMemo()
								.logged(),
						getTxnRecord("plusRangeTxn")
								.hasNoPseudoRandomData()
								.logged()
				).then(
				);
	}

	private HapiApiSpec usdFeeAsExpected() {
		double baseFee = 0.001;
		double plusRangeFee = 0.0010010316;

		final var baseTxn = "prng";
		final var plusRangeTxn = "prngWithRange";

		return defaultHapiSpec("usdFeeAsExpected")
				.given(
						overridingAllOf(Map.of(
								"prng.isEnabled", "true"
						)),
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),

						TxnVerbs.hapiPrng()
								.payingWith("bob")
								.via(baseTxn)
								.blankMemo()
								.logged(),
						getTxnRecord(baseTxn)
								.hasOnlyPseudoRandomBytes()
								.logged(),
						validateChargedUsd(baseTxn, baseFee)
				).when(
						hapiPrng(10)
								.payingWith("bob")
								.via(plusRangeTxn)
								.blankMemo()
								.logged(),
						getTxnRecord(plusRangeTxn)
								.hasOnlyPseudoRandomNumberInRange(10)
								.logged(),
						validateChargedUsdWithin(plusRangeTxn, plusRangeFee, 0.5)
				).then(
				);
	}

	private HapiApiSpec failsInPreCheckForNegativeRange() {
		return defaultHapiSpec("failsInPreCheckForNegativeRange")
				.given(
						overridingAllOf(Map.of(
								"prng.isEnabled", "true"
						)),
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
						hapiPrng(-10)
								.payingWith("bob")
								.blankMemo()
								.hasPrecheck(INVALID_PRNG_RANGE)
								.logged(),
						hapiPrng(0)
								.payingWith("bob")
								.blankMemo()
								.hasPrecheck(OK)
								.logged()
				).when(
				).then(
				);
	}

	private HapiApiSpec happyPathWorksForRangeAndBitString() {
		return defaultHapiSpec("happyPathWorksForRangeAndBitString")
				.given(
						overridingAllOf(Map.of(
								"prng.isEnabled", "true"
						)),
						// running hash is set
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
						// n-1 running hash and running has set
						TxnVerbs.hapiPrng()
								.payingWith("bob")
								.blankMemo()
								.via("prng")
								.logged(),
						// n-1, n-2 running hash and running has set
						getTxnRecord("prng")
								.hasNoPseudoRandomData() // When running this suite in CI this check will fail since it
								// already has n-3 running hash
								.logged(),
						// n-1, n-2, n-3 running hash and running has set
						hapiPrng(10)
								.payingWith("bob")
								.via("prngWithRange1")
								.blankMemo()
								.logged(),
						getTxnRecord("prngWithRange1")
								.hasNoPseudoRandomData() // When running this suite in CI this check will fail since it
								// already has n-3 running hash
								.logged(),
						TxnVerbs.hapiPrng()
								.payingWith("bob")
								.via("prng2")
								.blankMemo()
								.logged()
				).when(
						// should have pseudo random data
						hapiPrng(10)
								.payingWith("bob")
								.via("prngWithRange")
								.blankMemo()
								.logged(),
						getTxnRecord("prngWithRange")
								.hasOnlyPseudoRandomNumberInRange(10)
								.logged()
				).then(
						TxnVerbs.hapiPrng()
								.payingWith("bob")
								.via("prngWithoutRange")
								.blankMemo()
								.logged(),
						getTxnRecord("prngWithoutRange")
								.hasOnlyPseudoRandomBytes()
								.logged(),

						hapiPrng(0)
								.payingWith("bob")
								.via("prngWithZeroRange")
								.blankMemo()
								.logged(),
						getTxnRecord("prngWithZeroRange")
								.hasOnlyPseudoRandomBytes()
								.logged(),

						TxnVerbs.hapiPrng()
								.range(Integer.MAX_VALUE)
								.payingWith("bob")
								.via("prngWithMaxRange")
								.blankMemo()
								.logged(),
						getTxnRecord("prngWithMaxRange")
								.hasOnlyPseudoRandomNumberInRange(Integer.MAX_VALUE)
								.logged(),

						TxnVerbs.hapiPrng()
								.range(Integer.MIN_VALUE)
								.blankMemo()
								.payingWith("bob")
								.hasPrecheck(INVALID_PRNG_RANGE)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


