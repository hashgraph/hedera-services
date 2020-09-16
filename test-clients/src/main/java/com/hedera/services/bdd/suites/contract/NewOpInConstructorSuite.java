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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;

import java.util.List;

public class NewOpInConstructorSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(NewOpInConstructorSuite.class);

	final String PATH_TO_CHILD_STORAGE_BYTECODE = "testfiles/ChildStorage.bin";
	final String PATH_TO_ABANDONING_PARENT_BYTECODE = "testfiles/AbandoningParent.bin";

	public static void main(String... args) {
		new NewOpInConstructorSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				contractCreateWithNewOpInConstructor("ChildStorage", PATH_TO_CHILD_STORAGE_BYTECODE, 2),
				contractCreateWithNewOpInConstructor("AbandoningParent", PATH_TO_ABANDONING_PARENT_BYTECODE, 5)
		});
	}

	private HapiApiSpec contractCreateWithNewOpInConstructor(
			final String testName,
			final String filePath,
			final long expectedChildrenCount) {
		final String bc = testName + "Bytecode";
		final String txn = testName + "Txn";
		final String parent = testName + "ParentInfo";

		return defaultHapiSpec("ContractCreateWithNewOpInConstructor-" + testName)
				.given(
						fileCreate(bc).path(filePath)
				).when().then(
						contractCreate(testName).bytecode(bc).via(txn),
						getContractInfo(testName).saveToRegistry(parent).logged(),
						getTxnRecord(txn)
								.saveCreatedContractListToRegistry(testName)
								.logged(),
						UtilVerbs.contractListWithPropertiesInheritedFrom(testName + "CreateResult",
								expectedChildrenCount, parent)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
