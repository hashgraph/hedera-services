package com.hedera.services.bdd.suites.regression;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.checkPersistentEntities;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

/**
 * This restart test uses the following named persistent entities:
 *
 * ACCOUNTS
 *   - sender (balance = 1tℏ)
 *   - receiver (balance = 99tℏ, receiverSigRequired = true)
 *
 * SCHEDULES
 * 	 - pendingXfer (1tℏ from sender to receiver; has sender sig only)
 */
public class RestartWithScheduledEntities extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RestartWithScheduledEntities.class);

	private static final String ENTITIES_DIR = "src/main/resource/jrs/entities/RestartWithScheduledEntities";

	private static final String SENDER = "sender";
	private static final String RECEIVER = "receiver";
	private static final String PENDING_XFER = "pendingXfer";

	public static void main(String... args) {
		var hero = new RestartWithScheduledEntities();

		hero.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						scheduleFocusedJrsRestartSpec(),
				}
		);
	}

	private HapiApiSpec scheduleFocusedJrsRestartSpec() {

		return customHapiSpec("ScheduleFocusedJrsRestartSpec")
				.withProperties(Map.of(
						"persistentEntities.dir.path", ENTITIES_DIR
				)).given(
						checkPersistentEntities()
				).when().then(
						withOpContext((spec, opLog) -> {
							boolean isPostRestart = spec.setup().ciPropertiesMap().getBoolean("postRestart");
							if (isPostRestart) {
								allRunFor(spec, postRestartValidation());
							} else {
								allRunFor(spec, preRestartSetup());
							}
						})
				);
	}

	private HapiSpecOperation[] preRestartSetup() {
		return new HapiSpecOperation[] {
				assertionsHold((spec, opLog) -> {})
		};
	}

	private HapiSpecOperation[] postRestartValidation() {
		return new HapiSpecOperation[] {
				getAccountInfo(RECEIVER).has(accountWith()
						.balance(99L)),

				scheduleSign(PENDING_XFER)
						.withSignatories(RECEIVER)
						.lookingUpBytesToSign(),

				getAccountInfo(RECEIVER).has(accountWith()
						.balance(100L))
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
