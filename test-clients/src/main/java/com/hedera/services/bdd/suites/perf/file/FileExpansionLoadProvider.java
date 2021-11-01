package com.hedera.services.bdd.suites.perf.file;


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
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.util.concurrent.TimeUnit.MINUTES;

public class FileExpansionLoadProvider extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FileExpansionLoadProvider.class);

	private AtomicLong duration = new AtomicLong(5);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(13);

	public static void main(String... args) {
		new FileExpansionLoadProvider().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runFileExpansions(),
				}
		);
	}

	private HapiApiSpec runFileExpansions() {
		return HapiApiSpec.defaultHapiSpec("RunFileExpansions")
				.given(
						getAccountBalance(DEFAULT_PAYER).logged(),
						stdMgmtOf(duration, unit, maxOpsPerSec)
				).when(
						runWithProvider(fileExpansionsFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						getAccountBalance(DEFAULT_PAYER).logged(),
						// The freeze and long wait after freeze means to keep the server in MAINTENANCE state till test
						// end to prevent it from making new export files that may cause account balances validator to
						// be inconsistent. The freeze shouldn't cause normal perf test any issue.
						freezeOnly().payingWith(GENESIS)
								.startingIn(30).seconds()
								.hasKnownStatusFrom(SUCCESS, UNKNOWN)
								.hasAnyPrecheck(),
						sleepFor(60_000)
				);
	}

	private Function<HapiApiSpec, OpProvider> fileExpansionsFactory() {
		final AtomicLong currentTarget = new AtomicLong();
		final AtomicBoolean timeForNewTarget = new AtomicBoolean(Boolean.TRUE);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				HapiSpecOperation op;
				if (timeForNewTarget.get()) {
				} else {

				}
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
