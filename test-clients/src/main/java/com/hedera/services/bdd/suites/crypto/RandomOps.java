package com.hedera.services.bdd.suites.crypto;

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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getExecTime;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getExecTimeNoPayment;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class RandomOps extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RandomOps.class);

	public static void main(String... args) {
		new RandomOps().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
//						freezeDemo(),
//						retryLimitDemo()
						execTimesDemo(),
				}
		);
	}

	private HapiApiSpec execTimesDemo() {
		final var cryptoTransfer = "cryptoTransfer";
		final var submitMessage = "submitMessage";
		final var contractCall = "contractCall";

		final var humbleUser = "aamAdmi";
		final var topic = "ofGeneralInterest";
		final var contract = "binding";
		final var bytecode = "bytecode";

		return defaultHapiSpec("execTimesDemo")
				.given(
						inParallel(IntStream.range(0, 1000)
								.mapToObj(i -> cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L))
										.deferStatusResolution()
										.noLogging())
								.toArray(HapiSpecOperation[]::new)),
						sleepFor(5_000),
						cryptoCreate(humbleUser).balance(ONE_HUNDRED_HBARS),
						createTopic(topic),
						fileCreate(bytecode)
								.path(ContractResources.MULTIPURPOSE_BYTECODE_PATH),
						contractCreate(contract)
								.bytecode(bytecode)
				).when(
						cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L))
								.payingWith(GENESIS)
								.via(cryptoTransfer),
						submitMessageTo(topic)
								.message(randomUppercase(256))
								.via(submitMessage),
						contractCall(contract)
								.sending(ONE_HBAR)
								.via(contractCall)
				).then(
						/* NetworkGetExecutionTime requires superuser payer */
						getExecTime(cryptoTransfer, submitMessage, contractCall)
								.payingWith(GENESIS)
								.hasAnswerOnlyPrecheck(INVALID_TRANSACTION_ID),
								/* Uncomment to validate failure message */
//								.assertingNoneLongerThan(1, ChronoUnit.MILLIS)
//								.logged(),
						getExecTimeNoPayment(cryptoTransfer, submitMessage, contractCall)
								.payingWith(GENESIS)
								.hasCostAnswerPrecheck(NOT_SUPPORTED),
						getExecTime(cryptoTransfer, submitMessage, contractCall)
								.payingWith(humbleUser)
								.hasCostAnswerPrecheck(NOT_SUPPORTED)
								.hasAnswerOnlyPrecheck(NOT_SUPPORTED),
						getExecTimeNoPayment(cryptoTransfer, submitMessage, contractCall)
								.payingWith(humbleUser)
								.hasCostAnswerPrecheck(NOT_SUPPORTED)
				);
	}

	private HapiApiSpec retryLimitDemo() {
		return defaultHapiSpec("RetryLimitDemo")
				.given()
				.when()
				.then(
						getAccountInfo("0.0.2")
								.hasRetryAnswerOnlyPrecheck(OK)
								.setRetryLimit(5),
						cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L))
								.hasRetryPrecheckFrom(OK)
								.setRetryLimit(3),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 7L))
				);
	}

	private HapiApiSpec freezeDemo() {
		return customHapiSpec("FreezeDemo")
				.withProperties(Map.of(
						"nodes", "127.0.0.1:50213:0.0.3,127.0.0.1:50214:0.0.4,127.0.0.1:50215:0.0.5"
				)).given().when(
				).then(
						freezeOnly().startingIn(60).seconds()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
