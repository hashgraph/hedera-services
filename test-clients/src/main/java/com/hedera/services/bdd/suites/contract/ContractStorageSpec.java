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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class ContractStorageSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractStorageSpec.class);

	private static final String SETSIZE_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_howManyKB\"," +
			"\"type\":\"uint256\"}],\"name\":\"setSizeInKB\",\"outputs\":[],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SETCONTENT_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_value\"," +
			"\"type\":\"uint256\"},{\"name\":\"_size\",\"type\":\"uint256\"}],\"name\":\"changeArray\",\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String GETCONTENT_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_index\"," +
			"\"type\":\"uint256\"}],\"name\":\"getData\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256[]\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String PATH_TO_CONTRACT_STORAGE_BYTECODE = "src/main/resource/testfiles/ContractStorage.bin";

	/* Default the size of the array to 4KB */
	private static final int SIZE=16;

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ContractStorageSpec().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				Storage(),
		});
	}

	HapiApiSpec Storage() {
		var MAX_GAS_LIMIT = 3000000;

		return HapiApiSpec.defaultHapiSpec("ContractStorage")
				.given(
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(
										"maxGasLimit", "" + MAX_GAS_LIMIT
								)).payingWith(ADDRESS_BOOK_CONTROL),
						TxnVerbs.fileCreate("contractStorageFile")
								.payingWith(GENESIS)
								.path(PATH_TO_CONTRACT_STORAGE_BYTECODE),
						TxnVerbs.contractCreate("contractStorageContract")
								.payingWith(GENESIS)
								.bytecode("contractStorageFile")
				)
				.when(
						contractCall("contractStorageContract", SETSIZE_ABI, SIZE)
								.payingWith(GENESIS)
								.gas(300_000L)
								.logged()
				)
				.then(
						withOpContext((spec, opLog) -> {
							long numberOfIterations = 1000;
							List<HapiSpecOperation> subOps = new ArrayList<>();

							for (int i = 0; i < numberOfIterations; i++) {
								int randInt = ThreadLocalRandom.current().nextInt(1000);
								int randSize = ThreadLocalRandom.current().nextInt(SIZE);
								opLog.info("data to set is : {}", randInt);
								opLog.info("size to set is : {}", randSize);
								var subOp1 = contractCall(
										"contractStorageContract",
										SETCONTENT_ABI,
										randInt,
										randSize)
										.payingWith(GENESIS)
										.gas(3_000_000L)
										.logged();
								var subOp2 = contractCallLocal("contractStorageContract",
										GETCONTENT_ABI,
										randSize)
										.payingWith(GENESIS)
										.nodePayment(10_000_000L)
										.gas(3_000_000L)
										.maxResultSize(randSize*1000)
										.saveResultTo("contractStorageArrayResult")
										.logged();
								CustomSpecAssert.allRunFor(spec, subOp1, subOp2);
								byte[] 	result = spec.registry().getBytes("contractStorageArrayResult");

								CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(GETCONTENT_ABI);
								if(result != null && result.length > 0) {
									Object[] retResults = function.decodeResult(result);
									if (retResults != null && retResults.length > 0) {
										opLog.info("Data retrieved is : {}", retResults);
										opLog.info("round : {}", i);
//										for(int j=0; j<16*randSize; j++) {
//											if(Integer.parseInt(retResults[j].toString()) != randInt) {
//												opLog.error("Data is not set as expected.");
//												opLog.info("Expected : {}", randInt);
//												opLog.info("Retrieved : {}", retResults[j]);
//											}
//										}
									}
								}
							}
						})
				);
	}
}
