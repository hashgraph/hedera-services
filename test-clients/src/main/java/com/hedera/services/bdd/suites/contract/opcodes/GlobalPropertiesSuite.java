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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;

public class GlobalPropertiesSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(GlobalPropertiesSuite.class);

	public static void main(String... args) {
		new GlobalPropertiesSuite().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				chainIdWorks(),
				baseFeeWorks(),
				coinbaseWorks(),
				gasLimitWorks()
		);
	}

	private HapiApiSpec chainIdWorks() {
		final BigInteger expectedChainID = new BigInteger(HapiSpecSetup.getDefaultNodeProps().get("contracts.chainId"));
		return defaultHapiSpec("chainIdWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_CHAIN_ID_ABI)
								.via("chainId")
				).then(
						getTxnRecord("chainId").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().resultThruAbi(
												ContractResources.GLOBAL_PROPERTIES_CHAIN_ID_ABI,
												isLiteralResult(
														new Object[]{expectedChainID}
												)
										)
								)
						),
						contractCallLocal("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_CHAIN_ID_ABI)
								.nodePayment(1_234_567)
								.has(
										ContractFnResultAsserts.resultWith()
												.resultThruAbi(
														ContractResources.GLOBAL_PROPERTIES_CHAIN_ID_ABI,
														ContractFnResultAsserts.isLiteralResult(
																new Object[]{expectedChainID}
														)
												)
								)
				);
	}

	private HapiApiSpec baseFeeWorks() {
		final BigInteger expectedBaseFee = BigInteger.valueOf(0);
		return defaultHapiSpec("baseFeeWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_BASE_FEE_ABI)
								.via("baseFee")
				).then(
						getTxnRecord("baseFee").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().resultThruAbi(
												ContractResources.GLOBAL_PROPERTIES_BASE_FEE_ABI,
												isLiteralResult(
														new Object[]{BigInteger.valueOf(0)}
												)
										)
								)
						),
						contractCallLocal("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_BASE_FEE_ABI)
								.nodePayment(1_234_567)
								.has(
										ContractFnResultAsserts.resultWith()
												.resultThruAbi(
														ContractResources.GLOBAL_PROPERTIES_BASE_FEE_ABI,
														ContractFnResultAsserts.isLiteralResult(
																new Object[]{expectedBaseFee}
														)
												)
								)
				);
	}

	private HapiApiSpec coinbaseWorks() {
		return defaultHapiSpec("coinbaseWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_COINBASE_ABI)
								.via("coinbase")
				).then(
						withOpContext((spec, opLog) -> {
							final var expectedCoinbase = parsedToByteString(DEFAULT_PROPS.fundingAccount().getAccountNum());

							final var callLocal = contractCallLocal("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_COINBASE_ABI)
									.nodePayment(1_234_567)
									.saveResultTo("callLocalCoinbase");
							final var callRecord = getTxnRecord("coinbase");

							allRunFor(spec, callRecord, callLocal);
							final var recordResult = callRecord.getResponseRecord().getContractCallResult();
							final var callLocalResult = spec.registry().getBytes("callLocalCoinbase");
							Assertions.assertEquals(recordResult.getContractCallResult(), expectedCoinbase);
							Assertions.assertArrayEquals(callLocalResult, expectedCoinbase.toByteArray());
						})
				);
	}

	private HapiApiSpec gasLimitWorks() {
		final var gasLimit = Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("contracts.maxGas"));
		return defaultHapiSpec("gasLimitWorks")
				.given(
						fileCreate("globalProps").path(ContractResources.GLOBAL_PROPERTIES),
						contractCreate("globalPropsContract").bytecode("globalProps")
				).when(
						contractCall("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_GASLIMIT_ABI)
								.via("gasLimit")
								.gas(gasLimit)
				).then(
						getTxnRecord("gasLimit").logged().hasPriority(
								recordWith().contractCallResult(
										resultWith().resultThruAbi(
												ContractResources.GLOBAL_PROPERTIES_GASLIMIT_ABI,
												isLiteralResult(
														new Object[]{BigInteger.valueOf(gasLimit)}
												)
										)
								)
						),
						contractCallLocal("globalPropsContract", ContractResources.GLOBAL_PROPERTIES_GASLIMIT_ABI)
								.gas(gasLimit)
								.nodePayment(1_234_567)
								.has(
										ContractFnResultAsserts.resultWith()
												.resultThruAbi(
														ContractResources.GLOBAL_PROPERTIES_GASLIMIT_ABI,
														ContractFnResultAsserts.isLiteralResult(
																new Object[]{BigInteger.valueOf(gasLimit)}
														)
												)
								)
				);
	}
}
