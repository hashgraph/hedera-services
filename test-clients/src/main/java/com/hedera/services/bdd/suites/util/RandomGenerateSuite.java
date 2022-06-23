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
				generatesRandomBytesIfNoRangeOrZeroRangeGiven()
//				failsInPreCheckForNegativerange(),
//				returnsNumberWithinRange(),
//				usdFeeAsExpected()
		);
	}

	private HapiApiSpec generatesRandomBytesIfNoRangeOrZeroRangeGiven() {
		return defaultHapiSpec("createAnAccountWithStakingFields")
				.given(
						cryptoCreate("bob").balance(ONE_HUNDRED_HBARS),

						randomGenerate()
								.payingWith("bob")
								.via("randomGenerate")
								.logged(),
						getTxnRecord("randomGenerate")
								.hasOnlyPseudoRandomBitString()
								.logged()
				).when(
						randomGenerate(10)
								.payingWith("bob")
								.via("randomGenerateWithRange")
								.logged(),
						getTxnRecord("randomGenerateWithRange")
								.hasOnlyPseudoRandomNumberInRange(10)
								.logged()
				).then(
						randomGenerate(0)
								.payingWith("bob")
								.via("randomGenerateWithZeroRange")
								.logged(),
						getTxnRecord("randomGenerateWithZeroRange")
								.hasOnlyPseudoRandomBitString()
								.logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


