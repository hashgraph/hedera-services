package com.hedera.services.bdd.suites.reconnect;

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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;

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
						sleepFor(Duration.ofSeconds(25).toMillis()),
						getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode()
				)
				.when(
						fileUpdate(API_PERMISSIONS)
								.overridingProps(Map.of("updateFile", "1-1011"))
								.payingWith(SYSTEM_ADMIN)
								.logged(),
						getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode()
				)
				.then(
						withLiveNode("0.0.6")
								.within(5 * 60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),
						UtilVerbs.sleepFor(30 * 1000),
						withLiveNode("0.0.6")
								.within(5 * 60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),
						getFileContents(API_PERMISSIONS)
								.logged()
								.setNode("0.0.3")
								.payingWith(SYSTEM_ADMIN)
								.saveToRegistry(fileInfoRegistry),
						getFileContents(API_PERMISSIONS)
								.logged()
								.setNode("0.0.6")
								.payingWith(SYSTEM_ADMIN)
								.hasContents(fileInfoRegistry)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
