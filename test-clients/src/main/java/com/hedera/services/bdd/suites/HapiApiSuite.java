package com.hedera.services.bdd.suites;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_FAILED;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_PASSED;

public abstract class HapiApiSuite {
	public enum FinalOutcome {
		SUITE_PASSED, SUITE_FAILED
	}

	private static final Random r = new Random();

	protected abstract Logger getResultsLogger();
	public abstract List<HapiApiSpec> getSpecsInSuite();

	public static final Key EMPTY_KEY = Key.newBuilder().setKeyList(KeyList.newBuilder().build()).build();

	private static final int BYTES_PER_KB = 1024;
	public static final int MAX_CALL_DATA_SIZE = 6 * BYTES_PER_KB;
	public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
	public static final long ADEQUATE_FUNDS = 10_000_000_000L;
	public static final long ONE_HBAR = 100_000_000L;
	public static final long TINY_PARTS_PER_WHOLE = 100_000_000L;
	public static final long FIVE_HBARS = 5 * ONE_HBAR;
	public static final long ONE_HUNDRED_HBARS = 100 * ONE_HBAR;
	public static final long THOUSAND_HBAR = 1_000 * ONE_HBAR;
	public static final long ONE_MILLION_HBARS = 1_000_000L * ONE_HBAR;
	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;

	public static final String RELAYER = "RELAYER";
	public static final KeyShape SECP_256K1_SHAPE = KeyShape.SECP256K1;
	public static final String SECP_256K1_SOURCE_KEY = "secp256k1Alias";
	public static final String SECP_256K1_RECEIVER_SOURCE_KEY = "secp256k1ReceiverAlias";
	public static final String TOKEN_TREASURY = "treasury";
	public static final String NONSENSE_KEY = "Jabberwocky!";
	public static final String ZERO_BYTE_MEMO = "\u0000kkkk";
	public static final String NODE = HapiSpecSetup.getDefaultInstance().defaultNodeName();
	public static final String HBAR_TOKEN_SENTINEL = "HBAR";
	public static final String SYSTEM_ADMIN = HapiSpecSetup.getDefaultInstance().strongControlName();
	public static final String FREEZE_ADMIN = HapiSpecSetup.getDefaultInstance().freezeAdminName();
	public static final String FUNDING = HapiSpecSetup.getDefaultInstance().fundingAccountName();
	public static final String STAKING_REWARD = HapiSpecSetup.getDefaultInstance().stakingRewardAccountName();
	public static final String NODE_REWARD = HapiSpecSetup.getDefaultInstance().nodeRewardAccountName();
	public static final String GENESIS = HapiSpecSetup.getDefaultInstance().genesisAccountName();
	public static final String DEFAULT_PAYER = HapiSpecSetup.getDefaultInstance().defaultPayerName();
	public static final String DEFAULT_CONTRACT_SENDER = "DEFAULT_CONTRACT_SENDER";
	public static final String DEFAULT_CONTRACT_RECEIVER = "DEFAULT_CONTRACT_RECEIVER";
	public static final String ADDRESS_BOOK_CONTROL = HapiSpecSetup.getDefaultInstance().addressBookControlName();
	public static final String FEE_SCHEDULE_CONTROL = HapiSpecSetup.getDefaultInstance().feeScheduleControlName();
	public static final String EXCHANGE_RATE_CONTROL = HapiSpecSetup.getDefaultInstance().exchangeRatesControlName();
	public static final String SYSTEM_DELETE_ADMIN = HapiSpecSetup.getDefaultInstance().systemDeleteAdminName();
	public static final String SYSTEM_UNDELETE_ADMIN = HapiSpecSetup.getDefaultInstance().systemUndeleteAdminName();
	public static final String NODE_DETAILS = HapiSpecSetup.getDefaultInstance().nodeDetailsName();
	public static final String ADDRESS_BOOK = HapiSpecSetup.getDefaultInstance().addressBookName();
	public static final String EXCHANGE_RATES = HapiSpecSetup.getDefaultInstance().exchangeRatesName();
	public static final String FEE_SCHEDULE = HapiSpecSetup.getDefaultInstance().feeScheduleName();
	public static final String APP_PROPERTIES = HapiSpecSetup.getDefaultInstance().appPropertiesFile();
	public static final String API_PERMISSIONS = HapiSpecSetup.getDefaultInstance().apiPermissionsFile();
	public static final String UPDATE_ZIP_FILE = HapiSpecSetup.getDefaultInstance().updateFeatureName();
	public static final String THROTTLE_DEFS = HapiSpecSetup.getDefaultInstance().throttleDefinitionsName();

	public static final HapiSpecSetup DEFAULT_PROPS = HapiSpecSetup.getDefaultInstance();
	public static final String ETH_SUFFIX = "_Eth";

	private boolean deferResultsSummary = false;
	private boolean tearDownClientsAfter = true;
	private List<HapiApiSpec> finalSpecs = Collections.emptyList();
	private int suiteRunnerCounter = 0;

	public String name() {
		String simpleName = this.getClass().getSimpleName();

		simpleName =  !simpleName.endsWith("Suite")
				? simpleName
				: simpleName.substring(0, simpleName.length() - "Suite".length());
//		return suiteRunnerCounter == 2 ? simpleName.concat(ETH_SUFFIX) : simpleName;
		return simpleName;
	}

	public List<HapiApiSpec> getFinalSpecs() {
		return finalSpecs;
	}

	public boolean canRunConcurrent() {
		return false;
	}

	public void skipClientTearDown() {
		this.tearDownClientsAfter = false;
	}

	public void deferResultsSummary() {
		this.deferResultsSummary = true;
	}

	public boolean getDeferResultsSummary() {
		return deferResultsSummary;
	}

	public void summarizeDeferredResults() {
		if (getDeferResultsSummary()) {
			deferResultsSummary = false;
			summarizeResults(getResultsLogger());
		}
	}

	public FinalOutcome runSuiteAsync() {
//		runSuite(this::runAsync);
//		getResultsLogger().info(System.lineSeparator());
		return runSuite(this::runAsync);
	}

	public FinalOutcome runSuiteSync() {
//		runSuite(this::runSync);
//		getResultsLogger().info(System.lineSeparator());
		return runSuite(this::runSync);
	}

	protected FinalOutcome finalOutcomeFor(List<HapiApiSpec> completedSpecs) {
		return completedSpecs.stream().allMatch(HapiApiSpec::OK) ? SUITE_PASSED : SUITE_FAILED;
	}

	private FinalOutcome runSuite(Consumer<List<HapiApiSpec>> runner) {
		suiteRunnerCounter++;
		if (!getDeferResultsSummary()) {
			getResultsLogger().info("-------------- STARTING " + name() + " SUITE --------------");
		}

		List<HapiApiSpec> specs = getSpecsInSuite();
		final var name = name();
		specs.forEach(spec -> spec.setSuitePrefix(name));
		runner.accept(specs);
		finalSpecs = specs;
		summarizeResults(getResultsLogger());
		if (tearDownClientsAfter) {
			HapiApiClients.tearDown();
		}
		return finalOutcomeFor(finalSpecs);
	}

	public static HapiSpecOperation[] flattened(Object... ops) {
		return Stream
				.of(ops)
				.map(op -> (op instanceof HapiSpecOperation)
						? new HapiSpecOperation[] { (HapiSpecOperation) op }
						: ((op instanceof List) ? ((List) op).toArray(
						new HapiSpecOperation[0]) : (HapiSpecOperation[]) op))
				.flatMap(Stream::of)
				.toArray(HapiSpecOperation[]::new);
	}

	@SafeVarargs
	protected final List<HapiApiSpec> allOf(final List<HapiApiSpec>... specLists) {
		return Arrays.stream(specLists).flatMap(List::stream).toList();
	}

	protected HapiSpecOperation[] asOpArray(int N, Function<Integer, HapiSpecOperation> factory) {
		return IntStream.range(0, N).mapToObj(factory::apply).toArray(HapiSpecOperation[]::new);
	}

	private void summarizeResults(Logger log) {
		if (getDeferResultsSummary()) {
			return;
		}
		log.info("-------------- STARTING " + name() + " SUITE --------------");
		log.info("-------------- RESULTS OF " + name() + " SUITE --------------");
		for (HapiApiSpec spec : finalSpecs) {
			log.info(spec);
		}
	}

	private void runSync(Iterable<HapiApiSpec> specs) {
		StreamSupport
				.stream(specs.spliterator(), false)
				.forEach(Runnable::run);
	}

	private void runAsync(Iterable<HapiApiSpec> specs) {
		CompletableFuture[] futures = StreamSupport
				.stream(specs.spliterator(), false)
				.map(CompletableFuture::runAsync)
				.toArray(n -> new CompletableFuture[n]);
		CompletableFuture.allOf(futures).join();
	}

	public static String salted(String str) {
		return str + r.nextInt(1_234_567);
	}
}
