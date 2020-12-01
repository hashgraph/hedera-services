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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.makeFree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ValidateChangesAfterReconnect extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(ValidateChangesAfterReconnect.class);

	final String PATH_TO_VALID_BYTECODE = HapiSpecSetup.getDefaultInstance().defaultContractPath();

	public static void main(String... args) {
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				validateChangesAfterReconnect()
		);
	}

	private HapiApiSpec validateChangesAfterReconnect() {
		final String fileInfoRegistry = "apiPermissionsReconnect";
		final String transactionId = "specialTransactionId";
		final String transactionFeeid = "authorizedTxn";
		final long newFee = 159_588_904;

		return customHapiSpec("validateChangesAfterReconnect")
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
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("minimumAutoRenewDuration", "20")),

						fileCreate("effectivelyImmutable")
								.contents("Can't touch me!"),
						fileUpdate(API_PERMISSIONS)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("updateFile", "2-50")),

						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(1, 1)
													.toByteString();
											spec.registry().saveBytes("newRates", newRates);
											return newRates;
										}
								).payingWith(MASTER),

						makeFree(CryptoGetInfo),

						getAccountBalance(GENESIS)
								.setNode("0.0.6")
								.unavailableNode()
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
								.payingWith(MASTER)
								.saveToRegistry(fileInfoRegistry),
						getFileContents(API_PERMISSIONS)
								.logged()
								.setNode("0.0.6")
								.payingWith(MASTER)
								.hasContents(fileInfoRegistry),

						fileUpdate("effectivelyImmutable")
								.setNode("0.0.6")
								.hasPrecheck(NOT_SUPPORTED),

						fileCreate("contractFile")
								.setNode("0.0.6")
								.payingWith(ADDRESS_BOOK_CONTROL)
								.path(PATH_TO_VALID_BYTECODE),
						contractCreate("testContract")
								.bytecode("contractFile")
								.autoRenewSecs(15)
								.setNode("0.0.6")
								.hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE),

						cryptoCreate("civilian")
								.via(transactionFeeid)
								.setNode("0.0.6"),
						getTxnRecord(transactionFeeid)
								.setNode("0.0.6")
								.hasPriority(recordWith().fee(newFee)),

						cryptoCreate("civilian")
								.setNode("0.0.6"),
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
