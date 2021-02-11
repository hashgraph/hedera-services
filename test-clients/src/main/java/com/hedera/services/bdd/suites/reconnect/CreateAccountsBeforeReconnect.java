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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.LoadTest.defaultLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class CreateAccountsBeforeReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreateAccountsBeforeReconnect.class);

	private static final int ACCOUNT_CREATION_LIMIT = 20_000;
	private static final int ACCOUNT_CREATION_RECONNECT_TPS = 120;
	
	public static final int DEFAULT_MINS_FOR_RECONNECT_TESTS = 3;
	public static final int DEFAULT_THREADS_FOR_RECONNECT_TESTS = 1;

	public static void main(String... args) {
		new CreateAccountsBeforeReconnect().runSuiteSync();
	}

	private static final AtomicInteger accountNumber = new AtomicInteger(0);


	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runCreateAccounts()
		);
	}

	private synchronized HapiSpecOperation generateCreateAccountOperation() {
		final long accNumber = accountNumber.getAndIncrement();
		if (accNumber >= ACCOUNT_CREATION_LIMIT) {
			return getVersionInfo()
					.noLogging();
		}

		return cryptoCreate("account" + accNumber)
				.balance(accNumber)
				.noLogging()
				.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
				.deferStatusResolution();
	}

	private HapiApiSpec runCreateAccounts() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings(
				ACCOUNT_CREATION_RECONNECT_TPS,
				DEFAULT_MINS_FOR_RECONNECT_TESTS,
				DEFAULT_THREADS_FOR_RECONNECT_TESTS);

		Supplier<HapiSpecOperation[]> createBurst = () -> new HapiSpecOperation[] {
				generateCreateAccountOperation()
		};

		return defaultHapiSpec("RunCreateAccounts")
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
