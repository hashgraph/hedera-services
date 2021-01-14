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
import com.hedera.services.bdd.suites.consensus.ConsensusThrottlesSuite;
import com.hedera.services.bdd.suites.consensus.SubmitMessageSuite;
import com.hedera.services.bdd.suites.consensus.TopicCreateSuite;
import com.hedera.services.bdd.suites.consensus.TopicDeleteSuite;
import com.hedera.services.bdd.suites.consensus.TopicGetInfoSuite;
import com.hedera.services.bdd.suites.consensus.TopicUpdateSuite;
import com.hedera.services.bdd.suites.contract.BigArraySpec;
import com.hedera.services.bdd.suites.contract.ChildStorageSpec;
import com.hedera.services.bdd.suites.contract.ContractCallLocalSuite;
import com.hedera.services.bdd.suites.contract.ContractCallSuite;
import com.hedera.services.bdd.suites.contract.ContractCreateSuite;
import com.hedera.services.bdd.suites.contract.ContractDeleteSuite;
import com.hedera.services.bdd.suites.contract.ContractGetBytecodeSuite;
import com.hedera.services.bdd.suites.contract.ContractUpdateSuite;
import com.hedera.services.bdd.suites.contract.DeprecatedContractKeySuite;
import com.hedera.services.bdd.suites.contract.NewOpInConstructorSuite;
import com.hedera.services.bdd.suites.contract.OCTokenSpec;
import com.hedera.services.bdd.suites.contract.SmartContractFailFirstSpec;
import com.hedera.services.bdd.suites.contract.SmartContractInlineAssemblySpec;
import com.hedera.services.bdd.suites.contract.SmartContractPaySpec;
import com.hedera.services.bdd.suites.contract.SmartContractSelfDestructSpec;
import com.hedera.services.bdd.suites.crypto.CryptoCornerCasesSuite;
import com.hedera.services.bdd.suites.crypto.CryptoCreateForSuiteRunner;
import com.hedera.services.bdd.suites.crypto.CryptoCreateSuite;
import com.hedera.services.bdd.suites.crypto.CryptoDeleteSuite;
import com.hedera.services.bdd.suites.crypto.CryptoGetInfoRegression;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.bdd.suites.crypto.CryptoUpdateSuite;
import com.hedera.services.bdd.suites.crypto.QueryPaymentSuite;
import com.hedera.services.bdd.suites.fees.SpecialAccountsAreExempted;
import com.hedera.services.bdd.suites.file.ExchangeRateControlSuite;
import com.hedera.services.bdd.suites.file.FetchSystemFiles;
import com.hedera.services.bdd.suites.file.FileAppendSuite;
import com.hedera.services.bdd.suites.file.FileDeleteSuite;
import com.hedera.services.bdd.suites.file.FileUpdateSuite;
import com.hedera.services.bdd.suites.file.PermissionSemanticsSpec;
import com.hedera.services.bdd.suites.file.ProtectedFilesUpdateSuite;
import com.hedera.services.bdd.suites.file.negative.UpdateFailuresSpec;
import com.hedera.services.bdd.suites.file.positive.SysDelSysUndelSpec;
import com.hedera.services.bdd.suites.freeze.FreezeSuite;
import com.hedera.services.bdd.suites.freeze.SimpleFreezeOnly;
import com.hedera.services.bdd.suites.freeze.UpdateServerFiles;
import com.hedera.services.bdd.suites.issues.PrivilegedOpsSuite;
import com.hedera.services.bdd.suites.issues.IssueXXXXSpec;
import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.bdd.suites.misc.CannotDeleteSystemEntitiesSuite;
import com.hedera.services.bdd.suites.misc.ConsensusQueriesStressTests;
import com.hedera.services.bdd.suites.misc.ContractQueriesStressTests;
import com.hedera.services.bdd.suites.misc.CryptoQueriesStressTests;
import com.hedera.services.bdd.suites.misc.FileQueriesStressTests;
import com.hedera.services.bdd.suites.misc.OneOfEveryTransaction;
import com.hedera.services.bdd.suites.misc.ZeroStakeNodeTest;
import com.hedera.services.bdd.suites.perf.ContractCallLoadTest;
import com.hedera.services.bdd.suites.perf.CryptoTransferLoadTest;
import com.hedera.services.bdd.suites.perf.FileUpdateLoadTest;
import com.hedera.services.bdd.suites.perf.HCSChunkingRealisticPerfSuite;
import com.hedera.services.bdd.suites.perf.MixedTransferAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.MixedTransferCallAndSubmitLoadTest;
import com.hedera.services.bdd.suites.perf.SubmitMessageLoadTest;
import com.hedera.services.bdd.suites.perf.TokenRelStatusChanges;
import com.hedera.services.bdd.suites.reconnect.CreateAccountsBeforeReconnect;
import com.hedera.services.bdd.suites.perf.TokenTransfersLoadProvider;
import com.hedera.services.bdd.suites.reconnect.CheckUnavailableNode;
import com.hedera.services.bdd.suites.reconnect.CreateFilesBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.CreateTopicsBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.MixedValidationsAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.SubmitMessagesBeforeReconnect;
import com.hedera.services.bdd.suites.reconnect.UpdateApiPermissionsDuringReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateApiPermissionStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateAppPropertiesStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.UpdateAllProtectedFilesDuringReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateDuplicateTransactionAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateExchangeRateStateAfterReconnect;
import com.hedera.services.bdd.suites.reconnect.ValidateFeeScheduleStateAfterReconnect;
import com.hedera.services.bdd.suites.records.CharacterizationSuite;
import com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.CryptoRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.DuplicateManagementTest;
import com.hedera.services.bdd.suites.records.FileRecordsSanityCheckSuite;
import com.hedera.services.bdd.suites.records.SignedTransactionBytesRecordsSuite;
import com.hedera.services.bdd.suites.records.RecordCreationSuite;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import com.hedera.services.bdd.suites.streaming.RecordStreamValidation;
import com.hedera.services.bdd.suites.throttling.BucketThrottlingSpec;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hedera.services.bdd.suites.token.TokenCreateSpecs;
import com.hedera.services.bdd.suites.token.TokenDeleteSpecs;
import com.hedera.services.bdd.suites.token.TokenManagementSpecs;
import com.hedera.services.bdd.suites.token.TokenTransactSpecs;
import com.hedera.services.bdd.suites.token.TokenUpdateSpecs;
import com.hedera.services.legacy.regression.SmartContractAggregatedTests;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.HapiSpecSetup.NodeSelection.FIXED;
import static com.hedera.services.bdd.spec.HapiSpecSetup.TlsConfig.OFF;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.isIdLiteral;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class SuiteRunner {
	private static final Logger log = LogManager.getLogger(SuiteRunner.class);
	private static final int SUITE_NAME_WIDTH = 32;

	private static final HapiSpecSetup.TlsConfig DEFAULT_TLS_CONFIG = OFF;
	private static final HapiSpecSetup.TxnConfig DEFAULT_TXN_CONFIG = HapiSpecSetup.TxnConfig.ALTERNATE;
	private static final HapiSpecSetup.NodeSelection DEFAULT_NODE_SELECTOR = FIXED;

	private static final int EXPECTED_DEV_NETWORK_SIZE = 3;
	private static final int EXPECTED_CI_NETWORK_SIZE = 4;
	private static final String DEFAULT_PAYER_ID = "0.0.2";

	public static int expectedNetworkSize = EXPECTED_DEV_NETWORK_SIZE;

	static final Map<String, HapiApiSuite[]> CATEGORY_MAP = new HashMap<>() {{
		/* CI jobs */
		put("CiConsensusAndCryptoJob", aof(
				new SignedTransactionBytesRecordsSuite(),
				new DuplicateManagementTest(),
				new TopicCreateSuite(),
				new TopicUpdateSuite(),
				new TopicDeleteSuite(),
				new SubmitMessageSuite(),
				new ChunkingSuite(),
				new TopicGetInfoSuite(),
				new BucketThrottlingSpec(),
				new SpecialAccountsAreExempted(),
				new CryptoTransferSuite(),
				new CryptoUpdateSuite(),
				new CryptoRecordsSanityCheckSuite(),
				new PrivilegedOpsSuite(),
				new CannotDeleteSystemEntitiesSuite()));
		put("CiTokenJob", aof(
				new TokenAssociationSpecs(),
				new TokenUpdateSpecs(),
				new TokenCreateSpecs(),
				new TokenDeleteSpecs(),
				new TokenManagementSpecs(),
				new TokenTransactSpecs()));
		put("CiFileJob", aof(
				new FileRecordsSanityCheckSuite(),
				new VersionInfoSpec(),
				new ProtectedFilesUpdateSuite(),
				new PermissionSemanticsSpec(),
				new SysDelSysUndelSpec()));
		put("CiSmartContractJob", aof(
				new NewOpInConstructorSuite(),
				new IssueXXXXSpec(),
				new ContractCallSuite(),
				new ContractCallLocalSuite(),
				new ContractUpdateSuite(),
				new ContractDeleteSuite(),
				new ChildStorageSpec(),
				new BigArraySpec(),
				new CharacterizationSuite(),
				new SmartContractFailFirstSpec(),
				new SmartContractSelfDestructSpec(),
				new DeprecatedContractKeySuite(),
				new ContractRecordsSanityCheckSuite(),
				new ContractGetBytecodeSuite(),
				new SmartContractInlineAssemblySpec(),
				new OCTokenSpec(),
				new RecordCreationSuite()));
		/* Umbrella Redux */
		put("UmbrellaRedux", aof(new UmbrellaRedux()));
		/* Load tests. */
		put("TokenTransfersLoad", aof(new TokenTransfersLoadProvider()));
		put("TokenRelChangesLoad", aof(new TokenRelStatusChanges()));
		put("FileUpdateLoadTest", aof(new FileUpdateLoadTest()));
		put("ContractCallLoadTest", aof(new ContractCallLoadTest()));
		put("SubmitMessageLoadTest", aof(new SubmitMessageLoadTest()));
		put("CryptoTransferLoadTest", aof(new CryptoTransferLoadTest()));
		put("MixedTransferAndSubmitLoadTest", aof(new MixedTransferAndSubmitLoadTest()));
		put("MixedTransferCallAndSubmitLoadTest", aof(new MixedTransferCallAndSubmitLoadTest()));
		put("HCSChunkingRealisticPerfSuite", aof(new HCSChunkingRealisticPerfSuite()));
		/* Functional tests - RECONNECT */
		put("CreateAccountsBeforeReconnect", aof(new CreateAccountsBeforeReconnect()));
		put("CreateTopicsBeforeReconnect", aof(new CreateTopicsBeforeReconnect()));
		put("SubmitMessagesBeforeReconnect", aof(new SubmitMessagesBeforeReconnect()));
		put("CreateFilesBeforeReconnect", aof(new CreateFilesBeforeReconnect()));
		put("CheckUnavailableNode", aof(new CheckUnavailableNode()));
		put("MixedValidationsAfterReconnect", aof(new MixedValidationsAfterReconnect()));
		put("UpdateApiPermissionsDuringReconnect", aof(new UpdateApiPermissionsDuringReconnect()));
		put("ValidateDuplicateTransactionAfterReconnect", aof(new ValidateDuplicateTransactionAfterReconnect()));
		put("ValidateApiPermissionStateAfterReconnect", aof(new ValidateApiPermissionStateAfterReconnect()));
		put("ValidateAppPropertiesStateAfterReconnect", aof(new ValidateAppPropertiesStateAfterReconnect()));
		put("ValidateFeeScheduleStateAfterReconnect", aof(new ValidateFeeScheduleStateAfterReconnect()));
		put("ValidateExchangeRateStateAfterReconnect", aof(new ValidateExchangeRateStateAfterReconnect()));
		put("UpdateAllProtectedFilesDuringReconnect", aof(new UpdateAllProtectedFilesDuringReconnect()));
		/* Functional tests - CONSENSUS */
		put("TopicCreateSpecs", aof(new TopicCreateSuite()));
		put("TopicDeleteSpecs", aof(new TopicDeleteSuite()));
		put("TopicUpdateSpecs", aof(new TopicUpdateSuite()));
		put("SubmitMessageSpecs", aof(new SubmitMessageSuite()));
		put("HCSTopicFragmentationSuite", aof(new ChunkingSuite()));
		put("TopicGetInfoSpecs", aof(new TopicGetInfoSuite()));
		put("ConsensusThrottlesSpecs", aof(new ConsensusThrottlesSuite()));
		put("ConsensusQueriesStressTests", aof(new ConsensusQueriesStressTests()));
		/* Functional tests - FILE */
		put("FileAppendSuite", aof(new FileAppendSuite()));
		put("FileUpdateSuite", aof(new FileUpdateSuite()));
		put("FileDeleteSuite", aof(new FileDeleteSuite()));
		put("UpdateFailuresSpec", aof(new UpdateFailuresSpec()));
		put("ExchangeRateControlSuite", aof(new ExchangeRateControlSuite()));
		put("PermissionSemanticsSpec", aof(new PermissionSemanticsSpec()));
		put("FileQueriesStressTests", aof(new FileQueriesStressTests()));
		/* Functional tests - TOKEN */
		put("TokenCreateSpecs", aof(new TokenCreateSpecs()));
		put("TokenUpdateSpecs", aof(new TokenUpdateSpecs()));
		put("TokenDeleteSpecs", aof(new TokenDeleteSpecs()));
		put("TokenTransactSpecs", aof(new TokenTransactSpecs()));
		put("TokenManagementSpecs", aof(new TokenManagementSpecs()));
		put("TokenAssociationSpecs", aof(new TokenAssociationSpecs()));
		/* Functional tests - CRYPTO */
		put("CryptoTransferSuite", aof(new CryptoTransferSuite()));
		put("CryptoDeleteSuite", aof(new CryptoDeleteSuite()));
		put("CryptoCreateSuite", aof(new CryptoCreateSuite()));
		put("CryptoUpdateSuite", aof(new CryptoUpdateSuite()));
		put("CryptoQueriesStressTests", aof(new CryptoQueriesStressTests()));
		put("CryptoCornerCasesSuite", aof(new CryptoCornerCasesSuite()));
		put("CryptoGetInfoRegression", aof(new CryptoGetInfoRegression()));
		/* Functional tests - CONTRACTS */
		put("NewOpInConstructorSpecs", aof(new NewOpInConstructorSuite()));
		put("DeprecatedContractKeySpecs", aof(new DeprecatedContractKeySuite()));
		put("MultipleSelfDestructsAreSafe", aof(new IssueXXXXSpec()));
		put("ContractQueriesStressTests", aof(new ContractQueriesStressTests()));
		put("ChildStorageSpecs", aof(new ChildStorageSpec()));
		put("ContractCallLocalSuite", aof(new ContractCallLocalSuite()));
		put("ContractCreateSuite", aof(new ContractCreateSuite()));
		put("BigArraySpec", aof(new BigArraySpec()));
		put("SmartContractFailFirstSpec", aof(new SmartContractFailFirstSpec()));
		put("OCTokenSpec", aof(new OCTokenSpec()));
		put("SmartContractInlineAssemblyCheck", aof(new SmartContractInlineAssemblySpec()));
		put("SmartContractSelfDestructSpec", aof(new SmartContractSelfDestructSpec()));
		put("SmartContractPaySpec", aof(new SmartContractPaySpec()));
		/* Functional tests - MIXED (record emphasis) */
		put("ThresholdRecordCreationSpecs", aof(new RecordCreationSuite()));
		put("SignedTransactionBytesRecordsSuite", aof(new SignedTransactionBytesRecordsSuite()));
		put("CryptoRecordSanityChecks", aof(new CryptoRecordsSanityCheckSuite()));
		put("FileRecordSanityChecks", aof(new FileRecordsSanityCheckSuite()));
		put("ContractRecordSanityChecks", aof(new ContractRecordsSanityCheckSuite()));
		put("ContractCallSuite", aof(new ContractCallSuite()));
		put("ProtectedFilesUpdateSuite", aof(new ProtectedFilesUpdateSuite()));
		put("DuplicateManagementTest", aof(new DuplicateManagementTest()));
		/* Record validation. */
		put("RecordStreamValidation", aof(new RecordStreamValidation()));
		/* Fee characterization. */
		put("ControlAccountsExemptForUpdates", aof(new SpecialAccountsAreExempted()));
		/* System files. */
		put("FetchSystemFiles", aof(new FetchSystemFiles()));
		put("CannotDeleteSystemEntitiesSuite", aof(new CannotDeleteSystemEntitiesSuite()));
		/* Throttling */
		put("BucketThrottlingSpec", aof(new BucketThrottlingSpec()));
		/* Network metadata. */
		put("VersionInfoSpec", aof(new VersionInfoSpec()));
		put("FreezeSuite", aof(new FreezeSuite()));
		/* Authorization. */
		put("SuperusersAreNeverThrottled", aof(new PrivilegedOpsSuite()));
		put("SysDelSysUndelSpec", aof(new SysDelSysUndelSpec()));
		/* Freeze and update */
		put("UpdateServerFiles", aof(new UpdateServerFiles()));
		put("OneOfEveryTxn", aof(new OneOfEveryTransaction()));
		/* Zero Stake behaviour */
		put("ZeroStakeTest", aof(new ZeroStakeNodeTest()));
		/* Query payment validation */
		put("QueryPaymentSuite", aof(new QueryPaymentSuite()));
		put("SimpleFreezeOnly", aof(new SimpleFreezeOnly()));
	}};

	static boolean runAsync;
	static List<CategorySuites> targetCategories;
	static boolean globalPassFlag = true;

	private static final String TLS_ARG = "-TLS";
	private static final String TXN_ARG = "-TXN";
	private static final String NODE_SELECTOR_ARG = "-NODE";
	/* Specify the network size so that we can read the appropriate throttle settings for that network. */
	private static final String NETWORK_SIZE_ARG = "-NETWORKSIZE";
	/* Specify the network to run legacy SC tests instead of using suiterunner */
	private static final String LEGACY_SMART_CONTRACT_TESTS="SmartContractAggregatedTests";
	private static String payerId = DEFAULT_PAYER_ID;

	public static void main(String... args) throws Exception {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		String[] effArgs = trueArgs(args);
		log.info("Effective args :: " + List.of(effArgs));
		if (Arrays.asList(effArgs).contains(LEGACY_SMART_CONTRACT_TESTS)) {
			SmartContractAggregatedTests.main(
					new String[]{
							System.getenv("NODES").split(":")[0],
							args[1],
							"1"});
		} else if (Stream.of(effArgs).anyMatch("-CI"::equals)) {
			var tlsOverride = overrideOrDefault(effArgs, TLS_ARG, DEFAULT_TLS_CONFIG.toString());
			var txnOverride = overrideOrDefault(effArgs, TXN_ARG, DEFAULT_TXN_CONFIG.toString());
			var nodeSelectorOverride = overrideOrDefault(effArgs, NODE_SELECTOR_ARG, DEFAULT_NODE_SELECTOR.toString());
			expectedNetworkSize = Integer.parseInt(overrideOrDefault(effArgs,
					NETWORK_SIZE_ARG,
					"" + EXPECTED_CI_NETWORK_SIZE).split("=")[1]);
			var otherOverrides = arbitraryOverrides(effArgs);
			createPayerAccount(System.getenv("NODES"), args[1]);
			HapiApiSpec.runInCiMode(
					System.getenv("NODES"),
					payerId,
					args[1],
					tlsOverride.substring(TLS_ARG.length() + 1),
					txnOverride.substring(TXN_ARG.length() + 1),
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

	/**
	 * Create a default payer account for each test client while running JRS regression tests
	 *
	 * @param nodes
	 * @param defaultNode
	 */
	private static void createPayerAccount(String nodes, String defaultNode) {
		Random r = new Random();
		try {
			Thread.sleep(r.nextInt(5000));
			new CryptoCreateForSuiteRunner(nodes, defaultNode).runSuiteAsync();
			Thread.sleep(2000);
			if(!isIdLiteral(payerId)){
				payerId = DEFAULT_PAYER_ID;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static String overrideOrDefault(String[] effArgs, String argPrefix, String defaultValue) {
		return Stream.of(effArgs)
				.filter(arg -> arg.startsWith(argPrefix))
				.findAny()
				.orElse(String.format("%s=%s", argPrefix, defaultValue));
	}

	private static Map<String, String> arbitraryOverrides(String[] effArgs) {
		var MISC_OVERRIDE_PATTERN = Pattern.compile("([^-].*?)=(.*)");
		return Stream.of(effArgs)
				.map(arg -> MISC_OVERRIDE_PATTERN.matcher(arg))
				.filter(Matcher::matches)
				.collect(toMap(m -> m.group(1), m -> m.group(2)));
	}

	private static String[] trueArgs(String[] args) {
		String ciArgs = Optional.ofNullable(System.getenv("DSL_SUITE_RUNNER_ARGS")).orElse("");
		log.info("Args from CircleCI environment: |" + ciArgs + "|");

		return StringUtils.isNotEmpty(ciArgs)
				? Stream.of(args, new Object[] { "-CI" }, getEffectiveDSLSuiteRunnerArgs(ciArgs))
				.flatMap(Stream::of)
				.toArray(n -> new String[n])
				: args;
	}

	/**
	 * Check if the DSL_SUITE_RUNNER_ARGS contain ALL_SUITES.
	 * If so, add all test suites from CATEGORY_MAP to args that should be run.
	 *
	 * @param realArgs
	 * 		DSL_SUITE_RUNNER_ARGS provided
	 * @return effective args after examining DSL_SUITE_RUNNER_ARGS
	 */
	private static String[] getEffectiveDSLSuiteRunnerArgs(String realArgs) {
		Set<String> effectiveArgs = new HashSet<>();
		String[] ciArgs = realArgs.split("\\s+");

		if (Stream.of(ciArgs).anyMatch("ALL_SUITES"::equals)) {
			effectiveArgs.addAll(CATEGORY_MAP.keySet());
			effectiveArgs.addAll(Stream.of(ciArgs).
					filter(e -> !e.equals("ALL_SUITES")).
					collect(Collectors.toList()));
			log.info("Effective args when running ALL_SUITES : " + effectiveArgs.toString());
			return effectiveArgs.toArray(new String[effectiveArgs.size()]);
		}

		return ciArgs;
	}

	private static List<CategoryResult> runCategories(List<String> args) {
		collectTargetCategories(args);
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

	private static void collectTargetCategories(List<String> args) {
		targetCategories = args
				.stream()
				.filter(k -> null != CATEGORY_MAP.get(k))
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
		for (int i = 0; i < inputs.length; i++) {
			outputs.add(null);
		}
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

	public static void setPayerId(String payerId) {
		SuiteRunner.payerId = payerId;
	}

}
