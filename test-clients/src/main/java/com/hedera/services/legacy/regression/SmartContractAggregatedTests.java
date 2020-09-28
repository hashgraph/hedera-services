package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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


import com.hedera.services.legacy.smartcontract.OCTokenIT;

import java.util.List;

/**
 * This class aggregates the remaining smartContract tests and runs them one by one.
 */
public class SmartContractAggregatedTests {

	private static final List<LegacySmartContractTest> SCTests = List.of(
			new SmartContractTestBitcarbon(),
			new SmartContractTestInlineAssembly(),
			new OCTokenIT()
	);


	public static void main(String[] args) throws Exception {

		int numberOfReps = 1;
		if ((args.length) > 0) {
			numberOfReps = Integer.parseInt(args[0]);
		}

		// just a simple utility for running legecy SC tests one after the other.
		for(LegacySmartContractTest scTest : SCTests) {
			for (int i = 0; i < numberOfReps; i++) {
				scTest.demo();
			}
		}
	}
}
