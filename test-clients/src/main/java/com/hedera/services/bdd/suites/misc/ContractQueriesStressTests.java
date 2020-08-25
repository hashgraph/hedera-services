package com.hedera.services.bdd.suites.misc;

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
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.concurrent.TimeUnit.SECONDS;

public class ContractQueriesStressTests extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractQueriesStressTests.class);

	final String PATH_TO_CHILD_STORAGE_BYTECODE = "src/main/resource/testfiles/ChildStorage.bin";

	private static final String GET_MY_VALUE_ABI =
			"{\"constant\":true," +
					"\"inputs\":[],\"name\":\"getMyValue\"," +
					"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_get\",\"type\":\"uint256\"}]," +
					"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}\n";
	private static final String SET_ZERO_READ_ONE_ABI =
			"{\"constant\":false," +
					"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}]," +
					"\"name\":\"setZeroReadOne\"," +
					"\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"_getOne\",\"type\":\"uint256\"}]," +
					"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}\n";
	private static final String GROW_CHILD_ABI =
			"{\"constant\":false," +
					"\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_childId\",\"type\":\"uint256\"}," +
					"{\"internalType\":\"uint256\",\"name\":\"_howManyKB\",\"type\":\"uint256\"}," +
					"{\"internalType\":\"uint256\",\"name\":\"_value\",\"type\":\"uint256\"}]," +
					"\"name\":\"growChild\"," +
					"\"outputs\":[]," +
					"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}\n";

	private AtomicLong duration = new AtomicLong(30);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(100);

	public static void main(String... args) {
		new ContractQueriesStressTests().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						contractCallLocalStress(),
						getContractRecordsStress(),
						getContractBytecodeStress(),
						getContractInfoStress(),
				}
		);
	}

	private HapiApiSpec getContractInfoStress() {
		return defaultHapiSpec("GetContractInfoStress")
				.given().when().then(
						withOpContext((spec, opLog) -> configureFromCi(spec)),
						runWithProvider(getContractInfoFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private HapiApiSpec getContractBytecodeStress() {
		return defaultHapiSpec("GetAccountRecordsStress")
				.given().when().then(
						withOpContext((spec, opLog) -> configureFromCi(spec)),
						runWithProvider(getContractBytecodeFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private HapiApiSpec contractCallLocalStress() {
		return defaultHapiSpec("ContractCallLocalStress")
				.given().when().then(
						withOpContext((spec, opLog) -> configureFromCi(spec)),
						runWithProvider(contractCallLocalFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private HapiApiSpec getContractRecordsStress() {
		return defaultHapiSpec("GetContractRecordsStress")
				.given().when().then(
						withOpContext((spec, opLog) -> configureFromCi(spec)),
						runWithProvider(getContractRecordsFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private Function<HapiApiSpec, OpProvider> getContractRecordsFactory() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						fileCreate("bytecode").path(PATH_TO_CHILD_STORAGE_BYTECODE),
						contractCreate("childStorage").bytecode("bytecode"),
						contractCall( "childStorage", GROW_CHILD_ABI, 0, 1, 1),
						contractCall( "childStorage", GROW_CHILD_ABI, 1, 1, 3),
						contractCall( "childStorage", SET_ZERO_READ_ONE_ABI, 23).via("first"),
						contractCall( "childStorage", SET_ZERO_READ_ONE_ABI, 23).via("second"),
						contractCall( "childStorage", SET_ZERO_READ_ONE_ABI, 23).via("third")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				return Optional.of(getAccountRecords("somebody")
						.has(inOrder(
								recordWith().txnId("first"),
								recordWith().txnId("second"),
								recordWith().txnId("third")))
						.noLogging());
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> getContractInfoFactory() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						fileCreate("bytecode").path(PATH_TO_CHILD_STORAGE_BYTECODE),
						contractCreate("childStorage").bytecode("bytecode")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				return Optional.of(getContractInfo("childStorage").noLogging());
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> getContractBytecodeFactory() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						fileCreate("bytecode").path(PATH_TO_CHILD_STORAGE_BYTECODE),
						contractCreate("childStorage").bytecode("bytecode")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				return Optional.of(getContractBytecode("childStorage").noLogging());
			}
		};
	}

	private Function<HapiApiSpec, OpProvider> contractCallLocalFactory() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						fileCreate("bytecode").path(PATH_TO_CHILD_STORAGE_BYTECODE),
						contractCreate("childStorage").bytecode("bytecode")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				var op = contractCallLocal("childStorage", GET_MY_VALUE_ABI)
						.noLogging()
						.has(resultWith().resultThruAbi(
								GET_MY_VALUE_ABI,
								isLiteralResult(new Object[] { BigInteger.valueOf(73) })));
				return Optional.of(op);
			}
		};
	}

	private void configureFromCi(HapiApiSpec spec) {
		HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
		configure("duration", duration::set, ciProps, ciProps::getLong);
		configure("unit", unit::set, ciProps, ciProps::getTimeUnit);
		configure("maxOpsPerSec", maxOpsPerSec::set, ciProps, ciProps::getInteger);
	}

	private <T> void configure(
			String name,
			Consumer<T> configurer,
			HapiPropertySource ciProps,
			Function<String, T> getter
	) {
		if (ciProps.has(name)) {
			configurer.accept(getter.apply(name));
		}
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
