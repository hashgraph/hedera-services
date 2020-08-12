package com.hedera.services.bdd.spec.assertions;

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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractInfo;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;

public class ContractFnResultAsserts extends BaseErroringAssertsProvider<ContractFunctionResult> {
	static final Logger log = LogManager.getLogger(ContractFnResultAsserts.class);

	Optional<String> resultAbi = Optional.empty();
	Optional<Function<HapiApiSpec, Function<Object[], Optional<Throwable>>>> objArrayAssert = Optional.empty();

	public static ContractFnResultAsserts resultWith() {
		return new ContractFnResultAsserts();
	}

	public ContractFnResultAsserts resultThruAbi(
			String abi, Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> provider) {
		registerProvider((spec, o) -> {
			Object[] actualObjs = viaAbi(abi, ((ContractFunctionResult)o).getContractCallResult().toByteArray());
			Optional<Throwable> error = provider.apply(spec).apply(actualObjs);
			if (error.isPresent()) {
				throw error.get();
			}
		});
		return this;
	}
	public static Object[] viaAbi(String abi, byte[] bytes) {
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(abi);
		return function.decodeResult(bytes);
	}

	public ContractFnResultAsserts contract(String contract) {
		registerIdLookupAssert(contract, r -> r.getContractID(), ContractID.class, "Bad contract!");
		return this;
	}

	public ContractFnResultAsserts logs(ErroringAssertsProvider<List<ContractLoginfo>> provider) {
		registerProvider((spec, o) -> {
			List<ContractLoginfo> logs = ((ContractFunctionResult)o).getLogInfoList();
			ErroringAsserts<List<ContractLoginfo>> asserts = provider.assertsFor(spec);
			List<Throwable> errors = asserts.errorsIn(logs);
			AssertUtils.rethrowSummaryError(log, "Bad logs!", errors);
		});
		return this;
	}

	public ContractFnResultAsserts error(String msg) {
		registerProvider((spec, o) -> {
			ContractFunctionResult result = (ContractFunctionResult)o;
			Assert.assertEquals("Wrong contract function error!",
					msg, Optional.ofNullable(result.getErrorMessage()).orElse(""));
		});
		return this;
	}

	/* Helpers to create the provider for #resultThruAbi. */
	public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isContractWith(
			ContractInfoAsserts theExpectedInfo) {
		return spec -> actualObjs -> {
			try {
				Assert.assertEquals("Extra contract function return values!", 1, actualObjs.length);
				String implicitContract = "contract" + new Random().nextInt();
				ContractID contract = TxnUtils.asContractId((byte[])actualObjs[0]);
				spec.registry().saveContractId(implicitContract, contract);
				HapiGetContractInfo op = getContractInfo(implicitContract).has(theExpectedInfo);
				Optional<Throwable> opError = op.execFor(spec);
				if (opError.isPresent()) {
					throw opError.get();
				}
			} catch (Throwable t) {
				return Optional.of(t);
			}
			return Optional.empty();
		};
	}

	public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isComputedResult(
			Function<HapiApiSpec, Object[]> resultProvider
	) {
		return spec -> actualObjs -> matchErrors(resultProvider.apply(spec), actualObjs);
	}

	public static Function<HapiApiSpec, Function<Object[], Optional<Throwable>>> isLiteralResult(Object[] objs) {
		return ignore -> actualObjs -> matchErrors(objs, actualObjs);
	}

	private static Optional<Throwable> matchErrors(Object[] expected, Object[] actual) {
		try {
			for (int i = 0; i < Math.max(expected.length, actual.length); i++) {
				try {
					Assert.assertEquals(expected[i], actual[i]);
				} catch (Throwable t) {
					return Optional.of(t);
				}
			}
		} catch (Throwable T) {
			return Optional.of(T);
		}
		return Optional.empty();
	}
}
