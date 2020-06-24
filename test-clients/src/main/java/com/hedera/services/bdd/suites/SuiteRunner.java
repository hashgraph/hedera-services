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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.consensus.ChunkingSuite;
import com.hedera.services.bdd.suites.consensus.TopicCreateSuite;
import com.hedera.services.bdd.suites.consensus.TopicDeleteSuite;
import com.hedera.services.bdd.suites.consensus.SubmitMessageSuite;
import com.hedera.services.bdd.suites.consensus.TopicGetInfoSuite;
import com.hedera.services.bdd.suites.consensus.TopicUpdateSuite;
import com.hedera.services.bdd.suites.consensus.ConsensusThrottlesSuite;
import com.hedera.services.bdd.suites.contract.ChildStorageSpec;
import com.hedera.services.bdd.suites.contract.ContractCallSuite;
import com.hedera.services.bdd.suites.contract.DeprecatedContractKeySuite;
import com.hedera.services.bdd.suites.contract.NewOpInConstructorSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.file.FetchSystemFiles;
import com.hedera.services.bdd.suites.freeze.FreezeSuite;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.file.PermissionSemanticsSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.freeze.UpdateServerFiles;
import com.hedera.services.bdd.suites.issues.Issue2144Spec;
import com.hedera.services.bdd.suites.issues.IssueXXXXSpec;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.perf.ContractCallLoadTest;
import com.hedera.services.bdd.suites.perf.CryptoTransferLoadTest;
import com.hedera.services.bdd.suites.perf.FileUpdateLoadTest;
import com.hedera.services.bdd.suites.perf.MixedTransferAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.MixedTransferCallAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.SubmitMessageLoadTest;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.ThresholdRecordCreationSuite;

import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.streaming.RecordStreamValidation;
import com.hedera.services.bdd.suites.throttling.LegacyToBucketTransitionSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiSpecSetup.NodeSelection.FIXED;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static com.hedera.services.bdd.spec.HapiSpecSetup.TlsConfig.*;
import static java.util.stream.Collectors.toMap;

public class SuiteRunner {
	private static final Logger log = LogManager.getLogger(SuiteRunner.class);
	private static final int SUITE_NAME_WIDTH = 32;

	private static final HapiSpecSetup.TlsConfig DEFAULT_TLS_CONFIG = OFF;
	private static final HapiSpecSetup.NodeSelection DEFAULT_NODE_SELECTOR = FIXED;

	private static final int EXPECTED_DEV_NETWORK_SIZE = 3;
	private static final int EXPECTED_CI_NETWORK_SIZE = 4;

	public static int expectedNetworkSize = EXPECTED_DEV_NETWORK_SIZE;

	static final Map<String, HapiApiSuite[]> CATEGORY_MAP = new HashMap<>() {{
		/* Umbrella Redux */
		put("UmbrellaRedux", aof(new UmbrellaRedux()));
		/* Load tests. */
		put("FileUpdateLoadTest", aof(new FileUpdateLoadTest()));
		put("ContractCallLoadTest", aof(new ContractCallLoadTest()));
		put("SubmitMessageLoadTest", aof(new SubmitMessageLoadTest()));
		put("CryptoTransferLoadTest", aof(new CryptoTransferLoadTest()));
		put("MixedTransferAndSubmitLoadTest", aof(new MixedTransferAndSubmitLoadTest()));
		put("MixedTransferCallAndSubmitLoadTest", aof(new MixedTransferCallAndSubmitLoadTest()));
		/* Functional tests - CONSENSUS */
		put("TopicCreateSpecs", aof(new TopicCreateSuite()));
		put("TopicDeleteSpecs", aof(new TopicDeleteSuite()));
		put("TopicUpdateSpecs", aof(new TopicUpdateSuite()));
		put("SubmitMessageSpecs", aof(new SubmitMessageSuite()));
		put("HCSTopicFragmentationSuite", aof(new ChunkingSuite()));
		put("TopicGetInfoSpecs", aof(new TopicGetInfoSuite()));
		put("ConsensusThrottlesSpecs", aof(new ConsensusThrottlesSuite()));
		/* Functional tests - FILE */
		put("PermissionSemanticsSpec", aof(new PermissionSemanticsSpec()));
		/* Functional tests - CRYPTO */
		put("CryptoCreateSuite", aof(new CryptoCreateSuite()));
		/* Functional tests - CONTRACTS */
		put("NewOpInConstructorSpecs", aof(new NewOpInConstructorSuite()));
		put("DeprecatedContractKeySpecs", aof(new DeprecatedContractKeySuite()));
		put("MultipleSelfDestructsAreSafe", aof(new IssueXXXXSpec()));
		put("ChildStorageSpecs", aof(new ChildStorageSpec()));
		/* Functional tests - MIXED (record emphasis) */
		put("ThresholdRecordCreationSpecs", aof(new ThresholdRecordCreationSuite()));
		put("CryptoRecordSanityChecks", aof(new CryptoRecordsSanityCheckSuite()));
		put("FileRecordSanityChecks", aof(new FileRecordsSanityCheckSuite()));
		put("ContractRecordSanityChecks", aof(new ContractRecordsSanityCheckSuite()));
		put("ContractCallSuite", aof(new ContractCallSuite()));
		put("ProtectedFilesUpdateSuite", aof(new ProtectedFilesUpdateSuite()));
		/* Record validation. */
		put("RecordStreamValidation", aof(new RecordStreamValidation()));
		/* Fee characterization. */
		put("ControlAccountsExemptForUpdates", aof(new SpecialAccountsAreExempted()));
		/* System files. */
		put("FetchSystemFiles", aof(new FetchSystemFiles()));
		/* Throttling */
		put("BucketAndLegacyThrottlingSpec", aof(new LegacyToBucketTransitionSpec()));
		/* Network metadata. */
		put("VersionInfoSpec", aof(new VersionInfoSpec()));
		put("FreezeSuite", aof(new FreezeSuite()));
		/* Authorization. */
		put("SuperusersAreNeverThrottled", aof(new Issue2144Spec()));
		put("SysDelSysUndelSpec", aof(new SysDelSysUndelSpec()));
		/* Freeze and update */
		put("UpdateServerFiles", aof(new UpdateServerFiles()));
	}};

	static boolean runAsync;
	static Set<String> argSet;
	static List<CategorySuites> targetCategories;
	static boolean globalPassFlag = true;

	private static final String TLS_ARG = "-TLS";
	private static final String NODE_SELECTOR_ARG = "-NODE";

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		String[] effArgs = trueArgs(args);
		log.info("Effective args :: " + List.of(effArgs));
		if (Stream.of(effArgs).anyMatch("-CI"::equals)) {
			expectedNetworkSize = EXPECTED_CI_NETWORK_SIZE;
			var tlsOverride = overrideOrDefault(effArgs, TLS_ARG, DEFAULT_TLS_CONFIG.toString());
			var nodeSelectorOverride = overrideOrDefault(effArgs, NODE_SELECTOR_ARG, DEFAULT_NODE_SELECTOR.toString());
			var otherOverrides = arbitraryOverrides(effArgs);
			HapiApiSpec.runInCiMode(
					System.getenv("NODES"),
					args[1],
					tlsOverride.substring(TLS_ARG.length() + 1),
					nodeSelectorOverride.substring(NODE_SELECTOR_ARG.length() + 1),
					otherOverrides);
		}
		boolean prohibitAsync = !Stream.of(effArgs).anyMatch("-A"::equals);
		Map<Boolean, List<String>> statefulCategories = Stream
				.of(effArgs)
				.filter(CATEGORY_MAP::containsKey)
				.collect(groupingBy(cat -> prohibitAsync || SuiteRunner.categoryLeaksState(CATEGORY_MAP.get(cat))));

		Map<String, List<CategoryResult>> byRunType = new HashMap<>();
		if (statefulCategories.get(Boolean.FALSE) != null) {
			runAsync = true;
			byRunType.put("async", runCategories(statefulCategories.get(Boolean.FALSE)));
		}
		if (statefulCategories.get(Boolean.TRUE) != null) {
			runAsync = false;
			byRunType.put("sync", runCategories(statefulCategories.get(Boolean.TRUE)));
		}
		summarizeResults(byRunType);

		System.exit(globalPassFlag ? 0 : 1);
	}

	private static String overrideOrDefault(String[] effArgs, String argPrefix, String defaultValue) {
		return Stream.of(effArgs)
				.filter(arg -> arg.startsWith(argPrefix))
				.findAny()
				.orElse(String.format("%s=%s", argPrefix, defaultValue));
	}

	private static Map<String, String> arbitraryOverrides(String[] effArgs) {
		var MISC_OVERRIDE_PATTERN = Pattern.compile("([^-].*)=(.*)");
		return Stream.of(effArgs)
				.map(arg -> MISC_OVERRIDE_PATTERN.matcher(arg))
				.filter(Matcher::matches)
				.collect(toMap(m -> m.group(1), m -> m.group(2)));
	}

	private static String[] trueArgs(String[] args) {
		String ciArgs = Optional.ofNullable(System.getenv("DSL_SUITE_RUNNER_ARGS")).orElse("");
		log.info("Args from CircleCI environment: |" + ciArgs + "|");
		return StringUtils.isNotEmpty(ciArgs)
			? Stream.of(args, new Object[] { "-CI" }, ciArgs.split("\\s+"))
				.flatMap(Stream::of)
				.toArray(n -> new String[n])
			: args;
	}

	private static List<CategoryResult> runCategories(List<String> args) {
		argSet = args.stream().collect(Collectors.toSet());
		collectTargetCategories();
		return runTargetCategories();
	}

	private static void summarizeResults(Map<String, List<CategoryResult>> byRunType) {
		byRunType.entrySet().stream().forEach(entry -> {
			log.info("============== " + entry.getKey() + " run results ==============");
			List<CategoryResult> results = entry.getValue();
			for (CategoryResult result : results) {
				log.info(result.summary);
				for (HapiApiSuite failed : result.failedSuites) {
					String specList = failed.getFinalSpecs().stream()
							.filter(HapiApiSpec::NOT_OK)
							.map(HapiApiSpec::toString)
							.collect(joining(", "));
					log.info("  --> Problems in suite '" + failed.name() + "' :: " + specList);
				}
				globalPassFlag &= result.failedSuites.isEmpty();
			}
		});
	}

	private static boolean categoryLeaksState(HapiApiSuite[] suites) {
		return Stream.of(suites).filter(HapiApiSuite::leaksState).findAny().isPresent();
	}

	private static List<CategoryResult> runTargetCategories() {
		if (runAsync) {
			return accumulateAsync(
					targetCategories.stream().toArray(n -> new CategorySuites[n]),
					sbc -> runSuitesAsync(sbc.category, sbc.suites));
		} else {
			return targetCategories.stream().map(sbc -> runSuitesSync(sbc.category, sbc.suites)).collect(toList());
		}
	}

	private static void collectTargetCategories() {
		targetCategories = CATEGORY_MAP
				.keySet()
				.stream()
				.filter(argSet::contains)
				.map(k -> new CategorySuites(rightPadded(k, SUITE_NAME_WIDTH), CATEGORY_MAP.get(k)))
				.collect(toList());
	}

	private static CategoryResult runSuitesAsync(String category, HapiApiSuite[] suites) {
		toggleStatsReporting(suites);
		List<FinalOutcome> outcomes = accumulateAsync(suites, HapiApiSuite::runSuiteAsync);
		List<HapiApiSuite> failed = IntStream.range(0, suites.length)
				.filter(i -> outcomes.get(i) != FinalOutcome.SUITE_PASSED)
				.mapToObj(i -> suites[i])
				.collect(toList());
		return summaryOf(category, suites, failed);
	}
	private static CategoryResult runSuitesSync(String category, HapiApiSuite[] suites) {
		toggleStatsReporting(suites);
		List<HapiApiSuite> failed = Stream.of(suites)
					.filter(suite -> suite.runSuiteSync() != FinalOutcome.SUITE_PASSED)
					.collect(toList());
		return summaryOf(category, suites, failed);
	}
	private static void toggleStatsReporting(HapiApiSuite[] suites) {
		Stream.of(suites).forEach(suite -> suite.setReportStats(suite.hasInterestingStats()));
	}
	private static CategoryResult summaryOf(String category, HapiApiSuite[] suites, List<HapiApiSuite> failed) {
		int numPassed = suites.length - failed.size();
		String summary = category + " :: " + numPassed + "/" + suites.length + " suites ran OK";
		return new CategoryResult(summary, failed);
	}

	private static <T, R> List<R> accumulateAsync(T[] inputs, Function<T, R> f) {
		final List<R> outputs = new ArrayList<>();
		for (int i = 0; i < inputs.length; i++) { outputs.add(null); }
		CompletableFuture<Void> future = CompletableFuture.allOf(
				IntStream.range(0, inputs.length)
						.mapToObj(i -> runAsync(() -> outputs.set(i, f.apply(inputs[i]))))
						.toArray(n -> new CompletableFuture[n]));
		future.join();
		return outputs;
	}

	static class CategoryResult {
		final String summary;
		final List<HapiApiSuite> failedSuites;

		public CategoryResult(String summary, List<HapiApiSuite> failedSuites) {
			this.summary = summary;
			this.failedSuites = failedSuites;
		}
	}

	static class CategorySuites {
		final String category;
		final HapiApiSuite[] suites;

		public CategorySuites(String category, HapiApiSuite[] suites) {
			this.category = category;
			this.suites = suites;
		}
	}

	static private String rightPadded(String s, int width) {
		if (s.length() == width) {
			return s;
		} else if (s.length() > width) {
			int cutLen = (width - 3) / 2;
			return s.substring(0, cutLen) + "..." + s.substring(s.length() - cutLen);
		} else {
			return s + IntStream.range(0, width - s.length()).mapToObj(ignore -> " ").collect(joining(""));
		}
	}

	@SafeVarargs
	public static <T> T[] aof(T... items) {
		return items;
	}
}
