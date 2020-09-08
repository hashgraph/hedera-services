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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;

public class ChildStorageSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ChildStorageSpec.class);
	final String PATH_TO_CHILD_STORAGE_BYTECODE = "src/main/resource/testfiles/ChildStorage.bin";

	private static final String GET_MY_VALUE_ABI =
			"{\"constant\":true,\"inputs\":[],\"name\":\"getMyValue\"," +
					"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}]," +
					"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}\n";
	private static final String GROW_CHILD_ABI =
			"{\"constant\":false," +
					"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"}," +
						"{\"internalType\":\"uint256\",\"name\":\"_howManyKB\",\"type\":\"uint256\"}," +
						"{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}]," +
					"\"name\":\"growChild\"," +
					"\"outputs\":[]," +
					"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}\n";
	private static final String GET_CHILD_VALUE_ABI =
			"{\"constant\":true," +
					"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"}]," +
					"\"name\":\"getChildValue\"," +
					"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}]," +
					"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}\n";
	private static final String SET_ZERO_READ_ONE_ABI =
			"{\"constant\":false," +
					"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}]," +
					"\"name\":\"setZeroReadOne\"," +
					"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_getOne\",\"type\":\"uint256\"}]," +
					"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}\n";
	private static final String SET_BOTH_ABI =
			"{\"constant\":false," +
					"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}]," +
					"\"name\":\"setBoth\"," +
					"\"outputs\":[],\"" +
					"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ChildStorageSpec().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				childStorage(),
		});
	}

	HapiApiSpec childStorage() {
		Map<String, String> props = new HashMap<>();

		var MAX_GAS_LIMIT_PROP = "maxGasLimit";
		var MAX_CONTRACT_SIZE_KB_PROP = "maxContractStateSize";

		return defaultHapiSpec("ChildStorage")
				.given(
						getFileContents(APP_PROPERTIES).addingConfigListTo(props),
						fileCreate("bytecode").path(PATH_TO_CHILD_STORAGE_BYTECODE),
						contractCreate("childStorage").bytecode("bytecode")
				).when(
						withOpContext((spec, opLog) -> {
							int almostFullKb = Integer.parseInt(props.get(MAX_CONTRACT_SIZE_KB_PROP)) * 3 / 4;
							long kbPerStep = 16;

							for (int childKbStorage = 0; childKbStorage <= almostFullKb; childKbStorage += kbPerStep) {
								var subOp1 = contractCall(
										"childStorage", GROW_CHILD_ABI, 0, kbPerStep, 17);
								var subOp2 = contractCall(
										"childStorage", GROW_CHILD_ABI, 1, kbPerStep, 19);
								CustomSpecAssert.allRunFor(spec, subOp1, subOp2);
							}
						})
				).then(flattened(
						valuesMatch(19, 17, 19),
						contractCall( "childStorage", SET_ZERO_READ_ONE_ABI, 23),
						valuesMatch(23, 23, 19),
						contractCall( "childStorage", SET_BOTH_ABI, 29)
								.hasKnownStatus(MAX_CONTRACT_STORAGE_EXCEEDED),
						valuesMatch(23, 23, 19)
				));
	}

	private HapiSpecOperation[] valuesMatch(long parent, long child0, long child1) {
		return new HapiSpecOperation[] {
				contractCallLocal("childStorage", GET_CHILD_VALUE_ABI, 0)
						.has(resultWith().resultThruAbi(
						GET_CHILD_VALUE_ABI,
						isLiteralResult(new Object[] { BigInteger.valueOf(child0) })))
						.expectStrictCostAnswer(),
				contractCallLocal("childStorage", GET_CHILD_VALUE_ABI, 1)
						.has(resultWith().resultThruAbi(
						GET_CHILD_VALUE_ABI,
						isLiteralResult(new Object[] { BigInteger.valueOf(child1) })))
						.expectStrictCostAnswer(),
				contractCallLocal("childStorage", GET_MY_VALUE_ABI)
						.has(resultWith().resultThruAbi(
						GET_MY_VALUE_ABI,
						isLiteralResult(new Object[] { BigInteger.valueOf(parent) })))
						.expectStrictCostAnswer(),
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
