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

import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

public class PrivilegedOpsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(PrivilegedOpsSuite.class);

	private static final byte[] totalLimits =
			protoDefsFromResource("testSystemFiles/only-mint-allowed.json").toByteArray();
	private static final byte[] defaultThrottles =
			protoDefsFromResource("testSystemFiles/throttles-dev.json").toByteArray();

	public static void main(String... args) {
		new PrivilegedOpsSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						superusersAreNeverThrottledOnTransfers(),
						superusersAreNeverThrottledOnMiscTxns(),
						superusersAreNeverThrottledOnHcsTxns(),
						superusersAreNeverThrottledOnMiscQueries(),
						superusersAreNeverThrottledOnHcsQueries(),
						systemAccountUpdatePrivilegesAsExpected(),
						freezeAdminPrivilegesAsExpected(),
				}
		);
	}

	final int BURST_SIZE = 10;
	Function<String, HapiSpecOperation[]> transferBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(ignore ->
					cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
							.payingWith(payer)
							.deferStatusResolution())
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> miscTxnBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> cryptoCreate(String.format("Account%d", i))
					.payingWith(payer)
					.deferStatusResolution())
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> hcsTxnBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> createTopic(String.format("Topic%d", i))
					.payingWith(payer)
					.deferStatusResolution())
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> miscQueryBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> getAccountInfo(ADDRESS_BOOK_CONTROL)
					.nodePayment(100L)
					.payingWith(payer))
			.toArray(n -> new HapiSpecOperation[n]);
	Function<String, HapiSpecOperation[]> hcsQueryBurstFn = payer -> IntStream
			.range(0, BURST_SIZE)
			.mapToObj(i -> getTopicInfo("misc")
					.nodePayment(100L)
					.payingWith(payer))
			.toArray(n -> new HapiSpecOperation[n]);

	private HapiApiSpec freezeAdminPrivilegesAsExpected() {
		return defaultHapiSpec("FreezeAdminPrivilegesAsExpected")
				.given(
						cryptoCreate("civilian")
				).when(

						fileUpdate(UPDATE_ZIP_FILE)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.contents("Nope")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(UPDATE_ZIP_FILE)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents("Nope")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(UPDATE_ZIP_FILE)
								.payingWith(FEE_SCHEDULE_CONTROL)
								.contents("Nope")
								.hasPrecheck(AUTHORIZATION_FAILED),
						fileUpdate(UPDATE_ZIP_FILE)
								.payingWith("civilian")
								.contents("Nope")
								.hasPrecheck(AUTHORIZATION_FAILED)
				).then(
						fileUpdate(UPDATE_ZIP_FILE)
								.fee(0L)
								.via("updateTxn")
								.payingWith(FREEZE_ADMIN)
								.contents("Yuu"),
						getTxnRecord("updateTxn").showsNoTransfers(),
						fileAppend(UPDATE_ZIP_FILE)
								.fee(0L)
								.via("appendTxn")
								.payingWith(FREEZE_ADMIN)
								.content("upp"),
						getTxnRecord("appendTxn").showsNoTransfers(),
						fileUpdate(UPDATE_ZIP_FILE)
								.fee(0L)
								.payingWith(SYSTEM_ADMIN)
								.contents("Yuuupp"),
						fileAppend(UPDATE_ZIP_FILE)
								.fee(0L)
								.payingWith(GENESIS)
								.content(new byte[0])
				);
	}

	private HapiApiSpec systemAccountUpdatePrivilegesAsExpected() {
		return defaultHapiSpec("SystemAccountUpdatePrivilegesAsExpected")
				.given(
						newKeyNamed("new88"),
						cryptoCreate("civilian")
				).when(
						cryptoUpdate("0.0.88")
								.payingWith(GENESIS)
								.signedBy(GENESIS, "new88")
								.key("new88")
				).then(
						cryptoUpdate("0.0.2")
								.receiverSigRequired(true)
								.payingWith("civilian")
								.signedBy("civilian", GENESIS)
								.hasPrecheck(AUTHORIZATION_FAILED),
						cryptoUpdate("0.0.2")
								.receiverSigRequired(true)
								.payingWith(SYSTEM_ADMIN)
								.signedBy(SYSTEM_ADMIN, GENESIS)
								.hasPrecheck(AUTHORIZATION_FAILED),
						cryptoUpdate("0.0.2")
								.receiverSigRequired(false)
								.payingWith(GENESIS)
								.signedBy(GENESIS),
						cryptoUpdate("0.0.88")
								.key(GENESIS)
								.payingWith("civilian")
								.signedBy("civilian", "new88", GENESIS),
						cryptoUpdate("0.0.88")
								.payingWith(GENESIS)
								.signedBy(GENESIS, "new88")
								.key("new88"),
						cryptoUpdate("0.0.88")
								.key(GENESIS)
								.payingWith(SYSTEM_ADMIN)
								.signedBy(SYSTEM_ADMIN, GENESIS)
				);
	}

	private HapiApiSpec superusersAreNeverThrottledOnTransfers() {
		return defaultHapiSpec("SuperusersAreNeverThrottledOnTransfers")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L))
				).when(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(totalLimits)
								.hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
				).then(flattened(
						transferBurstFn.apply(SYSTEM_ADMIN),
						transferBurstFn.apply(ADDRESS_BOOK_CONTROL),
						sleepFor(5_000L),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles)
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnMiscTxns() {
		return defaultHapiSpec("MasterIsNeverThrottledOnMiscTxns")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L))
				).when(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(totalLimits)
								.hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
				).then(flattened(
						miscTxnBurstFn.apply(SYSTEM_ADMIN),
						miscTxnBurstFn.apply(ADDRESS_BOOK_CONTROL),
						sleepFor(5_000L),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles)
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnHcsTxns() {
		return defaultHapiSpec("MasterIsNeverThrottledOnHcsTxns")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L))
				).when(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(totalLimits)
								.hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
				).then(flattened(
						hcsTxnBurstFn.apply(SYSTEM_ADMIN),
						hcsTxnBurstFn.apply(ADDRESS_BOOK_CONTROL),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles)
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnMiscQueries() {
		return defaultHapiSpec("MasterIsNeverThrottledOnMiscQueries")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L))
				).when(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(totalLimits)
								.hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
				).then(flattened(
						inParallel(miscQueryBurstFn.apply(SYSTEM_ADMIN)),
						inParallel(miscQueryBurstFn.apply(ADDRESS_BOOK_CONTROL)),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles)
				));
	}

	private HapiApiSpec superusersAreNeverThrottledOnHcsQueries() {
		return defaultHapiSpec("MasterIsNeverThrottledOnHcsQueries")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1_000_000_000_000L)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1_000_000_000_000L)),
						createTopic("misc")
				).when(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(totalLimits)
								.hasKnownStatus(SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
				).then(flattened(
						inParallel(hcsQueryBurstFn.apply(SYSTEM_ADMIN)),
						inParallel(hcsQueryBurstFn.apply(ADDRESS_BOOK_CONTROL)),
						fileUpdate(THROTTLE_DEFS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(defaultThrottles)
				));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
