package com.hedera.services.bdd.suites.throttling;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateToNewThrottlePropsFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ThrottleSanityChecks extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ThrottleSanityChecks.class);

	private static int receiptCapacity = 20;

	private AtomicLong duration = new AtomicLong(60);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
//	private AtomicInteger maxOpsPerSec = new AtomicInteger(26);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

	public static void main(String... args) {
		new ThrottleSanityChecks().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[]{
						updateToDownscaled(),
//						restoreFromBkup(),
				}
		);
	}

	private HapiApiSpec restoreFromBkup() {
		try {
			var fromBkup = ByteString.copyFrom(Files.readAllBytes(Paths.get("perf-application.properties.bkup")));
			return defaultHapiSpec("RestoreFromBkup")
					.given( ).when( ).then(
							fileUpdate(APP_PROPERTIES).contents(fromBkup)
					);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private HapiApiSpec updateToDownscaled() {
		AtomicReference<ByteString> legacyProps = new AtomicReference<>();

		return defaultHapiSpec("OverrideConfigTakesPrecedence")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES)
									.saveTo("perf-application.properties.bkup");
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						}),
						cryptoCreate("civilian").balance(10_000_000_000L),
						updateToNewThrottlePropsFrom(propertiesLoc()),
						sleepFor(15_000L)
				).when(
//						runWithProvider(topicCreates())
//								.lasting(duration::get, unit::get)
//								.maxOpsPerSec(maxOpsPerSec::get)
						runWithProvider(fastOps())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						withOpContext((spec, opLog) -> {
							opLog.info("Total ops accepted in {} {} = {} ==> {} TPS",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									String.format("%.3f", 1.0 * spec.finalAdhoc() / duration.get()));
						}),
						fileUpdate(APP_PROPERTIES).contents(ignore -> legacyProps.get())
				);
	}

	private Function<HapiApiSpec, OpProvider> fastOps() {
		AtomicInteger accountIndex = new AtomicInteger(0);
		List<String> nodeAccounts = IntStream.rangeClosed(3, 15)
				.mapToObj(i -> String.format("0.0.%d", i))
				.collect(Collectors.toList());
		Supplier<String> nextNodeAccount = () -> nodeAccounts.get(accountIndex.getAndIncrement() % 13);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(cryptoCreate("nobody"));
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = cryptoTransfer(tinyBarsFromTo("civilian", "nobody", 1))
						.setNodeFrom(nextNodeAccount::get)
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
//				var op = cryptoUpdate("nobody")
//						.setNodeFrom(nextNodeAccount::get)
//						.deferStatusResolution()
//						.payingWith("civilian")
//						.sendThreshold(1_234_567L + accountIndex.get())
//						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> topicCreates() {
		AtomicInteger topicId = new AtomicInteger(1_234_567);

		AtomicInteger accountIndex = new AtomicInteger(0);
		List<String> nodeAccounts = IntStream.rangeClosed(3, 15)
				.mapToObj(i -> String.format("0.0.%d", i))
				.collect(Collectors.toList());
		Supplier<String> nextNodeAccount = () -> nodeAccounts.get(accountIndex.getAndIncrement() % 13);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return Collections.emptyList();
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = createTopic("topic" + topicId.getAndIncrement())
						.setNodeFrom(nextNodeAccount::get)
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}


	private String propertiesLoc() {
		return "src/main/resource/downscaled-throttle.properties";
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
