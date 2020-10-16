package com.hedera.services.bdd.suites.file.negative;

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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.Assert.assertEquals;

public class UpdateFailuresSpec extends HapiApiSuite {
	private static final long A_LOT = 1_234_567_890L;
	private static final long A_LITTLE = 1_234L;
	private static final Logger log = LogManager.getLogger(UpdateFailuresSpec.class);

	public static void main(String... args) {
		new UpdateFailuresSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						precheckAllowsMissing(),
						precheckAllowsDeleted(),
//						precheckScreensFees(),
						precheckRejectsPrematureExpiry(),
						precheckAllowsBadEncoding(),
//						handleScreensFees(),
						handleIgnoresEarlierExpiry(),
						precheckRejectsUnauthorized(),
						confusedUpdateCantExtendExpiry(),
				}
		);
	}


	private HapiApiSpec confusedUpdateCantExtendExpiry() {
		var initialExpiry = new AtomicLong();
		var extension = 1_000L;
		return defaultHapiSpec("confusedUpdateCantExtendExpiry")
				.given(
						withOpContext((spec, opLog) -> {
							var infoOp = QueryVerbs.getFileInfo(EXCHANGE_RATES);
							CustomSpecAssert.allRunFor(spec, infoOp);
							var info = infoOp.getResponse().getFileGetInfo().getFileInfo();
							initialExpiry.set(info.getExpirationTime().getSeconds());
						})
				).when(
						fileUpdate(EXCHANGE_RATES)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents("NONSENSE".getBytes())
								.extendingExpiryBy(extension)
								.hasKnownStatus(ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE)
				).then(
						QueryVerbs.getFileInfo(EXCHANGE_RATES).hasExpiry(initialExpiry::get)
				);
	}


	private HapiApiSpec precheckRejectsUnauthorized() {
		return defaultHapiSpec("precheckRejectsUnauthAddressBookUpdate")
				.given(
						cryptoCreate("civilian")
				).when().then(
						fileUpdate(ADDRESS_BOOK)
								.payingWith("civilian")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(NODE_DETAILS)
								.payingWith("civilian")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(API_PERMISSIONS)
								.payingWith("civilian")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(APP_PROPERTIES)
								.payingWith("civilian")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(FEE_SCHEDULE)
								.payingWith("civilian")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(EXCHANGE_RATES)
								.payingWith("civilian")
								.hasPrecheck(AUTHORIZATION_FAILED)
				);
	}

	private HapiApiSpec precheckAllowsMissing() {
		return defaultHapiSpec("precheckAllowsMissing")
				.given().when().then(
						fileUpdate("1.2.3")
								.fee(1_234_567L)
								.signedBy(GENESIS)
								.hasPrecheck(OK)
								.hasKnownStatus(INVALID_FILE_ID)
				);
	}

	private HapiApiSpec precheckAllowsDeleted() {
		return defaultHapiSpec("precheckAllowsDeleted")
				.given(
						fileCreate("tbd")
				).when(
						fileDelete("tbd")
				).then(
						fileUpdate("tbd")
								.hasPrecheck(OK)
								.hasKnownStatus(FILE_DELETED)
				);
	}

	private HapiApiSpec precheckScreensFees() {
		return defaultHapiSpec("precheckScreensFees")
				.given(
						fileCreate("file")
				).when().then(
						fileUpdate("file")
								.extendingExpiryBy(666L)
								.fee(A_LITTLE)
								.hasPrecheck(INSUFFICIENT_TX_FEE)
				);
	}

	private HapiApiSpec precheckRejectsPrematureExpiry() {
		long now = Instant.now().getEpochSecond();
		return defaultHapiSpec("precheckRejectsPastExpiry")
				.given(
						fileCreate("file")
				).when().then(
						fileUpdate("file")
								.fee(A_LOT)
								.extendingExpiryBy(-now)
								.hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE)
				);
	}

	private HapiApiSpec handleScreensFees() {
		return defaultHapiSpec("handleScreensFees")
				.given(
						fileCreate("file")
				).when(
						fileUpdate("file")
								.extendingExpiryBy(100_000L)
								.deferStatusResolution()
				).then(
						fileUpdate("file")
								.extendingExpiryBy(666L)
								.hasKnownStatus(INSUFFICIENT_TX_FEE)
				);
	}

	private HapiApiSpec precheckAllowsBadEncoding() {
		return defaultHapiSpec("badEncoding")
				.given(
						fileCreate("file")
				).when().then(
						fileUpdate("file")
								.fee(A_LOT)
								.signedBy(GENESIS)
								.useBadWacl()
								.hasPrecheck(OK)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec handleIgnoresEarlierExpiry() {
		var initialExpiry = new AtomicLong();

		return defaultHapiSpec("handleScreensFees")
				.given(
						fileCreate("file"),
						withOpContext((spec, opLog) -> {
							initialExpiry.set(spec.registry().getTimestamp("file").getSeconds());
						})
				).when(
						fileUpdate("file").extendingExpiryBy(-1_000)
				).then(
						UtilVerbs.assertionsHold((spec, opLog) -> {
							var infoOp = QueryVerbs.getFileInfo("file");
							CustomSpecAssert.allRunFor(spec, infoOp);
							var currExpiry =
									infoOp.getResponse().getFileGetInfo().getFileInfo().getExpirationTime().getSeconds();
							assertEquals("Expiry changed unexpectedly!", initialExpiry.get(), currExpiry);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
