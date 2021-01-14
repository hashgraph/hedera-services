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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractUndelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ContractDeleteSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractDeleteSuite.class);

	final String PATH_TO_VALID_BYTECODE = HapiSpecSetup.getDefaultInstance().defaultContractPath();

	public static void main(String... args) {
		new ContractDeleteSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						rejectsWithoutProperSig(),
						systemDelThenUndelContractSanityChecks(),
						deleteWorksWithMutableContract(),
						deleteFailsWithImmutableContract(),
				}
		);
	}

	HapiApiSpec rejectsWithoutProperSig() {
		return defaultHapiSpec("ScDelete")
				.given(
						contractCreate("tbd")
				).when().then(
						contractDelete("tbd")
								.signedBy(GENESIS)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec deleteFailsWithImmutableContract() {
		return defaultHapiSpec("DeleteFailsWithImmutableContract")
				.given(
						contractCreate("immutable").omitAdminKey()
				).when().then(
						contractDelete("immutable").hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec deleteWorksWithMutableContract() {
		return defaultHapiSpec("DeleteWorksWithMutableContract")
				.given(
						contractCreate("toBeDeleted")
				).when().then(
						contractDelete("toBeDeleted")
				);
	}

	private HapiApiSpec systemDelThenUndelContractSanityChecks() {
		return defaultHapiSpec("SystemDelThenUndelContractSanityChecks")
				.given(
						fileCreate("conFile")
								.path(PATH_TO_VALID_BYTECODE),
						contractCreate("test-contract")
								.bytecode("conFile")
				).when(
						systemContractDelete("test-contract")
								.payingWith(SYSTEM_DELETE_ADMIN)
				).then(
						systemContractUndelete("test-contract")
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.fee(0L),
						getContractInfo("test-contract")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(OK)
								.logged()
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
