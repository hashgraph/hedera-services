package com.hedera.services.bdd.suites.contract;

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

import com.hedera.services.bdd.spec.HapiApiSpec;

import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;

import java.util.Arrays;
import java.util.List;

public class ContractUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractUpdateSuite.class);

	public static void main(String... args) {
		new ContractUpdateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests()
//				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				updateWithPendingNewKeySucceeds()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
		);
	}

	private HapiApiSpec updateWithPendingNewKeySucceeds() {
		return defaultHapiSpec("UpdateWithPendingNewKeySucceeds")
				.given(
						newKeyNamed("newKey"),
						fileCreate("bytecode").path(PATH_TO_LOOKUP_BYTECODE),
						contractCreate("target").bytecode("bytecode")
				).when(
						contractUpdate("target").newKey("newKey").deferStatusResolution()
				).then(
						contractUpdate("target")
								.via("txnRequiringSyncVerify")
								.signedBy(GENESIS, "newKey")
								.newMemo("So we outdanced thought...")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
