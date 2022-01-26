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

import com.hedera.services.bdd.suiterunner.reflective_runner.AutomatedSuite;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

public class PackageStore extends SuiteStore {
	@Override
	protected void initializeSuites(final Set<String> arguments) {
		List<String> packagePaths = arguments.stream().map(s -> SUITES_PATH + s.toLowerCase()).toList();

		for (String path : packagePaths) {
			final var objects = new Reflections(path).getTypesAnnotatedWith(AutomatedSuite.class);
			final var instances = objects
					.stream()
					.map(object -> {
						HapiApiSuite target = null;
						try {
							target = (HapiApiSuite) object.getDeclaredConstructor().newInstance();
						} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
							e.printStackTrace();
						}
						return target;
					})
					.toList();

			final var packageName = path.substring(path.lastIndexOf('.') + 1);
			final var category = getCategory(packageName);
			if (instances.size() > 0) {
				suites.putIfAbsent(category, () -> instances);
			}
		}
	}
}
