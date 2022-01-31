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


import com.hedera.services.bdd.suiterunner.models.SpecReport;
import com.hedera.services.bdd.suiterunner.models.SuiteReport;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.suiterunner.ReflectiveSuiteRunnerService.getPackages;
import static com.hedera.services.bdd.suiterunner.ReflectiveSuiteRunnerService.getSuites;
import static com.hedera.services.bdd.suiterunner.models.ReportFactory.getReportFor;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_FAILED;

public class ReflectiveSuiteRunner {
	public static final String LOG_PATH = "output/log-buffer.log";
	private static final String SEPARATOR = "====================";
	private static final Logger log = redirectLogger();
	private static final List<HapiApiSuite> failedSuites = new ArrayList<>();
	private static final List<SuiteReport> suiteReports = new ArrayList<>();

	public static void main(String[] args) {
		final var packages = getPackages(args);

		if (packages.isEmpty()) return;
		final var suites = getSuites(packages);
		final var suitesByRunType = distributeByRunType(suites.values());

		clearLog();

		executeInMode(suitesByRunType.get("RunsSync"), true);
		executeInMode(suitesByRunType.get("RunsAsync"), false);
		executeInMode(suitesByRunType.get("RunFreeze"), true);

		failedSuites.forEach(failed -> suiteReports.add(getReportFor(failed)));

		clearLog();

		generateFinalLog(suitesByRunType);
	}

	private static Map<String, List<HapiApiSuite>> distributeByRunType(final Collection<List<HapiApiSuite>> suites) {
		final Map<String, List<HapiApiSuite>> byRunType = Map.of(
				"RunsSync", new ArrayList<>(),
				"RunsAsync", new ArrayList<>(),
				"RunFreeze", new ArrayList<>()
		);

		/*	Important:
			The bellow logic enforces the rule, that freeze tests must be executed after all the other tests and
			operates under the presumption, that all the freeze tests are always executed in sync mode
		*/
		suites
				.stream()
				.flatMap(Collection::stream)
				.peek(suite -> {
					suite.skipClientTearDown();
					suite.deferResultsSummary();
				})
				.forEach(suite -> {
					if (isFreeze(suite)) {
						byRunType.get("RunFreeze").add(suite);
					}
					if (!suite.canRunAsync() && !isFreeze(suite)) {
						byRunType.get("RunsSync").add(suite);
					} else if (suite.canRunAsync() && !isFreeze(suite)) {
						byRunType.get("RunsAsync").add(suite);
					}
				});

		return byRunType;
	}

	private static void generateFinalLog(final Map<String, List<HapiApiSuite>> suitesByRunType) {
		final var builder = new StringBuilder();
		final var executedTotal = suitesByRunType
				.values()
				.stream()
				.map(List::size)
				.reduce(0, Integer::sum);

		builder.append(String.format("%1$s Execution summary %1$s%n", SEPARATOR));
		builder.append(String.format("TOTAL: %d%n", executedTotal));
		builder.append(String.format("PASSED: %d%n", executedTotal - failedSuites.size()));
		builder.append(String.format("FAILED: %d%n", failedSuites.size()));

		for (SuiteReport failedSuite : suiteReports) {
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

		RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.WARN);
		rootLogger.add(builder.newAppenderRef("log"));
		rootLogger.add(builder.newAppenderRef("stdout"));

		builder.add(console);
		builder.add(file);
		builder.add(rootLogger);

		Configurator.initialize(builder.build());

		return LogManager.getLogger(ReflectiveSuiteRunner.class);
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

	/* --- Helpers --- */
	private static boolean isFreeze(HapiApiSuite suite) {
		return suite.getClass().getPackageName().contains("freeze");
	}

	private static void executeInMode(final List<HapiApiSuite> suites, final boolean runSync) {
		suites.forEach(suite -> {
			final var suiteName = suite.getClass().getSimpleName();
			log.warn("Executing {}...", suiteName);
			final var finalOutcome = runSync
					? suite.runSuiteSync()
					: suite.runSuiteAsync();
			log.warn("finished {} with status: {}", suiteName, finalOutcome.toString());
			if (finalOutcome == SUITE_FAILED) {
				failedSuites.add(suite);
			}
		});
	}
}

