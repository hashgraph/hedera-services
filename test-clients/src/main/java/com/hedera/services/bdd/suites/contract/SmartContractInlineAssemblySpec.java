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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;

import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

public class SmartContractInlineAssemblySpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SmartContractFailFirstSpec.class);

	public static void main(String... args) {
		new org.ethereum.crypto.HashUtil();

		new SmartContractInlineAssemblySpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				smartContractInlineAssemblyCheck()
		});
	}

	HapiApiSpec smartContractInlineAssemblyCheck() {

		return defaultHapiSpec("smartContractInlineAssemblyCheck")
				.given(
						cryptoCreate("payer")
								.balance( 10_000_000_000_000L),
						fileCreate("simpleStorageByteCode")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH),
						fileCreate("inlineTestByteCode")
								.path(ContractResources.INLINE_TEST_BYTECODE_PATH)

				).when(
						contractCreate("simpleStorageContract")
								.payingWith("payer")
								.gas(300_000L)
								.bytecode("simpleStorageByteCode")
								.via("simpleStorageContractTxn"),
						contractCreate("inlineTestContract")
								.payingWith("payer")
								.gas(300_000L)
								.bytecode("inlineTestByteCode")
								.via("inlineTestContractTxn")

				).then(
						assertionsHold((spec, ctxLog) -> {

							var subop1 = getContractInfo("simpleStorageContract")
									.nodePayment(10L)
									.saveToRegistry("simpleStorageKey");

							var subop2 = getAccountInfo("payer")
									.saveToRegistry("payerAccountInfo");
							CustomSpecAssert.allRunFor(spec, subop1, subop2);

							ContractInfo simpleStorageContractInfo = spec.registry().getContractInfo("simpleStorageKey");
							String contractAddress = simpleStorageContractInfo.getContractAccountID();

							var subop3 = contractCallLocal("inlineTestContract", ContractResources.GET_CODE_SIZE_ABI, contractAddress)
									.saveResultTo("simpleStorageContractCodeSizeBytes")
									.gas(300_000L);

							CustomSpecAssert.allRunFor(spec,  subop3);

							byte[] 	result = spec.registry().getBytes("simpleStorageContractCodeSizeBytes");

							String funcJson = ContractResources.GET_CODE_SIZE_ABI.replaceAll("'", "\"");
							CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);

							int codeSize = 0;
							if(result != null && result.length > 0) {
								Object[] retResults = function.decodeResult(result);
								if (retResults != null && retResults.length > 0) {
									BigInteger retBi = (BigInteger) retResults[0];
									codeSize = retBi.intValue();
								}
							}

							ctxLog.info("Contract code size {}", codeSize);
							Assert.assertNotEquals(
									"Real smart contract code size should be greater than 0",
									0, codeSize);


							AccountInfo payerAccountInfo = spec.registry().getAccountInfo("payerAccountInfo");
							String acctAddress = payerAccountInfo.getContractAccountID();

							var subop4 = contractCallLocal("inlineTestContract", ContractResources.GET_CODE_SIZE_ABI, acctAddress)
									.saveResultTo("fakeCodeSizeBytes")
									.gas(300_000L);

							CustomSpecAssert.allRunFor(spec,  subop4);
							result = spec.registry().getBytes("fakeCodeSizeBytes");

							codeSize = 0;
							if(result != null && result.length > 0) {
								Object[] retResults = function.decodeResult(result);
								if (retResults != null && retResults.length > 0) {
									BigInteger retBi = (BigInteger) retResults[0];
									codeSize = retBi.intValue();
								}
							}

							ctxLog.info("Fake contract code size {}", codeSize);
							Assert.assertEquals(
									"Fake contract code size should be 0",
									0, codeSize);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
