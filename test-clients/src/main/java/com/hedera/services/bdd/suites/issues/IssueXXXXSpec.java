package com.hedera.services.bdd.suites.issues;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import java.util.List;

public class IssueXXXXSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(IssueXXXXSpec.class);

	public static void main(String... args) {
		new IssueXXXXSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[]{
						multipleSelfDestructsAreSafe(),
				}
		);
	}

	private HapiApiSpec multipleSelfDestructsAreSafe() {
		return defaultHapiSpec("MultipleSelfDestructsAreSafe")
				.given(
						fileCreate("bytecode").path(ContractResources.FUSE_BYTECODE_PATH),
						contractCreate("fuse").bytecode("bytecode")
				).when(
						contractCall("fuse", ContractResources.LIGHT_ABI).via("lightTxn")
				).then(
						getTxnRecord("lightTxn").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
