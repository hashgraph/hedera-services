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

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

class TypedSuiteRunnerTest {
//	private final PackageStore packageStore = new PackageStore();
//	private final Map<SuitePackage, Supplier<List<HapiApiSuite>>> suites = packageStore.initialize(effectiveArguments);

	// TODO: Refactor the different cases as separate tests.
	// TODO: Fix bug where when we pass "-s Autorenew suites" - does not split at all and is interpreting it
	//       as an wrong argument "-s Autorenew suites"
	@Test
	void properlyParses() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		final String[] withSplit = {"-s precompile, openzeppelin, opcodes, fees, freeze, negative "};
//		final String[] withSplit = {"-s Autorenew, Compose, Consensus, Contract, Contract, Crypto, Fees"};
//		final String[] withSplit = {"-s Contract"};
		final String[] withWrongArguments = {"-s Autorew, Compose, Consensus, Contract, Contct op codes, Contract records, Cryto, Fees"};
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