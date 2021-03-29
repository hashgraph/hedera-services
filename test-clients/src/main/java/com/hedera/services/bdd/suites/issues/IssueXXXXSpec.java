package com.hedera.services.bdd.suites.issues;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;

public class IssueXXXXSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(IssueXXXXSpec.class);

	private static final String defaultCongestionMultipliers =
			HapiSpecSetup.getDefaultNodeProps().get("fees.percentCongestionMultipliers");

	public static void main(String... args) {
		new IssueXXXXSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						throttleDefsHaveExpectedDefaults(),
						throttleDefsRejectUnauthorizedPayers(),
						throttleUpdateRejectsMultiGroupAssignment(),
//						throttleUpdateWithZeroGroupOpsPerSecFails(),
						canUpdateMultipliersDynamically(),
				}
		);
	}

	private HapiApiSpec canUpdateMultipliersDynamically() {
		var artificialLimits = protoDefsFromResource("testSystemFiles/artificial-limits.json");
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");

		AtomicLong normalPrice = new AtomicLong();
		AtomicLong sevenXPrice = new AtomicLong();

		return defaultHapiSpec("CanUpdateMultipliersDynamically")
				.given(
						cryptoCreate("civilian")
								.payingWith(GENESIS)
								.balance(ONE_MILLION_HBARS),
						fileCreate("bytecode")
								.path(ContractResources.MULTIPURPOSE_BYTECODE_PATH)
								.payingWith(GENESIS),
						contractCreate("scMulti")
								.bytecode("bytecode")
								.payingWith(GENESIS),
						contractCall("scMulti")
								.payingWith("civilian")
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("cheapCall"),
						getTxnRecord("cheapCall")
								.providingFeeTo(normalPrice::set)
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", "1,7x"
								)),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(artificialLimits.toByteArray()),
						contractCall("scMulti")
								.payingWith("civilian")
								.fee(ONE_HUNDRED_HBARS)
								.sending(ONE_HBAR)
								.via("pricyCall")
				).then(
						getTxnRecord("pricyCall")
								.providingFeeTo(sevenXPrice::set),
						withOpContext((spec, opLog) -> {
							Assert.assertEquals(
									"~7x multiplier should be in affect!",
									7.0,
									(1.0 * sevenXPrice.get()) / normalPrice.get(),
									0.1);
						}),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles.toByteArray()),
						fileUpdate(APP_PROPERTIES)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.overridingProps(Map.of(
										"fees.percentCongestionMultipliers", defaultCongestionMultipliers
								))
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
