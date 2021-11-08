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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suiterunner.enums.SuitePackage;
import com.hedera.services.bdd.suiterunner.models.ReportFactory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.suiterunner.enums.SuitePackage.PERF_SUITES;
import static com.hedera.services.bdd.suiterunner.store.PackageStore.getCategories;
import static com.hedera.services.bdd.suites.HapiApiSuite.FinalOutcome.SUITE_PASSED;

//	TODO: implement dialogue before running the tests to display available options
//	TODO: Implement a logic for exporting the results as CSV
//	TODO: Delete the log-buffer.log in a tear down method
//	TODO: Move all the String constants to a separate Definition class
// 	TODO: Implement loading feedback in the console with rotating /

public class TypedSuiteRunner {
	public static final String LOG_PATH = "output/log-buffer.log";
	private static final String SEPARATOR = "====================================";
	private static final Logger log = redirectLogger();
	private static Map<SuitePackage, Supplier<List<HapiApiSuite>>> suites;
	private static final List<SuiteReport> failedSuites = new ArrayList<>();

	public static void main(String[] args) {
		final var store = new PackageStore();
		suites = store.initialize();
		final var effectiveArguments = getArguments(args);
		final var suiteCategories = getCategories(effectiveArguments);
		final var suiteByRunType = distributeByRunType(suiteCategories);

		runSuitesSync(suiteByRunType.get("RunsSync"));

		final var vagueSuites = failedSuites
				.stream()
				.flatMap(suiteReport -> suiteReport.getFailingSpecs().stream())
				.filter(specReport -> specReport.getFailureReason().equals("Reason can not be extrapolated"))
				.map(SpecReport::getName)
				.collect(Collectors.toList());

		/*	Note to the reviewer:
		*	The "Vague spec reports" console output is for debugging purposes only
		* */
		System.out.println(" ==================================== Vague spec reports ====================================");
		System.out.println(String.join(", ", vagueSuites));
	}

	private static void runSuitesSync(final Set<HapiApiSuite> suites) {
		suites
				.stream()
				.filter(suite -> suite.runSuiteSync() != SUITE_PASSED)
				.peek(suite -> {
					log.warn(String.format("%1$s Failing specs in %2$s %1$s", SEPARATOR, suite.getClass().getSimpleName()));
					suite.getFinalSpecs().stream().filter(HapiApiSpec::NOT_OK).forEach(log::warn);
					log.warn(String.format("%1$s End of report for %2$s %1$s", SEPARATOR, suite.getClass().getSimpleName()));
				})
				.forEach(failedSuite -> failedSuites.add(ReportFactory.getReportFor(failedSuite)));

		resetLog();
	}

	//	TODO: Refactor to cleaner implementation after the input format is being approved
	//	TODO: Handle Illegal characters exception
	//	TODO: Handle user's collection choice to run by topic, package or CI collection
	private static List<String> getArguments(final String[] args) {
		if (Arrays.stream(args).anyMatch(arg -> arg.toLowerCase().contains("-spt".toLowerCase()))) {
			return suites
					.keySet()
					.stream()
					.filter(key -> key != PERF_SUITES)
					.map(key -> key.asString)
					.collect(Collectors.toList());
		}

		if (args.length == 0 || (args.length == 1 && args[0].toLowerCase().contains("-a".toLowerCase()))) {
			return suites
					.keySet()
					.stream()
					.map(key -> key.asString)
					.collect(Collectors.toList());
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

		final var finalArguments = arguments
				.stream()
				.map(PackageStore::getCategory)
				.map(cat -> cat.asString)
				.collect(Collectors.joining(", "));

		if (!wrongArguments.isEmpty()) {
			log.warn(String.format(
					"Input arguments are misspelled and/or test suites are missing. Skipping tests for: %s ",
					String.join(", ", wrongArguments)));
		}

		log.warn(String.format("%1$s Running tests for %2$s %1$s", SEPARATOR, String.join(", ", finalArguments)));

		return arguments;
	}

	private static Map<String, Set<HapiApiSuite>> distributeByRunType(final List<SuitePackage> categories) {
		final Map<String, Set<HapiApiSuite>> byRunType =
				Map.of("RunsSync", new HashSet<>(), "RunsAsync", new HashSet<>());

		categories
				.stream()
				.map(category -> suites.get(category).get())
				.flatMap(Collection::stream)
				.peek(suite -> {
					suite.skipClientTearDown();
					suite.deferResultsSummary();
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

	/*	Note to the reviewer:
	 *  After the TypedSuiteRunner run all the selected E2E tests the log4j root logger will reset to the default settings
	 * 	as declared in the xml file.
	 * */
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

		return LogManager.getLogger(TypedSuiteRunner.class);
	}

	private static void resetLog() {
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



