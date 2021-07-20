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

import com.google.common.math.Stats;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_FAILED;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_PASSED;

public abstract class HapiApiSuite {
	protected abstract Logger getResultsLogger();
	protected abstract List<HapiApiSpec> getSpecsInSuite();

	private static final Random r = new Random();

	public static String salted(String str) {
		return str + r.nextInt(1_234_567);
	}

	public static final long ONE_HBAR = 100_000_000L;
	public static final long THOUSAND_HBAR = 1_000 * ONE_HBAR;
	public static final long ONE_HUNDRED_HBARS = 100 * ONE_HBAR;
	public static final long ONE_MILLION_HBARS = 1_000_000L * ONE_HBAR;
	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
	public static String TOKEN_TREASURY = "treasury";

	private List<HapiApiSpec> finalSpecs = Collections.EMPTY_LIST;

	public List<HapiApiSpec> getFinalSpecs() {
		return finalSpecs;
	}
	public boolean leaksState() { return false; }

	public static final Key EMPTY_KEY = Key.newBuilder().setKeyList(KeyList.newBuilder().build()).build();
	public static final String NONSENSE_KEY = "Jabberwocky!";
	public static final String ZERO_BYTE_MEMO = "\u0000kkkk";

	public static final String NODE = HapiSpecSetup.getDefaultInstance().defaultNodeName();


	public static final String TRANSFER_LIST = "transferList";
	public static final String TOKEN_TRANSFER_LIST = "tokenTransferLists";
	public static final String ASSESSED_CUSTOM_FEES = "assessed_custom_fees";
	public static final String HBAR_TOKEN_SENTINEL = "HBAR";

	public static final String SYSTEM_ADMIN = HapiSpecSetup.getDefaultInstance().strongControlName();
	public static final String FREEZE_ADMIN = HapiSpecSetup.getDefaultInstance().freezeAdminName();
	public static final String FUNDING = HapiSpecSetup.getDefaultInstance().fundingAccountName();
	public static final String GENESIS = HapiSpecSetup.getDefaultInstance().genesisAccountName();
	public static final String DEFAULT_PAYER = HapiSpecSetup.getDefaultInstance().defaultPayerName();

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
	public static final long ADEQUATE_FUNDS = 10_000_000_000L;

	public String name() {
		String simpleName = this.getClass().getSimpleName();

		return !simpleName.endsWith("Suite")
				? simpleName
				: simpleName.substring(0, simpleName.length() - "Suite".length());
	}

	public enum FinalOutcome { SUITE_PASSED, SUITE_FAILED }
	protected FinalOutcome finalOutcomeFor(List<HapiApiSpec> completedSpecs) {
		return completedSpecs.stream().allMatch(HapiApiSpec::OK) ? SUITE_PASSED : SUITE_FAILED;
	}

	public FinalOutcome runSuiteAsync() {
		return runSuite(this::runAsync);
	}
	public FinalOutcome runSuiteSync() {
		return runSuite(this::runSync);
	}

	private FinalOutcome runSuite(Consumer<List<HapiApiSpec>> runner) {
		getResultsLogger().info("-------------- STARTING " + name() + " SUITE --------------");
		List<HapiApiSpec> specs = getSpecsInSuite();
		specs.stream().forEach(spec -> spec.setSuitePrefix(name()));
		runner.accept(specs);
		finalSpecs = specs;
		summarizeResults(getResultsLogger());
		HapiApiClients.tearDown();
		return finalOutcomeFor(finalSpecs);
	}

	public static HapiSpecOperation[] flattened(Object... ops) {
		return Stream
				.of(ops)
				.map(op -> (op instanceof HapiSpecOperation)
						? new HapiSpecOperation[] { (HapiSpecOperation)op }
						: ((op instanceof List) ? ((List)op).toArray(new HapiSpecOperation[0]) : (HapiSpecOperation[])op))
				.flatMap(Stream::of)
				.toArray(n -> new HapiSpecOperation[n]);
	}
	protected List<HapiApiSpec> allOf(List<HapiApiSpec>... specLists) {
		return Arrays.stream(specLists).flatMap(List::stream).collect(Collectors.toList());
	}

	protected HapiSpecOperation[] asOpArray(int N, Function<Integer, HapiSpecOperation> factory) {
		return IntStream.range(0, N).mapToObj(i -> factory.apply(i)).toArray(n -> new HapiSpecOperation[n]);
	}
	protected HapiQueryOp<?>[] asQueryOpArray(int N, Function<Integer, HapiQueryOp<?>> factory) {
		return IntStream.range(0, N).mapToObj(i -> factory.apply(i)).toArray(n -> new HapiQueryOp<?>[n]);
	}

	private void summarizeResults(Logger log) {
		log.info("-------------- RESULTS OF " + name() + " SUITE --------------");
		for (HapiApiSpec spec : finalSpecs) {
			log.info(spec);
		}
	}

	private String asNormalApproximation(Stats stats) {
		if (stats.count() > 1) {
			return "~ \uD835\uDCA9(μ=" + rounded(stats.mean())
					+ "ms, σ=" + rounded(stats.sampleStandardDeviation()) + "ms)";
		} else {
			return "= " + (long)stats.sum() + "ms";
		}
	}

	private String rounded(double v) {
		return String.format("%.2f", v);
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

	public static boolean cacheRecordsAreAddedToState() {
		return "true".equals(Optional.ofNullable(System.getenv("ADD_CACHE_RECORD_TO_STATE")).orElse(""));
	}
}
