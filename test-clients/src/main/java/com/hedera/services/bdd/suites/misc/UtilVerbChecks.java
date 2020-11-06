package com.hedera.services.bdd.suites.misc;

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
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.makeFree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class UtilVerbChecks extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UtilVerbChecks.class);

	public static void main(String... args) throws Exception {
		new UtilVerbChecks().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						testLivenessTimeout(),
						testMakingFree(),
				}
		);
	}

	private HapiApiSpec testMakingFree() {
		return defaultHapiSpec("TestMakingFree")
				.given(
						cryptoCreate("civilian"),
						getAccountInfo("0.0.2")
								.payingWith("civilian")
								.nodePayment(0L)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE)
				).when(
						makeFree(CryptoGetInfo)
				).then(
						getAccountInfo("0.0.2")
								.payingWith("civilian")
								.nodePayment(0L)
								.hasAnswerOnlyPrecheck(OK)
				);
	}

	private HapiApiSpec testLivenessTimeout() {
		return defaultHapiSpec("TestLivenessTimeout")
				.given().when().then(
						withLiveNode("0.0.3")
								.within(300, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
