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
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.legacy.core.CommonUtils.calculateSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;

public class BalanceOperationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(BalanceOperationSuite.class);

	public static void main(String[] args) {
		new BalanceOperationSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				verifiesExistenceOfAccountsAndContracts()
		});
	}

	HapiApiSpec verifiesExistenceOfAccountsAndContracts() {
		final long BALANCE = 10;
		final String ACCOUNT = "test";
		final String CONTRACT = "balanceChecker";
		final String INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";

		return defaultHapiSpec("VerifiesExistenceOfAccountsAndContracts")
				.given(
						fileCreate("bytecode").path(ContractResources.BALANCE_CHECKER_CONTRACT),
						contractCreate("balanceChecker")
								.bytecode("bytecode")
								.gas(300_000L),
						cryptoCreate("test").balance(BALANCE)
				).when(
				).then(
						contractCall(CONTRACT,
								ContractResources.BALANCE_CHECKER_BALANCE_OF,
								INVALID_ADDRESS)
								.hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
						contractCallLocal(CONTRACT,
								ContractResources.BALANCE_CHECKER_BALANCE_OF,
								INVALID_ADDRESS)
								.hasAnswerOnlyPrecheck(INVALID_SOLIDITY_ADDRESS),
						withOpContext((spec, opLog) -> {
							AccountID id = spec.registry().getAccountID(ACCOUNT);
							ContractID contractID = spec.registry().getContractId(CONTRACT);
							String solidityAddress = calculateSolidityAddress((int)id.getShardNum(), id.getRealmNum(), id.getAccountNum());
							String contractAddress = calculateSolidityAddress((int)contractID.getShardNum(), contractID.getRealmNum(), contractID.getContractNum());

							final var call = contractCall(CONTRACT,
									ContractResources.BALANCE_CHECKER_BALANCE_OF,
									solidityAddress)
									.via("callRecord");

							final var callRecord = getTxnRecord("callRecord").hasPriority(
									recordWith().contractCallResult(
											resultWith().resultThruAbi(
													ContractResources.BALANCE_CHECKER_BALANCE_OF,
													isLiteralResult(
															new Object[]{BigInteger.valueOf(BALANCE)}
													)
											)
									)
							);

							final var callLocal = contractCallLocal(CONTRACT,
									ContractResources.BALANCE_CHECKER_BALANCE_OF,
									solidityAddress)
									.has(
											ContractFnResultAsserts.resultWith()
													.resultThruAbi(
															ContractResources.BALANCE_CHECKER_BALANCE_OF,
															ContractFnResultAsserts.isLiteralResult(
																	new Object[]{BigInteger.valueOf(BALANCE)}
															)
													)
									);

							final var contractCallLocal = contractCallLocal(CONTRACT,
									ContractResources.BALANCE_CHECKER_BALANCE_OF,
									contractAddress)
									.has(
											ContractFnResultAsserts.resultWith()
													.resultThruAbi(
															ContractResources.BALANCE_CHECKER_BALANCE_OF,
															ContractFnResultAsserts.isLiteralResult(
																	new Object[]{BigInteger.valueOf(0)}
															)
													)
									);

							allRunFor(spec, call, callLocal, callRecord, contractCallLocal);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
