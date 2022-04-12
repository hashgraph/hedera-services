package com.hedera.services.bdd.suites.throttling;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;

public class ResetThrottleSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(
			com.hedera.services.bdd.suites.throttling.ResetThrottleSuite.class);

	public static void main(String... args) {
		new ResetThrottleSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				resetThrottle()
		);
	}

	protected HapiApiSpec resetThrottle() {
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		return defaultHapiSpec("RunCryptoTransfersWithAutoAccounts")
				.given(
				).when(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(GENESIS)
								.contents(defaultThrottles.toByteArray())
				).then(
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


