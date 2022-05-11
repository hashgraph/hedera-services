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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.suites.reconnect.AutoRenewEntitiesForReconnect.runTransfersBeforeReconnect;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;

public class ValidateDuplicateTransactionAfterReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ValidateDuplicateTransactionAfterReconnect.class);

	public static void main(String... args) {
		new ValidateDuplicateTransactionAfterReconnect().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runTransfersBeforeReconnect(),
				validateDuplicateTransactionAfterReconnect()
		);
	}

	private HapiApiSpec validateDuplicateTransactionAfterReconnect() {
		final String transactionId = "specialTransactionId";
		return customHapiSpec("validateDuplicateTransactionAfterReconnect")
				.withProperties(Map.of(
						"txn.start.offset.secs", "-5")
				)
				.given(
						sleepFor(Duration.ofSeconds(50).toMillis()),
						getAccountBalance(GENESIS)
								.setNode("0.0.8")
								.unavailableNode()
				)
				.when(
						cryptoCreate("repeatedTransaction")
								.via(transactionId),
						getAccountBalance(GENESIS)
								.setNode("0.0.8")
								.unavailableNode()
				)
				.then(
						withLiveNode("0.0.8")
								.within(180, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(10)
								.sleepingBetweenRetriesFor(5),
						cryptoCreate("repeatedTransaction")
								.txnId(transactionId)
								.hasPrecheck(DUPLICATE_TRANSACTION)
								.setNode("0.0.8")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
