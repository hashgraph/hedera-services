package com.hedera.services.bdd.suites.throttling;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;

public class ThrottleDefsUpdateSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ThrottleDefsUpdateSpecs.class);

	public static void main(String... args) {
		new ThrottleDefsUpdateSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
//						throttleDefsRejectUnauthorizedPayers(),
//						throttleDefsHaveExpectedDefaults(),
						throttleUpdateRejectsMultiGroupAssignment(),
//						throttleUpdateWithZeroGroupOpsPerSecFails(),
//						congestionPricingWorks(),
				}
		);
	}

	private HapiApiSpec congestionPricingWorks() {
		int burstSize = 999;
		String expensiveXfer = "expensive";

		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		var expensiveXferThrottles = protoDefsFromResource("testSystemFiles/expensive-xfer-throttles.json");

		return defaultHapiSpec("CongestionPricingWorks")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ONE_MILLION_HBARS)),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(expensiveXferThrottles.toByteArray())
				).when(
						inParallel(IntStream.range(0, burstSize).mapToObj(i ->
								cryptoTransfer(tinyBarsFromTo(EXCHANGE_RATE_CONTROL, FUNDING, 1))
										.fee(ONE_HUNDRED_HBARS)
										.payingWith(EXCHANGE_RATE_CONTROL)
										.deferStatusResolution())
								.toArray(HapiSpecOperation[]::new)),
						cryptoTransfer(tinyBarsFromTo(EXCHANGE_RATE_CONTROL, FUNDING, 1))
								.payingWith(EXCHANGE_RATE_CONTROL)
								.fee(ONE_HUNDRED_HBARS)
								.via(expensiveXfer)
				).then(
						getTxnRecord(expensiveXfer)
								.logged(),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles.toByteArray())
				);
	}

	private HapiApiSpec throttleUpdateWithZeroGroupOpsPerSecFails() {
		var zeroOpsPerSecThrottles = protoDefsFromResource("testSystemFiles/zero-ops-group.json");

		return defaultFailingHapiSpec("ThrottleUpdateWithZeroGroupOpsPerSecFails")
				.given( ).when( ).then(
						fileUpdate(THROTTLE_DEFS)
								.contents(zeroOpsPerSecThrottles.toByteArray())
								.hasKnownStatus(THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC)
				);
	}

	private HapiApiSpec throttleUpdateRejectsMultiGroupAssignment() {
		var multiGroupThrottles = protoDefsFromResource("testSystemFiles/duplicated-operation.json");

		return defaultHapiSpec("ThrottleUpdateRejectsMultiGroupAssignment")
				.given( ).when( ).then(
						fileUpdate(THROTTLE_DEFS)
								.contents(multiGroupThrottles.toByteArray())
								.hasKnownStatus(OPERATION_REPEATED_IN_BUCKET_GROUPS)
				);
	}

	private HapiApiSpec throttleDefsRejectUnauthorizedPayers() {
		return defaultHapiSpec("ThrottleDefsRejectUnauthorizedPayers")
				.given(
						cryptoCreate("civilian")
				).when( ).then(
						fileUpdate(THROTTLE_DEFS)
								.contents("BOOM")
								.payingWith("civilian")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(THROTTLE_DEFS)
								.contents("BOOM")
								.payingWith(FEE_SCHEDULE_CONTROL)
								.hasPrecheck(AUTHORIZATION_FAILED)
				);
	}

	private HapiApiSpec throttleDefsHaveExpectedDefaults() {
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");

		return defaultHapiSpec("ThrottleDefsExistOnStartup")
				.given( ).when( ).then(
						getFileContents(THROTTLE_DEFS)
								.payingWith(GENESIS)
								.hasContents(ignore -> defaultThrottles.toByteArray())
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
