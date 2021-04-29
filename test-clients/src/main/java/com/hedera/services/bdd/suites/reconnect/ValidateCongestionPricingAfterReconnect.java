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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hedera.services.bdd.suites.reconnect.ValidateTokensStateAfterReconnect.reconnectingNode;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;

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
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						validateCongestionPricing(),
				}
		);
	}

	private HapiApiSpec validateCongestionPricing() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits-6N.json");
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		String tmpMinCongestionPeriodInSecs = "10";
		String civilianAccount = "civilian";
		String oneContract = "contract";

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
								.balance(ONE_MILLION_HBARS)
								.logging(),
						fileCreate("bytecode")
								.path(ContractResources.MULTIPURPOSE_BYTECODE_PATH)
								.payingWith(GENESIS).logging(),
						contractCreate(oneContract)
								.bytecode("bytecode").logging()
								.payingWith(GENESIS),
						contractCall(oneContract)
								.payingWith(civilianAccount)
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("cheapCallBeforeCongestionPricing").logging(),
						getTxnRecord("cheapCallBeforeCongestionPricing")
								.providingFeeTo(normalPrice::set).logging(),
						getAccountBalance(GENESIS)
								.setNode(reconnectingNode)
								.unavailableNode().logging()
				).when(
						/* update the multiplier to 10x with a 1% congestion for tmpMinCongestionPeriodInSecs */
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", "1,10x",
										"fees.minCongestionPeriod", tmpMinCongestionPeriodInSecs
								)).logging(),
						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(artificialLimits.toByteArray()).logging(),
						blockingOrder(
								IntStream.range(0, 10).mapToObj(i ->
										contractCall(oneContract)
												.payingWith(GENESIS)
												.fee(ONE_HUNDRED_HBARS)
												.sending(ONE_HBAR)
												.logging())
										.toArray(HapiSpecOperation[]::new)
						)
				).then(
						withLiveNode(reconnectingNode)
								.within(60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),

						contractCall(oneContract)
								.payingWith(civilianAccount)
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("pricyCallAfterReconnect")
								.setNode(reconnectingNode)
								.logging(),
						blockingOrder(
								IntStream.range(0, 10).mapToObj(i ->
										contractCall(oneContract)
												.payingWith(GENESIS)
												.fee(ONE_HUNDRED_HBARS)
												.sending(ONE_HBAR)
												.setNode(reconnectingNode)
												.logging())
										.toArray(HapiSpecOperation[]::new)
						),

						getTxnRecord("pricyCallAfterReconnect")
								.payingWith(GENESIS)
								.providingFeeTo(tenXPrice::set)
								.setNode(reconnectingNode)
								.logging(),

						/* check if the multiplier took effect in the contract call operation */
						withOpContext((spec, opLog) -> {
							Assert.assertEquals(
									"~10x multiplier should be in affect!",
									10.0,
									(1.0 * tenXPrice.get()) / normalPrice.get(),
									0.1);
						}),

						/* revert the multiplier before test ends */
						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles.toByteArray())
								.setNode(reconnectingNode)
								.logging(),
						fileUpdate(APP_PROPERTIES)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", defaultCongestionMultipliers,
										"fees.minCongestionPeriod", defaultMinCongestionPeriod
								))
								.logging(),

						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith(GENESIS).logging()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
