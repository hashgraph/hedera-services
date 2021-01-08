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
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;

public class ValidateDuplicateTransactionAfterReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ValidateDuplicateTransactionAfterReconnect.class);

	public static void main(String... args) {
		new ValidateDuplicateTransactionAfterReconnect().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
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
						sleepFor(Duration.ofSeconds(25).toMillis()),
						getAccountBalance(GENESIS)
								.setNode("0.0.6")
								.unavailableNode(),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("ledger.keepRecordsInState", "true"))
				)
				.when(
						cryptoCreate("repeatedTransaction")
								.payingWith(SYSTEM_ADMIN)
								.validDurationSecs(180)
								.via(transactionId),
						getAccountBalance(GENESIS)
								.setNode("0.0.6")
								.unavailableNode()
				)
				.then(
						withLiveNode("0.0.6")
								.within(60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(10)
								.sleepingBetweenRetriesFor(5),
						UtilVerbs.sleepFor(30 * 1000),
						withLiveNode("0.0.6")
								.within(60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(10)
								.sleepingBetweenRetriesFor(5),
						cryptoCreate("repeatedTransaction")
								.payingWith(SYSTEM_ADMIN)
								.txnId(transactionId)
								.validDurationSecs(180)
								.hasPrecheck(DUPLICATE_TRANSACTION)
								.setNode("0.0.6")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
