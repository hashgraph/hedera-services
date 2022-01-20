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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.legacy.core.CommonUtils.calculateSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

public class ExtCodeCopyOperationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ExtCodeCopyOperationSuite.class);

	public static void main(String[] args) {
		new ExtCodeCopyOperationSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				verifiesExistence()
		});
	}

	HapiApiSpec verifiesExistence() {
		final String CONTRACT = "extCodeCopyOpChecker";
		final String INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";
		ByteString EMPTY_BYTECODE = ByteString.EMPTY;

		return defaultHapiSpec("VerifiesExistence")
				.given(
						fileCreate("bytecode").path(ContractResources.EXT_CODE_OPERATIONS_CHECKER_CONTRACT),
						contractCreate("extCodeCopyOpChecker")
								.bytecode("bytecode")
								.gas(300_000L)
				).when(
				)
				.then(
						contractCall(CONTRACT,
								ContractResources.EXT_CODE_OP_CHECKER_CODE_COPY_OF,
								INVALID_ADDRESS)
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
						contractCallLocal(CONTRACT,
								ContractResources.EXT_CODE_OP_CHECKER_CODE_COPY_OF,
								INVALID_ADDRESS)
								.hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS),
						withOpContext((spec, opLog) -> {
							AccountID accountID = spec.registry().getAccountID(DEFAULT_PAYER);
							ContractID contractID = spec.registry().getContractId(CONTRACT);
							String accountSolidityAddress = calculateSolidityAddress((int) accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum());
							String contractAddress = calculateSolidityAddress((int) contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum());

							final var call = contractCall(CONTRACT,
									ContractResources.EXT_CODE_OP_CHECKER_CODE_COPY_OF,
									accountSolidityAddress)
									.via("callRecord");
							final var callRecord = getTxnRecord("callRecord");

							final var accountCodeCallLocal = contractCallLocal(CONTRACT,
									ContractResources.EXT_CODE_OP_CHECKER_CODE_COPY_OF,
									accountSolidityAddress)
									.saveResultTo("accountCode");

							final var contractCodeCallLocal = contractCallLocal(CONTRACT,
									ContractResources.EXT_CODE_OP_CHECKER_CODE_COPY_OF,
									contractAddress)
									.saveResultTo("contractCode");

							final var getBytecodeCall = getContractBytecode(CONTRACT)
									.saveResultTo("contractGetBytecode");

							allRunFor(spec, call, callRecord, accountCodeCallLocal, contractCodeCallLocal, getBytecodeCall);

							final var recordResult = callRecord.getResponseRecord().getContractCallResult();
							final var accountCode = spec.registry().getBytes("accountCode");
							final var contractCode = spec.registry().getBytes("contractCode");
							final var getBytecode = spec.registry().getBytes("contractGetBytecode");

							Assertions.assertEquals(EMPTY_BYTECODE, recordResult.getContractCallResult());
							Assertions.assertArrayEquals(EMPTY_BYTECODE.toByteArray(), accountCode);
							Assertions.assertArrayEquals(getBytecode, contractCode);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
