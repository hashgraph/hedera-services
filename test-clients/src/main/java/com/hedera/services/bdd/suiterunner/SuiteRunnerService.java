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

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

public class SuiteRunnerService {
	private static final Logger log = LogManager.getLogger(SuiteRunnerService.class);
	private static final String SEPARATOR = "====================================";
	public static Set<String> suitesPaths;
	public static Map<String, List<HapiApiSuite>> instantiatedSuites = new HashMap<>();
	public static boolean runAllSuites;

	public static Set<String> getPackages(String[] args) {
		collectSuitePaths();

		if (runAllTests(args)) {
			runAllSuites = true;
			log.warn(String.format("%1$s Running all tests without performance tests %1$s", SEPARATOR));
			return suitesPaths
					.stream()
					.filter(path -> !path.contains("perf"))
					.collect(toSet());
		}

		var arguments = parse(args);

		final var wrongArguments = arguments
				.stream()
				.filter(argument -> suitesPaths.stream().noneMatch(path -> path.contains(argument.toLowerCase())))
				.toList();

		if (!wrongArguments.isEmpty()) {
			log.warn(String.format(
					"Input arguments are misspelled and/or test suites are missing. Skipping tests for: %s suites",
					String.join(", ", wrongArguments)));
			arguments.removeAll(wrongArguments);
		}

		log.warn(String.format("%1$s Running tests for %2$s suites %1$s", SEPARATOR, String.join(", ", arguments)));

		return suitesPaths
				.stream()
				.filter(path -> !path.contains("perf"))
				.filter(path -> arguments.stream().anyMatch(argument -> path.contains(argument.toLowerCase())))
				.collect(toSet());
	}

	/**
	 *  The method .filter(suite -> suite.getPackageName().equals(path)) is necessary due to the fact, that the operation
	 *  of collecting the target objects depends on the syb-types (new Reflections(path).getSubTypesOf(HapiApiSuite.class).
	 *  The Reflections' library will include tests, which are not part of the particular path, because will follow the chain
	 *  of inheritance.
	 *  for example: if we intend to include only CryptoTransferThenFreezeTest - without the filter we will inherently include
	 *  CryptoTransferLoadTest(extended by CryptoTransferThenFreezeTest)  and LoadTest (extended by CryptoTransferLoadTest).
	 * @param paths the paths of the target packages
	 */
	public static Map<String, List<HapiApiSuite>> getSuites(final Set<String> paths) {
		for (String path : paths) {
			final var suites = new Reflections(path).getSubTypesOf(HapiApiSuite.class);
			final var instances = suites
					.stream()
					.filter(suite -> suite.getPackageName().equals(path))
					.map(suite -> {
						HapiApiSuite instance = null;
						try {
							instance = suite.getDeclaredConstructor().newInstance();
						} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
							e.printStackTrace();
						}
						return instance;
					})
					.toList();
			instantiatedSuites.putIfAbsent(path, instances);
		}
		return instantiatedSuites;
	}

	public static boolean runAllTests(String[] arguments) {
		return arguments.length == 0 || (arguments.length == 1 && arguments[0].toLowerCase().contains("-a".toLowerCase()));
	}

	/* --- Helpers --- */
	private static void collectSuitePaths() {
		suitesPaths = new Reflections("com.hedera.services.bdd.suites")
				.getSubTypesOf(HapiApiSuite.class)
				.stream()
				.map(Class::getPackageName)
				.filter(path -> path.contains("suites"))
				.collect(toSet());
	}

	@NotNull
	private static ArrayList<String> parse(String[] args) {
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
}
