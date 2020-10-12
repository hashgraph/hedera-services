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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.builder.RequestBuilder;

import java.util.List;

/**
 * This class aggregates the remaining smartContract tests and runs them one by one.
 */
public class SmartContractAggregatedTests {

	private static final List<LegacySmartContractTest> SCTests = List.of(
			new SmartContractTestBitcarbon(),
			new SmartContractCreateContract()
	);

	private static String grpcHost;
	private static long nodeAccountNum;
	private static AccountID nodeAccount;
	private static int numberOfReps = 1;

	public static void main(String[] args) throws Exception {

		if (args.length < 3) {
			System.out.println("Must provide all four arguments to this application.");
			System.out.println("0: host");
			System.out.println("1: node number");
			System.out.println("2: number of iterations");
			return;
		}

		System.out.println("args[0], host, is " + args[0]);
		System.out.println("args[1], node account, is " + args[1]);
		System.out.println("args[2], number of iterations, is " + args[2]);

		grpcHost = args[0];
		nodeAccountNum = Long.parseLong(args[1]);
		nodeAccount = RequestBuilder
				.getAccountIdBuild(nodeAccountNum, 0l, 0l);
		numberOfReps = Integer.parseInt(args[2]);


		// just a simple utility for running legecy SC tests one after the other.
		for(LegacySmartContractTest scTest : SCTests) {
			for (int i = 0; i < numberOfReps; i++) {
				scTest.demo(grpcHost, nodeAccount);
			}
		}
	}
}
