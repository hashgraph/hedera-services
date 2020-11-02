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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;

public class GetAccountBalanceAfterReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(GetAccountBalanceAfterReconnect.class);

	public static void main(String... args) {
		new GetAccountBalanceAfterReconnect().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				getAccountBalanceFromAllNodes()
		);
	}

	private HapiApiSpec getAccountBalanceFromAllNodes() {
		String sender = "0.0.1002";
		String receiver = "0.0.1003";
		return defaultHapiSpec("GetAccountBalanceFromAllNodes")
				.given().when().then(
						UtilVerbs.withLiveNode("0.0.6")
								.within(4 * 60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),
						balanceSnapshot("senderBalance", sender), // from default node 0.0.3
						balanceSnapshot("receiverBalance", receiver), // from default node 0.0.3
						getAccountBalance(sender).logged().setNode("0.0.4")
								.hasTinyBars(changeFromSnapshot("senderBalance", 0)),
						getAccountBalance(receiver).logged().setNode("0.0.4")
								.hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
						getAccountBalance(sender).logged().setNode("0.0.5")
								.hasTinyBars(changeFromSnapshot("senderBalance", 0)),
						getAccountBalance(receiver).logged().setNode("0.0.5")
								.hasTinyBars(changeFromSnapshot("receiverBalance", 0)),
						getAccountBalance(sender).logged().setNode("0.0.6")
								.hasTinyBars(changeFromSnapshot("senderBalance", 0)),
						getAccountBalance(receiver).logged().setNode("0.0.6")
								.hasTinyBars(changeFromSnapshot("receiverBalance", 0))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
