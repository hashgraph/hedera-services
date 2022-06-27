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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.randomGenerate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class RandomGenerateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RandomGenerateSuite.class);

	public static void main(String... args) {
		new RandomGenerateSuite().runSuiteSync();
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
				usdFeeAsExpected()
		);
	}

	private HapiApiSpec usdFeeAsExpected() {
		double baseFee = 0.001;
		double plusRangeFee = 0.0010010316;

		final var baseTxn = "randomGenerate";
		final var plusRangeTxn = "randomGenerateWithRange";

		return defaultHapiSpec("usdFeeAsExpected")
				.given(
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),

						randomGenerate()
								.payingWith("bob")
								.via(baseTxn)
								.blankMemo()
								.logged(),
						getTxnRecord(baseTxn)
								.hasOnlyPseudoRandomBytes()
								.logged(),
						validateChargedUsd(baseTxn, baseFee)
				).when(
						randomGenerate(10)
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
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),

						randomGenerate(-10)
								.payingWith("bob")
								.blankMemo()
								.hasPrecheck(INVALID_RANDOM_GENERATE_RANGE)
								.logged(),
						randomGenerate(0)
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
						// running hash is set
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),
						// n-1 running hash and running has set
						randomGenerate()
								.payingWith("bob")
								.blankMemo()
								.via("randomGenerate")
								.logged(),
						// n-1, n-2 running hash and running has set
						getTxnRecord("randomGenerate")
								.hasNoPseudoRandomData() // When running this suite in CI this check will fail since it
								// already has n-3 running hash
								.logged(),
						// n-1, n-2, n-3 running hash and running has set
						randomGenerate(10)
								.payingWith("bob")
								.via("randomGenerateWithRange1")
								.blankMemo()
								.logged(),
						getTxnRecord("randomGenerateWithRange1")
								.hasNoPseudoRandomData() // When running this suite in CI this check will fail since it
								// already has n-3 running hash
								.logged(),
						randomGenerate()
								.payingWith("bob")
								.via("randomGenerate2")
								.blankMemo()
								.logged()
				).when(
						// should have pseudo random data
						randomGenerate(10)
								.payingWith("bob")
								.via("randomGenerateWithRange")
								.blankMemo()
								.logged(),
						getTxnRecord("randomGenerateWithRange")
								.hasOnlyPseudoRandomNumberInRange(10)
								.logged()
				).then(
						randomGenerate()
								.payingWith("bob")
								.via("randomGenerateWithoutRange")
								.blankMemo()
								.logged(),
						getTxnRecord("randomGenerateWithoutRange")
								.hasOnlyPseudoRandomBytes()
								.logged(),

						randomGenerate(0)
								.payingWith("bob")
								.via("randomGenerateWithZeroRange")
								.blankMemo()
								.logged(),
						getTxnRecord("randomGenerateWithZeroRange")
								.hasOnlyPseudoRandomBytes()
								.logged(),

						randomGenerate()
								.range(Integer.MAX_VALUE)
								.payingWith("bob")
								.via("randomGenerateWithMaxRange")
								.blankMemo()
								.logged(),
						getTxnRecord("randomGenerateWithMaxRange")
								.hasOnlyPseudoRandomNumberInRange(Integer.MAX_VALUE)
								.logged(),

						randomGenerate()
								.range(Integer.MIN_VALUE)
								.blankMemo()
								.payingWith("bob")
								.hasPrecheck(INVALID_RANDOM_GENERATE_RANGE)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


