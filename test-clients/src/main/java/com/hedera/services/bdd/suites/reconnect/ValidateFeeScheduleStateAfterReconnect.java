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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.makeFree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ValidateFeeScheduleStateAfterReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ValidateFeeScheduleStateAfterReconnect.class);


	public static void main(String... args) {
		new ValidateFeeScheduleStateAfterReconnect().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				validateFeeScheduleStateAfterReconnect()
		);
	}

	private HapiApiSpec validateFeeScheduleStateAfterReconnect() {
		return customHapiSpec("validateFeeScheduleStateAfterReconnect")
				.withProperties(Map.of(
						"txn.start.offset.secs", "-5")
				)
				.given(
						sleepFor(Duration.ofSeconds(25).toMillis()),
						getAccountBalance(GENESIS)
								.setNode("0.0.6")
								.unavailableNode()
				)
				.when(
						getAccountBalance(GENESIS)
								.setNode("0.0.6")
								.unavailableNode(),
						makeFree(CryptoGetInfo),
						getAccountBalance(GENESIS)
								.setNode("0.0.6")
								.unavailableNode()
				)
				.then(
						withLiveNode("0.0.6")
								.within(180, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),
						getAccountInfo("0.0.2")
								.payingWith("civilian")
								.nodePayment(0L)
								.setNode("0.0.6")
								.hasAnswerOnlyPrecheck(OK)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
