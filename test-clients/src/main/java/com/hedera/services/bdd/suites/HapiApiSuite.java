package com.hedera.services.bdd.suites;

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

import com.google.common.math.Stats;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.stats.HapiStats;
import com.hedera.services.bdd.spec.stats.OpObs;
import com.hedera.services.bdd.spec.stats.QueryObs;
import com.hedera.services.bdd.spec.stats.ThroughputObs;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseType;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_FAILED;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_PASSED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static java.util.stream.Collectors.toList;

public abstract class HapiApiSuite {
	protected abstract Logger getResultsLogger();
	protected abstract List<HapiApiSpec> getSpecsInSuite();

	private static final Random r = new Random();

	protected String salted(String symbol) {
		return symbol + r.nextInt(1_234_567);
	}

	public static final long ONE_HBAR = 100_000_000L;
	public static final long A_HUNDRED_HBARS = 100 * ONE_HBAR;

	private List<HapiApiSpec> finalSpecs = Collections.EMPTY_LIST;

	public List<HapiApiSpec> getFinalSpecs() {
		return finalSpecs;
	}
	public boolean leaksState() { return false; }
	private boolean reportStats = false;
	public void setReportStats(boolean reportStats) {
		this.reportStats = reportStats;
	}
	public boolean hasInterestingStats() {
		return false;
	}

	public static final Key EMPTY_KEY = Key.newBuilder().setKeyList(KeyList.newBuilder().build()).build();
	public static final String NONSENSE_KEY = "Jabberwocky!";

	public static final String NODE = HapiSpecSetup.getDefaultInstance().defaultNodeName();

	public static final String MASTER = HapiSpecSetup.getDefaultInstance().strongControlName();
	public static final String FUNDING = HapiSpecSetup.getDefaultInstance().fundingAccountName();
	public static final String GENESIS = HapiSpecSetup.getDefaultInstance().genesisAccountName();

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
	public static final String PATH_TO_LOOKUP_BYTECODE = bytecodePath("BalanceLookup");
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
		if (reportStats) {
			reportStats(log);
		}
	}

	private void reportStats(Logger log) {
		List<HapiApiSpec> okSpecs = finalSpecs.stream().filter(HapiApiSpec::OK).collect(toList());
		if (okSpecs.isEmpty()) { return; }
		List<OpObs> opStats = okSpecs
				.stream()
				.filter(HapiApiSpec::OK)
				.flatMap(spec -> spec.registry().stats().stream())
				.collect(toList());
		boolean consensusLatenciesAvailable = okSpecs.stream().allMatch(spec -> spec.setup().measureConsensusLatency());
		HapiStats stats = new HapiStats(consensusLatenciesAvailable, opStats);

		log.info("-------------- STATS FOR SPECS IN " + name() + " SUITE --------------");
		log.info("# of operations = " + stats.numOps()
				+ " (" + stats.numTxns() + " txns, " + stats.numQueries() + " queries)");
		log.info("  * " + stats.countDetails());

		Stats txnResponseLatencyStats = stats.txnResponseLatencyStats();
		log.info("Txn response latency " + asNormalApproximation(txnResponseLatencyStats));
		for (HederaFunctionality txnType : stats.txnTypes()) {
			Stats detailLatencyStats = stats.txnResponseLatencyStatsFor(txnType);
			log.info("  * " + txnType + "[n=" + detailLatencyStats.count() + "] "
					+ asNormalApproximation(detailLatencyStats));
		}
		if (consensusLatenciesAvailable) {
			Stats consensusLatencyStats = stats.txnResponseLatencyStatsFor();
			log.info("Txn consensus latency " + asNormalApproximation(consensusLatencyStats));
		}
		Stats queryResponseLatencyStats = stats.queryResponseLatencyStats();
		log.info("Query response latency " + asNormalApproximation(queryResponseLatencyStats));
		for (HederaFunctionality queryType : stats.queryTypes()) {
			for (ResponseType type : EnumSet.of(COST_ANSWER, ANSWER_ONLY)) {
				Stats costAnswerLatency = stats.statsProjectedFor(
						QueryObs.class,
						(QueryObs obs) -> obs.functionality().equals(queryType) && obs.type().equals(type),
						QueryObs::getResponseLatency);
				if (costAnswerLatency.count() > 0L) {
					log.info("  * " + queryType + ":" + type + "[n=" + costAnswerLatency.count() + "] "
							+ asNormalApproximation(costAnswerLatency));
				}
			}
		}

		final AtomicBoolean headerPrinted = new AtomicBoolean(false);
		okSpecs.stream().filter(spec -> !spec.registry().throughputObs().isEmpty()).forEach(spec -> {
			if (!headerPrinted.get()) {
				log.info("-------------- THROUGHPUT OBS FROM SPECS IN " + name() + " SUITE --------------");
				headerPrinted.set(true);
			}
			log.info("From spec '" + spec.getName() + "':");
			List<ThroughputObs> throughputObs = spec.registry().throughputObs();
			for (ThroughputObs obs : throughputObs) {
				log.info("  * " + obs.getName() + " :: " + obs.summary());
			}
		});
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

	public static final String bytecodePath(String contract) {
		return String.format("src/main/resource/testfiles/%s.bin", contract);
	}

	public static boolean cacheRecordsAreAddedToState() {
		return "true".equals(Optional.ofNullable(System.getenv("ADD_CACHE_RECORD_TO_STATE")).orElse(""));
	}
}
