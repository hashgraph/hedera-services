package com.hedera.services.bdd.suites.file;

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
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;

public class ExchangeRateControlSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ExchangeRateControlSuite.class);

	public static void main(String... args) {
		new ExchangeRateControlSuite().runSuiteSync();
	}

	@Override
	public boolean leaksState() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				midnightRateChangesWhenAcct50UpdatesFile112(),
				acct57CanMakeSmallChanges()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
				anonCantUpdateRates(),
				acct57CantMakeLargeChanges()
		);
	}

	final HapiFileUpdate resetRatesOp = fileUpdate(EXCHANGE_RATES).fee(ADEQUATE_FUNDS)
			.contents(spec -> spec.ratesProvider().rateSetWith(1, 12).toByteString());

	private HapiApiSpec acct57CanMakeSmallChanges() {
		return defaultHapiSpec("Acct57CanMakeSmallChanges")
				.given(
						resetRatesOp,
						cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
				).when(
						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(10, 121)
													.toByteString();
											spec.registry().saveBytes("newRates", newRates);
											return newRates;
										}
								).payingWith(EXCHANGE_RATE_CONTROL)
				).then(
						getFileContents(EXCHANGE_RATES).hasContents(spec -> spec.registry().getBytes("newRates")),
						resetRatesOp
				);
	}

	private HapiApiSpec acct57UpdatesMidnightRateAtMidNight() throws ParseException {
		return defaultHapiSpec("Acct57UpdatesMidnightRateAtMidNight")
				.given(
						resetRatesOp,
						cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
				).when(
						// should be done just before midnight
						UtilVerbs.waitUntil("23:58"),
						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(10, 147)
													.toByteString();
											spec.registry().saveBytes("midnightRate", newRates);
											return newRates;
										}
								).payingWith(EXCHANGE_RATE_CONTROL)
				).then(
						// should be the first transaction after midnight
						UtilVerbs.sleepFor(300_000),
						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(10, 183)
													.toByteString();
											spec.registry().saveBytes("newRates", newRates);
											return newRates;
										}
								).payingWith(EXCHANGE_RATE_CONTROL),

						getFileContents(EXCHANGE_RATES).hasContents(spec -> spec.registry().getBytes("newRates")),
						resetRatesOp
				);
	}

	private HapiApiSpec midnightRateChangesWhenAcct50UpdatesFile112() {
		return defaultHapiSpec("MidnightRateChangesWhenAcct50UpdatesFile112")
				.given(
						resetRatesOp,
						cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS)),
						cryptoTransfer(tinyBarsFromTo(GENESIS, MASTER, ADEQUATE_FUNDS)),
						fileUpdate(EXCHANGE_RATES)
						.contents(
								spec -> {
									ByteString newRates = spec
											.ratesProvider()
											.rateSetWith(10, 254)
											.toByteString();
									spec.registry().saveBytes("newRates", newRates);
									return newRates;
								}
						).payingWith(EXCHANGE_RATE_CONTROL).fee(1_000_000_000).hasKnownStatus(EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED)
				).when(
						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(1, 25)
													.toByteString();
											spec.registry().saveBytes("newRates", newRates);
											return newRates;
										}
								).payingWith(MASTER).fee(1_000_000_000)
				).then(
						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(10, 254)
													.toByteString();
											spec.registry().saveBytes("newRates", newRates);
											return newRates;
										}
								).payingWith(EXCHANGE_RATE_CONTROL).fee(1_000_000_000).hasKnownStatus(SUCCESS),
						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> {
											ByteString newRates = spec
													.ratesProvider()
													.rateSetWith(1, 12, 1, 15)
													.toByteString();
											spec.registry().saveBytes("newRates", newRates);
											return newRates;
										}
								).payingWith(MASTER).fee(1_000_000_000).hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec anonCantUpdateRates() {
		return defaultHapiSpec("AnonCantUpdateRates")
				.given(
						resetRatesOp,
						cryptoCreate("randomAccount")
				).when().then(
						fileUpdate(EXCHANGE_RATES)
								.contents("Should be impossible!")
								.payingWith("randomAccount")
								.hasPrecheck(AUTHORIZATION_FAILED)
				);
	}

	private HapiApiSpec acct57CantMakeLargeChanges() {
		return defaultHapiSpec("Acct57CantMakeLargeChanges")
				.given(
						resetRatesOp,
						cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS))
				).when().then(
						fileUpdate(EXCHANGE_RATES)
								.contents(
										spec -> spec.ratesProvider()
													.rateSetWith(1, 25)
													.toByteString()
								).payingWith(EXCHANGE_RATE_CONTROL).hasKnownStatus(EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
