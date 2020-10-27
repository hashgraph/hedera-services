package com.hedera.services.bdd.suites.reconnect;

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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;

public class UpdateApiPermissionsDuringReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpdateApiPermissionsDuringReconnect.class);

	public static void main(String... args) {
		new UpdateApiPermissionsDuringReconnect().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				updateApiPermissionsDuringReconnect()
		);
	}

	private HapiApiSpec updateApiPermissionsDuringReconnect() {
		final String fileInfoRegistry = "apiPermissionsReconnect";
		return defaultHapiSpec("updateApiPermissionsDuringReconnect")
				.given(
						UtilVerbs.sleepFor(Duration.ofSeconds(60).toMillis()),
						getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode()
				)
				.when(
						fileUpdate(API_PERMISSIONS)
						.overridingProps(Map.of("updateFile", "1-1011"))
						.payingWith(MASTER)
						.logged(),
						getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode()
				)
				.then(

						UtilVerbs.sleepFor(Duration.ofMinutes(5).toMillis()),
						getFileContents(API_PERMISSIONS)
								.logged()
								.setNode("0.0.3")
								.payingWith(MASTER)
								.saveToRegistry(fileInfoRegistry),
						getFileContents(API_PERMISSIONS)
								.logged()
								.setNode("0.0.6")
								.payingWith(MASTER)
								.hasContents(fileInfoRegistry)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
