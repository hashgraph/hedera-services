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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.makeFree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.restoreFileFromRegistry;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.saveFileToRegistry;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.suites.reconnect.AutoRenewEntitiesForReconnect.runTransfersBeforeReconnect;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class UpdateAllProtectedFilesDuringReconnect extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(UpdateAllProtectedFilesDuringReconnect.class);

	public static void main(String... args) {
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
	}

	private static final String APP_FILE_REGISTRY = "AppPropertiesInRegistry";
	private static final String API_FILE_REGISTRY = "ApiPropertiesInRegistry";
	private static final String RATES_FILE_REGISTRY = "ExchangeRatesInRegistry";
	private static final String FEES_FILE_REGISTRY = "FeeSchedulesInRegistry";

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runTransfersBeforeReconnect(),
				updateAllProtectedFilesDuringReconnect()
		);
	}

	private HapiApiSpec updateAllProtectedFilesDuringReconnect() {
		final String fileInfoRegistry = "apiPermissionsReconnect";
		final String nonUpdatableFile = "nonUpdatableFile";

		return customHapiSpec("UpdateAllProtectedFilesDuringReconnect")
				.withProperties(Map.of(
						"txn.start.offset.secs", "-5")
				)
				.given(
						saveFileToRegistry(APP_PROPERTIES, APP_FILE_REGISTRY),
						saveFileToRegistry(API_PERMISSIONS, API_FILE_REGISTRY),
						saveFileToRegistry(EXCHANGE_RATES, RATES_FILE_REGISTRY),
						saveFileToRegistry(FEE_SCHEDULE, FEES_FILE_REGISTRY),

						sleepFor(Duration.ofSeconds(50).toMillis()),
						getAccountBalance(GENESIS)
								.setNode("0.0.8")
								.unavailableNode()
				)
				.when(
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(Map.of("ledger.autoRenewPeriod.minDuration", "20"))
								.erasingProps(Set.of("minimumAutoRenewDuration")),
						fileUpdate(API_PERMISSIONS)
								.payingWith(GENESIS)
								.overridingProps(Map.of("updateFile", "2-50")),

						fileCreate(nonUpdatableFile)
								.contents("Cannot update file because of the new api permissions"),

						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(100, 1)
													.toByteString();
											spec.registry().saveBytes("newRates", newRates);
											return newRates;
										}
								).payingWith(GENESIS),

						makeFree(CryptoGetInfo),

						getAccountBalance(GENESIS)
								.setNode("0.0.8")
								.unavailableNode()
				)
				.then(
						withLiveNode("0.0.8")
								.within(5 * 60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),
						UtilVerbs.sleepFor(30 * 1000),
						withLiveNode("0.0.8")
								.within(5 * 60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),

						getFileContents(API_PERMISSIONS)
								.logged()
								.setNode("0.0.3")
								.payingWith(GENESIS)
								.saveToRegistry(fileInfoRegistry),
						getFileContents(API_PERMISSIONS)
								.logged()
								.setNode("0.0.8")
								.payingWith(GENESIS)
								.hasContents(fileInfoRegistry),

						fileUpdate(nonUpdatableFile)
								.setNode("0.0.8")
								.fee(ONE_MILLION_HBARS)
								.hasPrecheck(NOT_SUPPORTED),

						fileCreate("contractFile")
								.setNode("0.0.8")
								.payingWith(GENESIS)
								.path(HapiSpecSetup.getDefaultInstance().defaultContractPath()),
						contractCreate("testContract")
								.bytecode("contractFile")
								.autoRenewSecs(15)
								.setNode("0.0.8")
								.hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE),

						cryptoCreate("civilian")
								.setNode("0.0.8")
								.fee(ONE_HUNDRED_HBARS)
								.hasPrecheck(INSUFFICIENT_TX_FEE),

						cryptoCreate("civilian")
								.setNode("0.0.8"),
						getAccountInfo("0.0.2")
								.payingWith("civilian")
								.nodePayment(0L)
								.setNode("0.0.8")
								.hasAnswerOnlyPrecheck(OK),

						restoreFileFromRegistry(APP_PROPERTIES, APP_FILE_REGISTRY),
						restoreFileFromRegistry(API_PERMISSIONS, API_FILE_REGISTRY),
						restoreFileFromRegistry(EXCHANGE_RATES, RATES_FILE_REGISTRY),
						restoreFileFromRegistry(FEE_SCHEDULE, FEES_FILE_REGISTRY)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
