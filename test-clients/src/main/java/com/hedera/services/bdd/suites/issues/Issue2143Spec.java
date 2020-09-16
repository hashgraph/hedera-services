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

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;

import java.util.List;
import java.util.Map;

public class Issue2143Spec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue2143Spec.class);

	public static void main(String... args) {
		new Issue2143Spec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						account55ControlCanUpdatePropertiesAndPermissions(),
						account57ControlCanUpdatePropertiesAndPermissions(),
				}
		);
	}

	private HapiApiSpec account55ControlCanUpdatePropertiesAndPermissions() {
		return defaultHapiSpec("Account55ControlCanUpdatePropertiesAndPermissions")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000L))
				).when(
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of("simpletransferTps", "100"))
								.payingWith(ADDRESS_BOOK_CONTROL),
						fileUpdate(API_PERMISSIONS)
								.overridingProps(Map.of("createFile", "0-100"))
								.payingWith(ADDRESS_BOOK_CONTROL)
				).then(
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of("simpletransferTps", "0"))
								.payingWith(ADDRESS_BOOK_CONTROL),
						fileUpdate(API_PERMISSIONS)
								.overridingProps(Map.of("createFile", "0-*"))
								.payingWith(ADDRESS_BOOK_CONTROL)
				);
	}

	private HapiApiSpec account57ControlCanUpdatePropertiesAndPermissions() {
		return defaultHapiSpec("Account57ControlCanUpdatePropertiesAndPermissions")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, 1_000_000_000L))
				).when(
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of("simpletransferTps", "100"))
								.payingWith(EXCHANGE_RATE_CONTROL),
						fileUpdate(API_PERMISSIONS)
								.overridingProps(Map.of("createFile", "0-100"))
								.payingWith(EXCHANGE_RATE_CONTROL)
				).then(
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of("simpletransferTps", "0"))
								.payingWith(EXCHANGE_RATE_CONTROL),
						fileUpdate(API_PERMISSIONS)
								.overridingProps(Map.of("createFile", "0-*"))
								.payingWith(EXCHANGE_RATE_CONTROL)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
