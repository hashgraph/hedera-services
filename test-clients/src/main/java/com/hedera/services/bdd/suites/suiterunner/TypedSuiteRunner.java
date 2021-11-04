package com.hedera.services.bdd.suites.suiterunner;

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

import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.SuiteRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.suites.suiterunner.SuiteCategory.PERF_SUITES;
import static com.hedera.services.bdd.suites.suiterunner.SuitesStore.getCategories;
import static com.hedera.services.bdd.suites.suiterunner.SuitesStore.initialize;

// TODO: implement dialogue before running the tests to display available options
public class TypedSuiteRunner {
	private static final Logger log = LogManager.getLogger(SuiteRunner.class);
	private static final Map<SuiteCategory, Supplier<List<HapiApiSuite>>> suites = initialize();

	public static void main(String[] args) {
		final var effectiveArguments = getArguments(args);
		final var suiteCategories = getCategories(effectiveArguments);
		final var suiteByRunType = distributeByRunType(suiteCategories);
	}

	// TODO: Handle Illegal characters exception
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
				.filter(arg -> !SuitesStore.isValidCategory(arg))
				.collect(Collectors.toList());

		arguments.removeAll(wrongArguments);

		if (!wrongArguments.isEmpty()) {
			log.warn(String.format(
					"Input arguments are misspelled or test suites are missing. Skipping tests for: %s ",
					String.join(", ", wrongArguments)));
		}
		return arguments;
	}

	private static Map<Boolean, Set<HapiApiSuite>> distributeByRunType(final ArrayDeque<SuiteCategory> categories) {
		final Map<Boolean, Set<HapiApiSuite>> suitesByRunType = Map.of(true, new HashSet<>(), false, new HashSet<>());

		while (!categories.isEmpty()) {
			suites.get(categories.pop()).get().forEach(suite -> suitesByRunType.get(suite.canRunAsync()).add(suite));
		}

		return suitesByRunType;
	}
}




