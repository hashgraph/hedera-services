package com.hedera.services.bdd.suites.issues;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;

import java.util.List;

public class Issue1648Suite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue1648Suite.class);

	public static void main(String... args) {
		new Issue1648Suite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				recordStorageFeeIncreasesWithNumTransfers()
		);
	}

	public static HapiApiSpec recordStorageFeeIncreasesWithNumTransfers() {
		return defaultHapiSpec("RecordStorageFeeIncreasesWithNumTransfers")
				.given(
						UtilVerbs.inParallel(
								cryptoCreate("A"),
								cryptoCreate("B"),
								cryptoCreate("C"),
								cryptoCreate("D")
						),
						UtilVerbs.inParallel(
								cryptoTransfer(
										tinyBarsFromTo("A", "B", 1L)
								).via("txn1"),
								cryptoTransfer(
										tinyBarsFromTo("A", "B", 1L),
										tinyBarsFromTo("C", "D", 1L)
								).via("txn2")
						)
				).when(
						UtilVerbs.recordFeeAmount("txn1", "feeForOne"),
						UtilVerbs.recordFeeAmount("txn2", "feeForTwo")
				).then(
						UtilVerbs.assertionsHold((spec, assertLog) -> {
							long feeForOne = spec.registry().getAmount("feeForOne");
							long feeForTwo = spec.registry().getAmount("feeForTwo");
							assertLog.info("[Record storage] fee for one transfer : " + feeForOne);
							assertLog.info("[Record storage] fee for two transfers: " + feeForTwo);
							Assert.assertEquals(-1, Long.compare(feeForOne, feeForTwo));
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
