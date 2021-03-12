package com.hedera.services.bdd.suites.perf;

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
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.nft.NftUseCase;
import com.hedera.services.bdd.suites.nft.NftXchange;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiSpecSetup.asStringMap;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.nft.NftXchange.civilianKeyNo;
import static com.hedera.services.bdd.suites.nft.NftXchange.civilianNo;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

public class NftXchangeLoadProvider extends HapiApiSuite {
	public static final AtomicInteger SETUP_PAUSE_MS = new AtomicInteger();
	public static final AtomicInteger POST_SETUP_PAUSE_SECS = new AtomicInteger();
	public static final AtomicInteger MAX_OPS_IN_PARALLEL = new AtomicInteger();
	public static final AtomicInteger NUM_CIVILIAN_KEYS = new AtomicInteger();

	private static final Logger log = LogManager.getLogger(NftXchangeLoadProvider.class);

	private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);
	private AtomicReference<List<NftUseCase>> nftUseCases = new AtomicReference<>();
	private AtomicInteger numCivilians = new AtomicInteger();

	public static void main(String... args) {
		new NftXchangeLoadProvider().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runNftXchange(),
				}
		);
	}

	private HapiApiSpec runNftXchange() {
		return HapiApiSpec.defaultHapiSpec("RunNftXchange")
				.given(
						stdMgmtOf(duration, unit, maxOpsPerSec)
				).when(
						runWithProvider(nftXchangeFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
								.postSetupSleepSecs(POST_SETUP_PAUSE_SECS::get)
				).then(
						withOpContext((spec, opLog) -> {
							for (NftUseCase useCase : nftUseCases.get()) {
								for (NftXchange xchange : useCase.getXchanges()) {
									allRunFor(
											spec,
											getAccountBalance(xchange.getTreasury()).logged());
								}
							}
						})
				);
	}

	private Function<HapiApiSpec, OpProvider> nftXchangeFactory() {
		var numUseCases = new AtomicInteger(0);
		var nextUseCase = new AtomicInteger(0);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				var ciProps = spec.setup().ciPropertiesMap();

				SETUP_PAUSE_MS.set(ciProps.getInteger("setupPauseMs"));
				MAX_OPS_IN_PARALLEL.set(ciProps.getInteger("setupParallelism"));
				POST_SETUP_PAUSE_SECS.set(ciProps.getInteger("postSetupPauseSecs"));
				NUM_CIVILIAN_KEYS.set(ciProps.getInteger("numCivilianKeys"));

				nftUseCases.set(parseAllFrom(ciProps.get("nftUseCases")));
				numUseCases.set(nftUseCases.get().size());
				numCivilians.set(ciProps.getInteger("numCivilians"));

				List<HapiSpecOperation> initializers = new ArrayList<>();

				addCivilians(initializers, numCivilians.get());

				var nftTypeId = new AtomicInteger(1);
				for (NftUseCase useCase : nftUseCases.get()) {
					initializers.addAll(useCase.initializers(nftTypeId));
				}

				return initializers;
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var the = nextUseCase.getAndUpdate(i -> (i + 1) % numUseCases.get());
				return Optional.of(nftUseCases.get()
						.get(the)
						.nextOp());
			}
		};
	}

	private List<NftUseCase> parseAllFrom(String configList) {
		configList = configList.substring(1, configList.length() - 1);
		String[] useCaseConfig = configList.split("[|]");
		return Arrays.stream(useCaseConfig).map(this::parseFrom).collect(toList());
	}

	private NftUseCase parseFrom(String config) {
		int openI = config.indexOf("{");
		String useCase = config.substring(0, openI);
		String props = config
				.substring(openI + 1, config.indexOf("}"))
				.replaceAll("_", "")
				.replaceAll("@", "=")
				.replaceAll("CC", ",");

		var propsMap = new MapPropertySource(asStringMap(props));
		return new NftUseCase(
				propsMap.getInteger("users"),
				propsMap.getInteger("serialNos"),
				propsMap.getInteger("frequency"),
				useCase,
				propsMap.getBoolean("swapHbar"));
	}

	private static void addCivilians(List<HapiSpecOperation> init, int n) {
		final int numCivs = n;

		init.add(withOpContext((spec, opLog) -> opLog.info("Initializing {} civilians...", numCivs)));
		for (int i = 0, keyN = NUM_CIVILIAN_KEYS.get(); i < keyN; i++) {
			init.add(newKeyNamed(civilianKeyNo(i)));
		}

		AtomicInteger soFar = new AtomicInteger(0);
		while (n > 0) {
			int nextParallelism = Math.min(n, MAX_OPS_IN_PARALLEL.get());
			init.add(inParallel(IntStream.range(0, nextParallelism)
					.mapToObj(i -> cryptoCreate(civilianNo(soFar.get() + i))
							.noLogging().deferStatusResolution().forgettingKey()
							.key(civilianKeyNo(soFar.get() + i))
							.hasRetryPrecheckFrom(NOISY_RETRY_PRECHECKS)
							.blankMemo()
							.emptyBalance()).toArray(HapiSpecOperation[]::new)));
			init.add(sleepFor(NftXchangeLoadProvider.SETUP_PAUSE_MS.get())
					.because("can't create civilians too fast!"));
			n -= nextParallelism;
			soFar.getAndAdd(nextParallelism);
		}

		init.add(withOpContext((spec, opLog) -> opLog.info("...finished initializing {}", numCivs)));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
