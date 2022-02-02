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


import com.hedera.services.bdd.suiterunner.exceptions.FailedSuiteException;
import com.hedera.services.bdd.suiterunner.models.SpecReport;
import com.hedera.services.bdd.suiterunner.models.SuiteReport;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.suiterunner.ReflectiveSuiteRunnerService.getPackages;
import static com.hedera.services.bdd.suiterunner.ReflectiveSuiteRunnerService.instantiateSuites;
import static com.hedera.services.bdd.suiterunner.models.ReportFactory.generateFailedSuiteReport;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_FAILED;

public class ReflectiveSuiteRunner {
	public static final String LOG_PATH = "src/main/java/com/hedera/services/bdd/suiterunner/logs/ReflectiveSuiteRunner.log";
	private static final String SEPARATOR = "----------";
	private static final Logger log = redirectLogger();
	private static final List<HapiApiSuite> failedSuites = new ArrayList<>();
	private static final List<SuiteReport> suiteReports = new ArrayList<>();
	private static final StringBuilder logMessageBuilder = new StringBuilder();

	public static void main(String[] args) throws FailedSuiteException {
		log.warn(Banner.getBanner());
		final var packages = getPackages(args);
		if (packages.isEmpty()) return;
		final var suites = instantiateSuites(packages);
		final var suitesByRunType = distributeSuitesByRunType(suites.values());

		clearLog();

		executeSuites(true, suitesByRunType.get("Sync"));
		executeSuites(false, suitesByRunType.get("Async"));
		executeSuites(true, suitesByRunType.get("Freeze"));

		failedSuites.forEach(suite -> suiteReports.add(generateFailedSuiteReport(suite)));

		clearLog();

		generateFinalLog(suitesByRunType);
	}

	private static Map<String, List<HapiApiSuite>> distributeSuitesByRunType(final Collection<List<HapiApiSuite>> suites) {
		final Map<String, List<HapiApiSuite>> byRunType = Map.of(
				"Sync", new ArrayList<>(),
				"Async", new ArrayList<>(),
				"Freeze", new ArrayList<>()
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
						byRunType.get("Freeze").add(suite);
					}
					if (!suite.canRunAsync() && !isFreeze(suite)) {
						byRunType.get("Sync").add(suite);
					} else if (suite.canRunAsync() && !isFreeze(suite)) {
						byRunType.get("Async").add(suite);
					}
				});

		return byRunType;
	}

	private static void executeSuites(final boolean synced, final List<HapiApiSuite> suites) {
		suites.forEach(suite -> {
			final var suiteName = suite.getClass().getSimpleName();
			log.warn("Executing {}...", suiteName);
			final var finalOutcome = synced
					? suite.runSuiteSync()
					: suite.runSuiteAsync();
			log.warn("finished {} with status: {}", suiteName, finalOutcome.toString());
			if (finalOutcome == SUITE_FAILED) {
				failedSuites.add(suite);
			}
		});
	}

	private static void generateFinalLog(final Map<String, List<HapiApiSuite>> suitesByRunType) throws FailedSuiteException {
		final var executedTotal = suitesByRunType
				.values()
				.stream()
				.map(List::size)
				.reduce(0, Integer::sum);
		final var failedTotal = failedSuites.size();

		logMessageBuilder
				.append(System.lineSeparator())
				.append(String.format("%1$s Execution summary %1$s%n", SEPARATOR))
				.append(String.format("TOTAL: %d%n", executedTotal))
				.append(String.format("PASSED: %d%n", executedTotal - failedTotal))
				.append(String.format("FAILED: %d%n", failedTotal));

		log.warn(logMessageBuilder.toString());

		logMessageBuilder.setLength(0);

		for (SuiteReport suiteReport : suiteReports) {
			logMessageBuilder
					.append(System.lineSeparator())
					.append(String.format("%1$s %2$d failing spec(s) in %3$s %1$s%n",
					SEPARATOR, suiteReport.getFailedSpecs().size(), suiteReport.getName()));
			for (SpecReport failingSpec : suiteReport.getFailedSpecs()) {
				logMessageBuilder
						.append(String.format("Spec name: %s%n", failingSpec.getName()))
						.append(String.format("Status: %s%n", failingSpec.getStatus()))
						.append(String.format("Cause: %s%n", failingSpec.getFailureReason()))
						.append(System.lineSeparator());
			}
			logMessageBuilder
					.append(String.format("%1$s End of report for %2$s %1$s%n", SEPARATOR, suiteReport.getName()))
					.append(System.lineSeparator());
			log.warn(logMessageBuilder.toString());
			logMessageBuilder.setLength(0);
		}

		logMessageBuilder.setLength(0);

		if (failedSuites.size() > 0) {
			final var suiteNames = failedSuites
					.stream()
					.map(suite -> suite.getClass().getSimpleName())
					.toList();
			throw new FailedSuiteException("Suite(s) are failing: ", suiteNames);
		}
	}

	private static Logger redirectLogger() {
		final var builder = ConfigurationBuilderFactory.newConfigurationBuilder();
		final var console = builder.newAppender("stdout", "Console");
		final var file = builder.newAppender("log", "File");

		file.addAttribute("fileName", LOG_PATH);

		final var pattern = builder.newLayout("PatternLayout");

		pattern.addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p at line %-1L in class %c{1} - %m%n");
		console.add(pattern);
		file.add(pattern);

		final var rootLogger = builder.newRootLogger(Level.WARN);

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
			final var bufferedWriter = Files.newBufferedWriter(Paths.get(LOG_PATH));
			bufferedWriter.write("");
			bufferedWriter.flush();
			bufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static boolean isFreeze(final HapiApiSuite suite) {
		return suite.getClass().getPackageName().contains("freeze");
	}
}
