package com.hedera.services.bdd.suiterunner;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.bdd.suiterunner.annotations.ExcludeFromRSR;
import com.hedera.services.bdd.suiterunner.annotations.StressTest;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

public class ReflectiveSuitesRunnerService {
	private static final Logger log = LogManager.getLogger(ReflectiveSuitesRunnerService.class);
	private static Set<String> suitesPaths;
	private static final TreeMap<String, List<HapiApiSuite>> instantiatedSuites = new TreeMap<>();
	private static final StringBuilder logMessageBuilder = new StringBuilder();

	public static Set<String> getPackages(final String[] args) {
		collectSuitesPaths();

		if (runAllTests(args)) {
			log.warn("Preparing to execute all tests without performance tests...");
			return suitesPaths
					.stream()
					.filter(path -> !path.contains("perf"))
					.collect(toSet());
		}

		var arguments = parse(args);

		final var wrongArguments = arguments
				.stream()
				.filter(argument -> suitesPaths
						.stream()
						.noneMatch(path -> path.contains(argument.toLowerCase())))
				.toList();

		if (!wrongArguments.isEmpty()) {
			logEvent("%d arguments are misspelled and/or test suites are missing. Wrong arguments:", wrongArguments);
			arguments.removeAll(wrongArguments);
		}

		logEvent("Preparing to execute tests for %d suite(s):", arguments);

		return suitesPaths
				.stream()
				.filter(path -> !path.contains("perf"))
				.filter(path -> arguments
						.stream()
						.anyMatch(argument -> path.contains(argument.toLowerCase())))
				.collect(toSet());
	}

	// TODO: Document and explain the ternary operator with suite.getSimpleName().equals("CryptoCreateForSuiteRunner")
	public static TreeMap<String, List<HapiApiSuite>> instantiateSuites(final Set<String> paths) {
		for (String path : paths) {
			final var suites = new Reflections(path).getSubTypesOf(HapiApiSuite.class);
			final var instances = suites
					.stream()
					.filter(suite -> !suite.isAnnotationPresent(ExcludeFromRSR.class))
					.filter(suite -> !suite.isAnnotationPresent(StressTest.class))
					.filter(suite -> isInRequestedScope(path, suite))
					.map(suite -> {
						try {
							return suite.getSimpleName().equals("CryptoCreateForSuiteRunner")
									? suite.getDeclaredConstructor(String.class, String.class).newInstance("localhost", "3")
									: suite.getDeclaredConstructor().newInstance();
						} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
							e.printStackTrace();
							return null;
						}
					})
					.toList();
			instantiatedSuites.putIfAbsent(path, instances);
		}
		return instantiatedSuites;
	}

	public static boolean runAllTests(final String[] arguments) {
		return arguments.length == 0 || (arguments.length == 1 && arguments[0].toLowerCase().contains("-a".toLowerCase()));
	}

	private static void collectSuitesPaths() {
		suitesPaths = new Reflections("com.hedera.services.bdd.suites")
				.getSubTypesOf(HapiApiSuite.class)
				.stream()
				.map(Class::getPackageName)
				.filter(path -> path.contains("suites"))
				.collect(Collectors.toSet());

		final var packages = suitesPaths
				.stream()
				.map(path -> path.replaceAll("com.hedera.services.bdd.suites.", ""))
				.sorted()
				.toList();

		logEvent("%d packages collected. Available packages are:", packages);
	}

	private static void logEvent(final String message, final List<String> source) {
		logMessageBuilder
				.append(System.lineSeparator())
				.append(String.format(message, source.size()))
				.append(System.lineSeparator())
				.append(String.join(System.lineSeparator(), source))
				.append(System.lineSeparator());
		log.warn(logMessageBuilder.toString());
		logMessageBuilder.setLength(0);
	}

	@NotNull
	private static ArrayList<String> parse(final String[] args) {
		return args[0].contains("-s")
				? Arrays
				.stream(args[0].replaceAll("-s ", "").trim().split(",\\s+|,"))
				.distinct()
				.collect(Collectors.toCollection(ArrayList::new))
				: Arrays
				.stream(args)
				.distinct()
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 *  This method guarantees, that the algorithm will instantiate only suites withing the query scope.
	 *  We collect objects by syb-type and the Reflections' library will include tests, which are not part of the
	 *  particular path, because the library traces the chain of inheritance.
	 *  For example: if we intend to run only CryptoTransferThenFreezeTest - without this check we will undesirably and
	 *  implicitly include CryptoTransferLoadTest(extended by CryptoTransferThenFreezeTest) and LoadTest (extended by CryptoTransferLoadTest).
	 * @param suite the evaluated suite
	 */
	private static boolean isInRequestedScope(final String path, Class<? extends HapiApiSuite> suite) {
		return suite.getPackageName().equals(path);
	}
}
