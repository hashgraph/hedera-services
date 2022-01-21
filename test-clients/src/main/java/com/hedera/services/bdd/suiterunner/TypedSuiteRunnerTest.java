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

import com.hedera.services.bdd.suiterunner.enums.SuitePackage;
import com.hedera.services.bdd.suiterunner.store.PackageStore;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

class TypedSuiteRunnerTest {
	private final PackageStore packageStore = new PackageStore();
	private final Map<SuitePackage, Supplier<List<HapiApiSuite>>> suites = packageStore.initialize();

	// TODO: Refactor the different cases as separate tests.
	// TODO: Fix bug where when we pass "-s Autorenew suites" - does not split at all and is interpreting it
	//       as an wrong argument "-s Autorenew suites"
	@Test
	void properlyParses() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		final String[] withSplit = {"-s Autorenew, Compose, Consensus, Contract, Contract op codes, Contract records, Crypto, Fees"};
		final String[] withoutSplit = {"Autorenew suites", "Compose suites", "Consensus suites", "LimeChain suites", "meta suites", "Hedera suites"};
		final String[] allWithSplit = {"-s -a"};
		final String[] allWithoutSplit = {"-a"};
		final String[] noArguments = {};
		final String[] withSplitNoPerfSuites = {"-s -a -spt"};
		final String[] withoutSplitNoPerfSuites = {"-a", "-spt"};
		final String[] withInvalidCharacter = {"-s Autorenew suites; compose suites, Consensus suites, LimeChain suites, meta suites, Hedera suites"};
		final String[] isolated = {"-s Crypto suites, LimeChain suites,  Hedera suites"};

		TypedSuiteRunner.main(withSplit);

	}


}