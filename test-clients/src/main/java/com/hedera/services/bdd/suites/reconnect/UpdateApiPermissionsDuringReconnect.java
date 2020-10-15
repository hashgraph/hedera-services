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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;

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
		String sender = "0.0.1001";
		String receiver = "0.0.1002";
		return defaultHapiSpec("updateApiPermissionsDuringReconnect")
				.given()
				.when()
				.then(
						UtilVerbs.sleepFor(Duration.ofMinutes(6).toMillis()),
						getFileContents(API_PERMISSIONS).logged().setNode("0.0.6")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
