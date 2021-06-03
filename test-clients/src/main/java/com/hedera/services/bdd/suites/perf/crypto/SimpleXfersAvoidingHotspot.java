package com.hedera.services.bdd.suites.perf.crypto;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static java.util.concurrent.TimeUnit.MINUTES;

public class SimpleXfersAvoidingHotspot extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SimpleXfersAvoidingHotspot.class);

	private static final int NUM_ACCOUNTS = 1_000;

	private AtomicLong duration = new AtomicLong(60);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

	public static void main(String... args) {
		new SimpleXfersAvoidingHotspot().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runSimpleXfers(),
				}
		);
	}

	private HapiApiSpec runSimpleXfers() {
		return HapiApiSpec.defaultHapiSpec("RunTokenTransfers")
				.given().when().then(
						runWithProvider(avoidantXfersFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private Function<HapiApiSpec, OpProvider> avoidantXfersFactory() {
		final var nextSender = new AtomicInteger();
		final IntFunction<String> nameFn = i -> "account" + i;

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(inParallel(IntStream.range(0, NUM_ACCOUNTS)
								.mapToObj(i -> cryptoCreate(nameFn.apply(i))
										.balance(ONE_HUNDRED_HBARS * 1_000)
										.key(GENESIS)
										.deferStatusResolution())
								.toArray(HapiSpecOperation[]::new)),
						sleepFor(30_000L));
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				final int sender = nextSender.getAndUpdate(i -> (i + 1) % NUM_ACCOUNTS);
				final int receiver = (sender + 1) % NUM_ACCOUNTS;
				final var op = cryptoTransfer(tinyBarsFromTo(nameFn.apply(sender), nameFn.apply(receiver), 1))
						.deferStatusResolution()
						.noLogging();
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}