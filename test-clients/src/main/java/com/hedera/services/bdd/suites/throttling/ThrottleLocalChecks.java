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
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

public class ThrottleLocalChecks extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ThrottleLocalChecks.class);

	private static final int NUM_LOCAL_NODES = 3;

	private static final double EXPECTED_SC_TPS = 13.0 / NUM_LOCAL_NODES;
	private static final double EXPECTED_FILE_TPS = 13.0 / NUM_LOCAL_NODES;
	private static final double EXPECTED_CRYPTO_TPS = 500.0 / NUM_LOCAL_NODES;
	private static final double EXPECTED_OTHER_HCS_TPS = 300.0 / NUM_LOCAL_NODES;
	private static final double EXPECTED_TOPIC_CREATE_TPS = 5.0 / NUM_LOCAL_NODES;

	private static final String BACKUP_LOC = "bkup-application.properties";
	private static final String THROTTLE_PROPS_FOR_CHECK = "src/main/resource/local-check-throttle.properties";

	private AtomicLong duration = new AtomicLong(60);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

	public static void main(String... args) {
		new ThrottleLocalChecks().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
//						saveToBkup(),
						checkOtherHcsThrottles(),
//						checkContractThrottles(),
//						checkFileThrottles(),
//						checkCryptoThrottles(),
//						checkTopicCreateThrottles(),
//						restoreFromBkup(),
				}
		);
	}

	private HapiApiSpec checkTopicCreateThrottles() {
		AtomicReference<ByteString> legacyProps = new AtomicReference<>();

		return defaultHapiSpec("CheckTopicCreateThrottles")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES).saveTo(BACKUP_LOC);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						})
				).when(
						updateToNewThrottlePropsFrom(THROTTLE_PROPS_FOR_CHECK),
						sleepFor(5_000L)
				).then(
						runWithProvider(topicCreates())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get),
						withOpContext((spec, opLog) -> {
							opLog.info("Total ops accepted in {} {} = {} ==> {}tps vs {}tps expected",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									String.format("%.3f", 1.0 * spec.finalAdhoc() / duration.get()),
									EXPECTED_TOPIC_CREATE_TPS);
						}),
						fileUpdate(APP_PROPERTIES).contents(ignore -> legacyProps.get())
				);
	}

	private HapiApiSpec checkContractThrottles() {
		AtomicReference<ByteString> legacyProps = new AtomicReference<>();

		return defaultHapiSpec("CheckContractThrottles")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES).saveTo(BACKUP_LOC);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						})
				).when(
						updateToNewThrottlePropsFrom(THROTTLE_PROPS_FOR_CHECK),
						sleepFor(5_000L)
				).then(
						runWithProvider(contractOps())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get),
						withOpContext((spec, opLog) -> {
							opLog.info("Total ops accepted in {} {} = {} ==> {}tps vs {}tps expected",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									String.format("%.3f", 1.0 * spec.finalAdhoc() / duration.get()),
									EXPECTED_SC_TPS);
						}),
						fileUpdate(APP_PROPERTIES).contents(ignore -> legacyProps.get())
				);
	}

	private HapiApiSpec checkFileThrottles() {
		AtomicReference<ByteString> legacyProps = new AtomicReference<>();

		return defaultHapiSpec("CheckFileThrottles")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES).saveTo(BACKUP_LOC);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						})
				).when(
						updateToNewThrottlePropsFrom(THROTTLE_PROPS_FOR_CHECK),
						sleepFor(5_000L)
				).then(
						runWithProvider(fileOps())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get),
						withOpContext((spec, opLog) -> {
							opLog.info("Total ops accepted in {} {} = {} ==> {}tps vs {}tps expected",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									String.format("%.3f", 1.0 * spec.finalAdhoc() / duration.get()),
									EXPECTED_FILE_TPS);
						}),
						fileUpdate(APP_PROPERTIES).contents(ignore -> legacyProps.get())
				);
	}

	private HapiApiSpec checkOtherHcsThrottles() {
		AtomicReference<ByteString> legacyProps = new AtomicReference<>();

		return defaultHapiSpec("CheckOtherHcsThrottles")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES).saveTo(BACKUP_LOC);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						})
				).when(
						updateToNewThrottlePropsFrom(THROTTLE_PROPS_FOR_CHECK),
						sleepFor(5_000L)
				).then(
						runWithProvider(otherHcsOps())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get),
						withOpContext((spec, opLog) -> {
							opLog.info("Total ops accepted in {} {} = {} ==> {}tps vs {}tps expected",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									String.format("%.3f", 1.0 * spec.finalAdhoc() / duration.get()),
									EXPECTED_OTHER_HCS_TPS);
						}),
						fileUpdate(APP_PROPERTIES).contents(ignore -> legacyProps.get())
				);
	}

	private HapiApiSpec checkCryptoThrottles() {
		AtomicReference<ByteString> legacyProps = new AtomicReference<>();

		return defaultHapiSpec("CheckCryptoThrottles")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES).saveTo(BACKUP_LOC);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						})
				).when(
						updateToNewThrottlePropsFrom(THROTTLE_PROPS_FOR_CHECK),
						sleepFor(5_000L)
				).then(
						runWithProvider(cryptoOps())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get),
						withOpContext((spec, opLog) -> {
							opLog.info("Total ops accepted in {} {} = {} ==> {}tps vs {}tps expected",
									duration.get(),
									unit.get(),
									spec.finalAdhoc(),
									String.format("%.3f", 1.0 * spec.finalAdhoc() / duration.get()),
									EXPECTED_CRYPTO_TPS);
						}),
						fileUpdate(APP_PROPERTIES).contents(ignore -> legacyProps.get())
				);
	}

	private Function<HapiApiSpec, OpProvider> createTopicOps() {
		long INITIAL_BALANCE = 1_000 * 100_000_000L;
		final var nextTopic = new AtomicInteger();

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian").balance(INITIAL_BALANCE).withRecharging()
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = createTopic(String.format("ofGeneralInterest%d", nextTopic.getAndIncrement()))
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> otherHcsOps() {
		long INITIAL_BALANCE = 1_000 * 100_000_000L;

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian").balance(INITIAL_BALANCE).withRecharging(),
						createTopic("ofGeneralInterest")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = submitMessageTo("ofGeneralInterest")
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> contractOps() {
		long INITIAL_BALANCE = 1_000 * 100_000_000L;

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian").balance(INITIAL_BALANCE).withRecharging(),
						fileCreate("bytecode").path("src/main/resource/Multipurpose.bin"),
						contractCreate("multi").bytecode("bytecode")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = contractCall("multi")
						.deferStatusResolution()
						.payingWith("civilian")
						.sending(1L)
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> fileOps() {
		long INITIAL_BALANCE = 1_000 * 100_000_000L;
		var r = new SplittableRandom();

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian").balance(INITIAL_BALANCE).withRecharging(),
						fileCreate("unknown")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = fileUpdate("unknown")
						.contents(ignore ->  {
							var randomBytes = new byte[32];
							r.nextBytes(randomBytes);
							return ByteString.copyFrom(randomBytes);
						})
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> cryptoOps() {
		long INITIAL_BALANCE = 1_000 * 100_000_000L;

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian").balance(INITIAL_BALANCE).withRecharging(),
						cryptoCreate("nobody")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = cryptoTransfer(tinyBarsFromTo("civilian", "nobody", 1))
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> topicCreates() {
		long INITIAL_BALANCE = 1_000 * 100_000_000L;
		AtomicInteger topicId = new AtomicInteger(1_234_567);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("civilian").balance(INITIAL_BALANCE).withRecharging()
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = createTopic("topic" + topicId.getAndIncrement())
						.deferStatusResolution()
						.payingWith("civilian")
						.hasPrecheckFrom(OK, BUSY);
				return Optional.of(op);
			}
		};
	}

	private HapiApiSpec restoreFromBkup() {
		try {
			var fromBkup = ByteString.copyFrom(Files.readAllBytes(Paths.get(BACKUP_LOC)));

			return defaultHapiSpec("RestoreFromBkup")
					.given().when().then(
							fileUpdate(APP_PROPERTIES).contents(fromBkup)
					);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private HapiApiSpec saveToBkup() {
		return defaultHapiSpec("SaveToBkup")
				.given( ).when( ).then(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES).saveTo(BACKUP_LOC);
							allRunFor(spec, lookup);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
