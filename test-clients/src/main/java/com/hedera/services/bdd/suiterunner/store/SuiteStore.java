package com.hedera.services.bdd.suiterunner.store;

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
import com.hedera.services.bdd.suites.HapiApiSuite;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class SuiteStore {
	protected static final String SUITES_PATH = "com.hedera.services.bdd.suites.";

	protected static final Map<SuitePackage, Supplier<List<HapiApiSuite>>> suites = new EnumMap<>(SuitePackage.class);

	public Map<SuitePackage, Supplier<List<HapiApiSuite>>> initializeCategories(final Set<String> arguments) {
		initializeSuites(arguments);
		return suites;
	}

	protected abstract void initializeSuites(final Set<String> effectiveArguments);

	public static List<String> getAllCategories = Arrays
			.stream(SuitePackage.values())
			.map(c -> c.asString)
			.collect(Collectors.toList());

	public static boolean isValidCategory(String arg) {
		return Arrays
				.stream(SuitePackage.values())
				.map(value -> value.asString)
				.anyMatch(string -> string.equalsIgnoreCase(arg));
	}

//	TODO: Clarify why the freeze suites are being executed separately
//	public static List<SuitePackage> getCategories(Set<String> input) {
//		return input
//				.stream()
//				.map(PackageStore::getCategory)
//				.filter(cat -> cat != FREEZE_SUITES)
//				.collect(Collectors.toList());
//	}

	public static SuitePackage getCategory(final String input) {
		return Arrays
				.stream(SuitePackage.values())
				.filter(category -> category.asString.equalsIgnoreCase(input)).findFirst()
				.orElse(null);
	}

}
