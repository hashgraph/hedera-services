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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.cedarsoftware.util.UrlUtilities.getHostName;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.STANDARD_PERMISSIBLE_PRECHECKS;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.LoadTest.initialBalance;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect.DEFAULT_MINS_FOR_RECONNECT_TESTS;
import static com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect.DEFAULT_THREADS_FOR_RECONNECT_TESTS;

public class CreateSchedulesBeforeReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreateSchedulesBeforeReconnect.class);

	private static final int SCHEDULE_CREATION_LIMIT = 20000;
	private static final int SCHEDULE_CREATION_RECONNECT_TPS = 120;

	public static void main(String... args) {
		new CreateSchedulesBeforeReconnect().runSuiteSync();
	}

	private static final AtomicInteger scheduleNumber = new AtomicInteger(0);

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runCreateSchedules()
		);
	}

	private synchronized HapiSpecOperation generateScheduleCreateOperation() {
		final long schedule = scheduleNumber.getAndIncrement();
		if (schedule >= SCHEDULE_CREATION_LIMIT) {
			return getVersionInfo()
					.noLogging();
		}
		cryptoCreate("scheduleSender")
				.balance(initialBalance.getAsLong())
				.key(GENESIS)
				.deferStatusResolution();
		cryptoCreate("scheduleReceiver")
				.key(GENESIS)
				.deferStatusResolution();

		return scheduleCreate("schedule-" + getHostName() + "-" +
						scheduleNumber.getAndIncrement(),
				cryptoTransfer(tinyBarsFromTo("scheduleSender", "scheduleReceiver", 1))
		)
				.signedBy(DEFAULT_PAYER)
				.fee(ONE_HUNDRED_HBARS)
				.alsoSigningWith("scheduleSender")
				.hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
				.hasAnyKnownStatus()
				.deferStatusResolution()
				.adminKey(DEFAULT_PAYER)
				.noLogging();
	}

	private HapiApiSpec runCreateSchedules() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings(
				SCHEDULE_CREATION_RECONNECT_TPS,
				DEFAULT_MINS_FOR_RECONNECT_TESTS,
				DEFAULT_THREADS_FOR_RECONNECT_TESTS);

		Supplier<HapiSpecOperation[]> createBurst = () -> new HapiSpecOperation[] {
				generateScheduleCreateOperation()
		};

		return defaultHapiSpec("RunCreateSchedules")
				.given(
						logIt(ignore -> settings.toString())
				).when()
				.then(
						defaultLoadTest(createBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
