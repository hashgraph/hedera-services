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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;

/* Notes to the reviewer
*  The bellow functions are pure experiments and should not be considered by any means as working solution.
*  Actual working implementation will be performed today (03.11)
* */
public class TypedSuiteRunner {

	public static void main(String[] args) {
		Map<SuiteCategory, List<HapiApiSuite>> suites = SuitesStore.getSuites();

		Map<Boolean, Set<SuiteCategory>> suitesByRunType = distributeByRunType(suites);

		Map<Boolean, List<SuiteCategory>> test = suites.keySet().stream()
				.collect(groupingBy(cat -> testFunction(suites.get(cat))));

		System.out.println();
	}

	private static Boolean testFunction(final List<HapiApiSuite> hapiApiSuites) {
		return hapiApiSuites.stream().anyMatch(HapiApiSuite::canRunAsync);
	}

	private static Map<Boolean, Set<SuiteCategory>> distributeByRunType(final Map<SuiteCategory, List<HapiApiSuite>> suites) {
		Map<Boolean, Set<SuiteCategory>> suitesByRunType = new HashMap<>();

		suitesByRunType.put(true, new HashSet<>());
		suitesByRunType.put(false, new HashSet<>());

		suites.keySet().forEach(cat -> {
			List<HapiApiSuite> hapiApiSuites = suites.get(cat);
			for (HapiApiSuite hapiApiSuite : hapiApiSuites) {
				suitesByRunType.get(hapiApiSuite.canRunAsync()).add(cat);
			}
		});

		return suitesByRunType;
	}


}
