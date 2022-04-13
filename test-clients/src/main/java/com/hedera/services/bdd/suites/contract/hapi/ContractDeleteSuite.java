package com.hedera.services.bdd.suites.contract.hapi;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemContractUndelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ContractDeleteSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractDeleteSuite.class);
	private static final String CONTRACT = "Multipurpose";
	private static final String PAYABLE_CONSTRUCTOR = "PayableConstructor";

	public static void main(String... args) {
		new ContractDeleteSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
						rejectsWithoutProperSig(),
						systemCannotDeleteOrUndeleteContracts(),
						deleteWorksWithMutableContract(),
						deleteFailsWithImmutableContract(),
						deleteTransfersToAccount(),
						deleteTransfersToContract()
				}
		);
	}

	HapiApiSpec rejectsWithoutProperSig() {
		return defaultHapiSpec("ScDelete")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when().then(
						contractDelete(CONTRACT)
								.signedBy(GENESIS)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec systemCannotDeleteOrUndeleteContracts() {
		return defaultHapiSpec("SystemCannotDeleteOrUndeleteContracts")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when( ).then(
						systemContractDelete(CONTRACT)
								.payingWith(SYSTEM_DELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED),
						systemContractUndelete(CONTRACT)
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED),
						getContractInfo(CONTRACT).hasAnswerOnlyPrecheck(OK)
				);
	}

	private HapiApiSpec deleteWorksWithMutableContract() {
		return defaultHapiSpec("DeleteWorksWithMutableContract")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when().then(
						contractDelete(CONTRACT),
						getContractInfo(CONTRACT)
								.has(contractWith().isDeleted())
				);
	}

	private HapiApiSpec deleteFailsWithImmutableContract() {
		return defaultHapiSpec("DeleteFailsWithImmutableContract")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT).omitAdminKey()
				).when().then(
						contractDelete(CONTRACT).hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec deleteTransfersToAccount() {
		return defaultHapiSpec("DeleteTransfersToAccount")
				.given(
						cryptoCreate("receiver").balance(0L),
						uploadInitCode(PAYABLE_CONSTRUCTOR),
						contractCreate(PAYABLE_CONSTRUCTOR).balance(1L)
				).when(
						contractDelete(PAYABLE_CONSTRUCTOR).transferAccount("receiver")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	private HapiApiSpec deleteTransfersToContract() {
		final var suffix = "Receiver";

		return defaultHapiSpec("DeleteTransfersToContract")
				.given(
						uploadInitCode(PAYABLE_CONSTRUCTOR),
						contractCreate(PAYABLE_CONSTRUCTOR).balance(0L),
						contractCustomCreate(PAYABLE_CONSTRUCTOR, suffix).balance(1L)
				).when(
						contractDelete(PAYABLE_CONSTRUCTOR).transferContract(PAYABLE_CONSTRUCTOR + suffix)
				).then(
						getAccountBalance(PAYABLE_CONSTRUCTOR + suffix).hasTinyBars(1L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
