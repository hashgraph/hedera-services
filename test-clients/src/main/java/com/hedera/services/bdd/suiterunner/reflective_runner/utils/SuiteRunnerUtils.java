package com.hedera.services.bdd.suiterunner.reflective_runner.utils;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

public class SuiteRunnerUtils {
	private static final Logger log = LogManager.getLogger(SuiteRunnerUtils.class);
	private static final String SEPARATOR = "====================================";
	public static Set<String> suitesPaths;
	public static Map<String, List<HapiApiSuite>> instantiatedSuites = new HashMap<>();
	public static Set<Object> processedSuites = new HashSet<>();
	public static boolean runAllSuites;

	public static Set<String> getPackages(String[] args) {
		collectSuitePaths();

		if (runAllTests(args)) {
			runAllSuites = true;
			log.warn(String.format("%1$s Running all tests without performance tests %1$s", SEPARATOR));
			return suitesPaths
					.stream()
					.filter(path -> !path.contains("perf") && !path.contains("freeze"))
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
			log.warn(String.format(
					"Input arguments are misspelled and/or test suites are missing. Skipping tests for: %s suites",
					String.join(", ", wrongArguments)));
			arguments.removeAll(wrongArguments);
		}

		log.warn(String.format("%1$s Running tests for %2$s suites %1$s", SEPARATOR, String.join(", ", arguments)));

		return suitesPaths
				.stream()
				.filter(path -> !path.contains("perf") && !path.contains("freeze"))
				.filter(path -> arguments
						.stream()
						.anyMatch(argument -> path.contains(argument.toLowerCase())))
				.collect(toSet());
	}

	/** If the developer pass a top level package and inner packages, the algorithm will not include in the map the inner
	 *  package. We operate under the assumption, that the intent of the developer is to run all the suites, contained in
	 *  the top level package.
	 * @param paths the paths of the target packages
	 */
	public static Map<String, List<HapiApiSuite>> getSuites(final Set<String> paths) {

		for (String path : paths) {
			final var suites = new Reflections(path).getSubTypesOf(HapiApiSuite.class);
			final var instances = suites
					.stream()
					.filter(suite -> !processedSuites.contains(suite))
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

			if (!instances.isEmpty()) {
				final var packageName = path.substring(path.lastIndexOf('.') + 1);
				instantiatedSuites.putIfAbsent(packageName, instances);
			}
			processedSuites.addAll(suites);
		}

		return instantiatedSuites;
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

	public static boolean runAllTests(String[] arguments) {
		return arguments.length == 0 || (arguments.length == 1 && arguments[0].toLowerCase().contains("-a".toLowerCase()));
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


//				.stream()
//				.sorted()
//				.toList();


