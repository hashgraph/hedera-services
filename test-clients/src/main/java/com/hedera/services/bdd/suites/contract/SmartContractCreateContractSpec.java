package com.hedera.services.bdd.suites.contract;

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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;

public class SmartContractCreateContractSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SmartContractCreateContractSpec.class);

	public static void main(String... args) {
		new org.ethereum.crypto.HashUtil();

		new SmartContractCreateContractSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				contractCreateContractSpec()
		});
	}

	HapiApiSpec contractCreateContractSpec() {

		return defaultHapiSpec("contractCreateContractSpec")
				.given(
						cryptoCreate("payer")
								.balance( 10_000_000_000L),
						fileCreate("createTrivialBytecode")
								.path(ContractResources.DELEGATING_CONTRACT_BYTECODE_PATH)
				).when(
						contractCreate("firstContract")
								.payingWith("payer")
								.gas(300_000L)
								.bytecode("createTrivialBytecode")
								.via("firstContractTxn")

				).then(
						assertionsHold((spec, ctxLog) -> {
							var subop1 = contractCall("firstContract", ContractResources.CREATE_CHILD_ABI)
									.payingWith("payer")
									.gas(300_000L)
									.via("createContractTxn");

							// First contract calls created contract and get an integer return value
							var subop2 = contractCallLocal("firstContract", ContractResources.GET_CHILD_RESULT_ABI)
									.saveResultTo("contractCallContractResultBytes")
									.gas(300_000L);
							CustomSpecAssert.allRunFor(spec,  subop1, subop2);

							byte[] 	resultBytes = spec.registry().getBytes("contractCallContractResultBytes");
							CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(ContractResources.GET_CHILD_RESULT_ABI);

							int contractCallReturnVal = 0;
							if(resultBytes != null && resultBytes.length > 0) {
								Object[] retResults = function.decodeResult(resultBytes);
								if (retResults != null && retResults.length > 0) {
									BigInteger retBi = (BigInteger) retResults[0];
									contractCallReturnVal = retBi.intValue();
								}
							}

							ctxLog.info("This contract call contract return value {}", contractCallReturnVal);
							Assert.assertEquals(
									"This contract call contract return value should be 7",
									ContractResources.CREATED_TRIVIAL_CONTRACT_RETURNS, contractCallReturnVal);


							// Get created contract's info with call to first contract
							var subop3 = contractCallLocal("firstContract", ContractResources.GET_CHILD_ADDRESS_ABI)
									.saveResultTo("getCreatedContractInfoResultBytes")
									.gas(300_000L);
							CustomSpecAssert.allRunFor(spec,  subop3);

							resultBytes = spec.registry().getBytes("getCreatedContractInfoResultBytes");

							function = CallTransaction.Function.fromJsonInterface(ContractResources.GET_CHILD_ADDRESS_ABI);

							Object[] retResults = function.decodeResult(resultBytes);
							String contractIDString = null;
							if (retResults != null && retResults.length > 0) {
								byte[] retVal = (byte[]) retResults[0];

								long realm = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 4, 12));
								long accountNum = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 12, 20));
								contractIDString = String.format("%d.%d.%d", realm, 0, accountNum);
							}
							ctxLog.info("The created contract ID {}", contractIDString);
							Assert.assertNotEquals("Created contract doesn't have valid Contract ID",
									ContractID.newBuilder().getDefaultInstanceForType(), TxnUtils.asContractId(contractIDString, spec));


							var subop4 = getContractInfo(contractIDString)
									.saveToRegistry("createdContractInfoSaved");

							CustomSpecAssert.allRunFor(spec, subop4);

							ContractGetInfoResponse.ContractInfo createdContratInfo = spec.registry().getContractInfo("createdContractInfoSaved");

							Assert.assertTrue(createdContratInfo.hasContractID());
							Assert.assertTrue(createdContratInfo.hasAccountID());
							Assert.assertTrue(createdContratInfo.hasExpirationTime());
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
