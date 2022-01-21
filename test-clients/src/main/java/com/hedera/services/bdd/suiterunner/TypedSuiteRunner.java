package com.hedera.services.bdd.suiterunner;

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

import com.hedera.services.bdd.suiterunner.enums.SuitePackage;
import com.hedera.services.bdd.suiterunner.models.SpecReport;
import com.hedera.services.bdd.suiterunner.models.SuiteReport;
import com.hedera.services.bdd.suiterunner.store.PackageStore;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.FREEZE_SUITES;
import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.PERF_SUITES;
import static com.hedera.services.bdd.suiterunner.models.ReportFactory.getReportFor;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_PASSED;
import static java.util.stream.Collectors.toList;

public class TypedSuiteRunner {
	public static final String LOG_PATH = "output/log-buffer.log";
	private static final String SEPARATOR = "====================================";
	private static final Logger log = redirectLogger();
	private static Map<SuitePackage, Supplier<List<HapiApiSuite>>> suites;
	private static final List<SuiteReport> failedSuites = new ArrayList<>();
	private static boolean runAllSuites = false;

	public static void main(String[] args) {
		final var store = new PackageStore();
		suites = store.initialize();
		final var effectiveArguments = getArguments(args);
		final var suiteCategories = PackageStore.getCategories(effectiveArguments);
		var suiteByRunType = distributeByRunType(suiteCategories);

		clearLog();

		runSuitesSync(suiteByRunType.get("RunsSync"));
		runSuitesAsync(suiteByRunType.get("RunsAsync"));

		if (effectiveArguments.contains("Freeze") || runAllSuites) {
			log.warn(String.format("%1$s Running Freeze suites %1$s", SEPARATOR));
			runSuitesSync(suites.get(FREEZE_SUITES).get());
		}
		clearLog();
		generateFinalLog();
	}

	private static void generateFinalLog() {
		for (SuiteReport failedSuite : failedSuites) {
			StringBuilder builder = new StringBuilder();
			builder.append(String.format("%1$s %2$d failing specs in %3$s %1$s%n",
					SEPARATOR, failedSuite.getFailingSpecs().size(), failedSuite.getName()));
			for (SpecReport failingSpec : failedSuite.getFailingSpecs()) {
				builder.append(String.format("Spec name: %s%n", failingSpec.getName()));
				builder.append(String.format("Status: %s%n", failingSpec.getStatus()));
				builder.append(String.format("Cause: %s%n", failingSpec.getFailureReason()));
				builder.append(System.lineSeparator());
			}
			builder.append(String.format("%1$s End of report for %2$s %1$s%n", SEPARATOR, failedSuite.getName()));
			builder.append(System.lineSeparator());
			log.warn(builder.toString());
		}
	}

	private static void runSuitesSync(final List<HapiApiSuite> suites) {
		log.warn(String.format("%1$s Running suites in sync mode %1$s", SEPARATOR));
		List<FinalOutcome> outcomes = suites.stream().map(HapiApiSuite::runSuiteSync).collect(toList());
		getFailedSuites(outcomes, suites).forEach(failed -> failedSuites.add(getReportFor(failed)));
	}

	private static void runSuitesAsync(final List<HapiApiSuite> suites) {
		log.warn(String.format("%1$s Running suites in async mode %1$s", SEPARATOR));
		List<FinalOutcome> outcomes = suites.stream().map(HapiApiSuite::runSuiteAsync).collect(toList());
		getFailedSuites(outcomes, suites).forEach(failed -> failedSuites.add(getReportFor(failed)));
	}

	private static List<HapiApiSuite> getFailedSuites(final List<FinalOutcome> outcomes, final List<HapiApiSuite> suites) {
		return IntStream.range(0, suites.size())
				.filter(i -> outcomes.get(i) != SUITE_PASSED)
				.mapToObj(suites::get)
				.collect(toList());
	}

	private static Set<String> getArguments(final String[] args) {
		if (Arrays.stream(args).anyMatch(arg -> arg.toLowerCase().contains("-spt".toLowerCase()))) {
			runAllSuites = true;
			log.warn(String.format("%1$s Running all tests except performance tests %1$s", SEPARATOR));
			return suites
					.keySet()
					.stream()
					.filter(key -> key != PERF_SUITES && key != FREEZE_SUITES)
					.map(key -> key.asString)
					.collect(Collectors.toSet());
		}

		if (args.length == 0 || (args.length == 1 && args[0].toLowerCase().contains("-a".toLowerCase()))) {
			runAllSuites = true;
			log.warn(String.format("%1$s Running all tests %1$s", SEPARATOR));
			return suites
					.keySet()
					.stream()
					.filter(key -> key != FREEZE_SUITES)
					.map(key -> key.asString)
					.collect(Collectors.toSet());
		}

		var arguments = args[0].contains("-s")
				? Arrays
				.stream(args[0].replaceAll("-s ", "").trim().split(",\\s+|,"))
				.collect(Collectors.toCollection(ArrayList::new))
				: Arrays
				.stream(args)
				.collect(Collectors.toCollection(ArrayList::new));

		final var wrongArguments = arguments
				.stream()
				.filter(arg -> !PackageStore.isValidCategory(arg))
				.collect(Collectors.toList());

		arguments.removeAll(wrongArguments);

		final var argumentsToLog = arguments
				.stream()
				.map(PackageStore::getCategory)
				.map(cat -> cat.asString)
				.collect(Collectors.joining(", "));

		if (!wrongArguments.isEmpty()) {
			log.warn(String.format(
					"Input arguments are misspelled and/or test suites are missing. Skipping tests for: %s suites",
					String.join(", ", wrongArguments)));
		}

		log.warn(String.format("%1$s Running tests for %2$s suites %1$s", SEPARATOR, String.join(", ", argumentsToLog)));

		return Set.copyOf(arguments);
	}

	private static Map<String, List<HapiApiSuite>> distributeByRunType(final List<SuitePackage> categories) {
		final Map<String, List<HapiApiSuite>> byRunType =
				Map.of("RunsSync", new ArrayList<>(), "RunsAsync", new ArrayList<>());

		categories
				.stream()
				.map(category -> suites.get(category).get())
				.flatMap(Collection::stream)
				.peek(suite -> {
//					suite.skipClientTearDown();
//					suite.deferResultsSummary();
				})
				.forEach(suite -> {
					if (suite.canRunAsync()) {
						byRunType.get("RunsAsync").add(suite);
					} else {
						byRunType.get("RunsSync").add(suite);
					}
				});

		return byRunType;
	}

	private static Logger redirectLogger() {
		ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();

		AppenderComponentBuilder console
				= builder.newAppender("stdout", "Console");

		AppenderComponentBuilder file
				= builder.newAppender("log", "File");
		file.addAttribute("fileName", LOG_PATH);

		LayoutComponentBuilder pattern
				= builder.newLayout("PatternLayout");
		pattern.addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p at line %-1L in class %c{1} - %m%n");

		console.add(pattern);
		file.add(pattern);

		RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.INFO);
		rootLogger.add(builder.newAppenderRef("log"));
		rootLogger.add(builder.newAppenderRef("stdout"));

		builder.add(console);
		builder.add(file);
		builder.add(rootLogger);

		Configurator.initialize(builder.build());

		return LogManager.getLogger(TypedSuiteRunner.class);
	}

	private static void clearLog() {
		try {
			BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(LOG_PATH));
			bufferedWriter.write("");
			bufferedWriter.flush();
			bufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}



