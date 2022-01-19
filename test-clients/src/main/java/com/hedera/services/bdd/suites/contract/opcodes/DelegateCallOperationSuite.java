package com.hedera.services.bdd.suites.contract.opcodes;

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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.legacy.core.CommonUtils.calculateSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class DelegateCallOperationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DelegateCallOperationSuite.class);

	public static void main(String[] args) {
		new DelegateCallOperationSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				verifiesExistence()
		});
	}

	HapiApiSpec verifiesExistence() {
		final String CONTRACT = "delegateCallOpChecker";
		final String INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";
		return defaultHapiSpec("VerifiesExistence")
				.given(
						fileCreate("bytecode").path(ContractResources.CALL_OPERATIONS_CHECKER),
						contractCreate(CONTRACT)
								.bytecode("bytecode")
								.gas(300_000L)
				).when(
				).then(
						contractCall(CONTRACT,
								ContractResources.DELEGATE_CALL_OP_CHECKER_ABI,
								INVALID_ADDRESS)
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
						withOpContext((spec, opLog) -> {
							AccountID id = spec.registry().getAccountID(DEFAULT_PAYER);
							String solidityAddress = calculateSolidityAddress((int)id.getShardNum(), id.getRealmNum(), id.getAccountNum());

							final var contractCall = contractCall(CONTRACT,
									ContractResources.DELEGATE_CALL_OP_CHECKER_ABI,
									solidityAddress)
									.hasKnownStatus(SUCCESS);

							allRunFor(spec, contractCall);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
