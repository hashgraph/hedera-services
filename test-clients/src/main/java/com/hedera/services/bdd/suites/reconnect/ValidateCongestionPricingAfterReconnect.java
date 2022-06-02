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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.reconnect.AutoRenewEntitiesForReconnect.runTransfersBeforeReconnect;
import static com.hedera.services.bdd.suites.reconnect.ValidateTokensStateAfterReconnect.reconnectingNode;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;

/**
 * A reconnect test in which a congestion pricing multiplier is updated and triggered while the node 0.0.8 is
 * disconnected from the network. Once the node is reconnected validate that the congestion pricing is in affect on
 * reconnected node
 */
public class ValidateCongestionPricingAfterReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ValidateCongestionPricingAfterReconnect.class);
	private static final String defaultCongestionMultipliers =
			HapiSpecSetup.getDefaultNodeProps().get("fees.percentCongestionMultipliers");
	private static final String defaultMinCongestionPeriod =
			HapiSpecSetup.getDefaultNodeProps().get("fees.minCongestionPeriod");

	public static void main(String... args) {
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runTransfersBeforeReconnect(),
						validateCongestionPricing(),
				}
		);
	}

	private HapiApiSpec validateCongestionPricing() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-6N.json");
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		String tmpMinCongestionPeriodInSecs = "5";
		String civilianAccount = "civilian";
		String oneContract = "Multipurpose";

		AtomicLong normalPrice = new AtomicLong();
		AtomicLong tenXPrice = new AtomicLong();

		return customHapiSpec("ValidateCongestionPricing")
				.withProperties(Map.of(
						"txn.start.offset.secs", "-5")
				)
				.given(
						sleepFor(Duration.ofSeconds(25).toMillis()),

						cryptoCreate(civilianAccount)
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS),
						uploadInitCode(oneContract),
						contractCreate(oneContract)
								.payingWith(GENESIS)
								.logging(),
						contractCall(oneContract)
								.payingWith(civilianAccount)
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("cheapCallBeforeCongestionPricing"),
						getTxnRecord("cheapCallBeforeCongestionPricing")
								.providingFeeTo(normalPrice::set),
						sleepFor(30000),
						getAccountBalance(GENESIS)
								.setNode(reconnectingNode)
								.unavailableNode()
				).when(
						/* update the multiplier to 10x with a 1% congestion for tmpMinCongestionPeriodInSecs */
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", "1,10x",
										"fees.minCongestionPeriod", tmpMinCongestionPeriodInSecs
								)),
						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(artificialLimits.toByteArray()),
						blockingOrder(
								IntStream.range(0, 20).mapToObj(i ->
										contractCall(oneContract)
												.payingWith(GENESIS)
												.fee(ONE_HUNDRED_HBARS)
												.sending(ONE_HBAR))
										.toArray(HapiSpecOperation[]::new)
						)
				).then(
						withLiveNode(reconnectingNode)
								.within(5 * 60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),
						// So although currently disconnected node is in ACTIVE mode, it might
						// immediately enter BEHIND mode and start a reconnection session.
						// We need to wait for the reconnection session to finish and the platform becomes ACTIVE again,
						// then we can send more transactions. Otherwise, transactions may be pending for too long
						// and we will get UNKNOWN status
						sleepFor(30000),
						blockingOrder(
								IntStream.range(0, 10).mapToObj(i ->
										contractCall(oneContract)
												.payingWith(GENESIS)
												.fee(ONE_HUNDRED_HBARS)
												.sending(ONE_HBAR)
												.setNode(reconnectingNode))
										.toArray(HapiSpecOperation[]::new)
						),
						contractCall(oneContract)
								.payingWith(civilianAccount)
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("pricyCallAfterReconnect")
								.setNode(reconnectingNode),

						getTxnRecord("pricyCallAfterReconnect")
								.payingWith(GENESIS)
								.providingFeeTo(tenXPrice::set)
								.setNode(reconnectingNode),

						/* check if the multiplier took effect in the contract call operation */
						withOpContext((spec, opLog) -> {
							Assertions.assertEquals(
									10.0,
									(1.0 * tenXPrice.get()) / normalPrice.get(),
									0.1,
									"~10x multiplier should be in affect!");
						}),

						/* revert the multiplier before test ends */
						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles.toByteArray())
								.setNode(reconnectingNode),
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", defaultCongestionMultipliers,
										"fees.minCongestionPeriod", defaultMinCongestionPeriod
								)),

						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith(GENESIS)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
