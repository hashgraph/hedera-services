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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class SStoreSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SStoreSuite.class);
	public static final int MAX_CONTRACT_STORAGE_KB = 1024;
	public static final int MAX_CONTRACT_GAS = 15_000_000;
	AtomicReference<ByteString> legacyProps = new AtomicReference<>();

	public static void main(String... args) {
		new SStoreSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				setupAppProperties(),
				multipleSStoreOpsSucceed(),
				benchmarkSingleSetter(),
				childStorage(),
				cleanupAppProperties(),
				temporarySStoreRefundTest()
		});
	}

	private HapiApiSpec setupAppProperties() {
		return HapiApiSpec.defaultHapiSpec("Setup")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						}),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"contracts.maxGas", "" + MAX_CONTRACT_GAS,
										"contracts.throttle.throttleByGas", "false")))
				.when()
				.then();
	}

	private HapiApiSpec cleanupAppProperties() {
		return HapiApiSpec.defaultHapiSpec("Cleanup")
				.given(fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL).contents(ignore -> legacyProps.get()))
				.when()
				.then();
	}

	HapiApiSpec multipleSStoreOpsSucceed() {
		return HapiApiSpec.defaultHapiSpec("MultipleSStoresShouldWork")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"contracts.maxGas", "" + MAX_CONTRACT_GAS
								)),
						fileCreate("bigArrayContractFile").path(ContractResources.GROW_ARRAY_BYTECODE_PATH),
						contractCreate("bigArrayContract").bytecode("bigArrayContractFile")
				)
				.when(
						withOpContext((spec, opLog) -> {
							int step = 16;
							List<HapiSpecOperation> subOps = new ArrayList<>();

							for (int sizeNow = step; sizeNow < MAX_CONTRACT_STORAGE_KB; sizeNow += step) {
								var subOp1 = contractCall(
										"bigArrayContract", ContractResources.GROW_ARRAY_GROW_TO, sizeNow)
										.gas(MAX_CONTRACT_GAS)
										.logged();
								subOps.add(subOp1);
							}
							CustomSpecAssert.allRunFor(spec, subOps);
						})
				)
				.then(
						withOpContext((spec, opLog) -> {
							long numberOfIterations = 10;
							List<HapiSpecOperation> subOps = new ArrayList<>();

							for (int i = 0; i < numberOfIterations; i++) {
								var subOp1 = contractCall(
										"bigArrayContract", ContractResources.GROW_ARRAY_CHANGE_ARRAY,
										ThreadLocalRandom.current().nextInt(1000))
										.logged();
								subOps.add(subOp1);
							}
							CustomSpecAssert.allRunFor(spec, subOps);
						})
				);
	}

	HapiApiSpec childStorage() {
		// Successfully exceeds deprecated max contract storage of 1 KB
		return defaultHapiSpec("ChildStorage")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"contracts.maxGas", "" + MAX_CONTRACT_GAS
								)),
						fileCreate("bytecode").path(ContractResources.CHILD_STORAGE_BYTECODE_PATH),
						contractCreate("childStorage").bytecode("bytecode")
				).when(
						withOpContext((spec, opLog) -> {
							int almostFullKb = MAX_CONTRACT_STORAGE_KB * 3 / 4;
							long kbPerStep = 16;

							for (int childKbStorage = 0; childKbStorage <= almostFullKb; childKbStorage += kbPerStep) {
								var subOp1 = contractCall(
										"childStorage", ContractResources.GROW_CHILD_ABI, 0, kbPerStep, 17).gas(
										MAX_CONTRACT_GAS);
								var subOp2 = contractCall(
										"childStorage", ContractResources.GROW_CHILD_ABI, 1, kbPerStep, 19).gas(
										MAX_CONTRACT_GAS);
								CustomSpecAssert.allRunFor(spec, subOp1, subOp2);
							}
						})
				).then(flattened(
						valuesMatch(19, 17, 19),
						contractCall("childStorage", ContractResources.SET_ZERO_READ_ONE_ABI, 23),
						valuesMatch(23, 23, 19),
						contractCall("childStorage", ContractResources.SET_BOTH_ABI, 29),
						valuesMatch(29, 29, 29)
				));
	}

	private HapiSpecOperation[] valuesMatch(long parent, long child0, long child1) {
		return new HapiSpecOperation[] {
				contractCallLocal("childStorage", ContractResources.GET_CHILD_VALUE_ABI, 0)
						.has(resultWith().resultThruAbi(
								ContractResources.GET_CHILD_VALUE_ABI,
								isLiteralResult(new Object[] { BigInteger.valueOf(child0) }))),
				contractCallLocal("childStorage", ContractResources.GET_CHILD_VALUE_ABI, 1)
						.has(resultWith().resultThruAbi(
								ContractResources.GET_CHILD_VALUE_ABI,
								isLiteralResult(new Object[] { BigInteger.valueOf(child1) }))),
				contractCallLocal("childStorage", ContractResources.GET_MY_VALUE_ABI)
						.has(resultWith().resultThruAbi(
								ContractResources.GET_MY_VALUE_ABI,
								isLiteralResult(new Object[] { BigInteger.valueOf(parent) }))),
		};
	}

	private HapiApiSpec benchmarkSingleSetter() {
		final long GAS_LIMIT = 1_000_000;
		return defaultHapiSpec("SimpleStorage")
				.given(
						cryptoCreate("payer")
								.balance(10 * ONE_HUNDRED_HBARS),
						fileCreate("bytecode")
								.path(ContractResources.BENCHMARK_CONTRACT)
								.memo("test-memo-contract")
								.payingWith("payer")
				)
				.when(
						contractCreate("immutableContract")
								.payingWith("payer")
								.bytecode("bytecode")
								.via("creationTx")
								.gas(GAS_LIMIT),
						contractCall(
								"immutableContract",
								ContractResources.TWO_SSTORES,
								Bytes.fromHexString("0x05").toArray()
						)
								.gas(GAS_LIMIT)
								.via("storageTx")
				).then(
						contractCallLocal("immutableContract", ContractResources.BENCHMARK_GET_COUNTER)
								.nodePayment(1_234_567)
								.has(
										ContractFnResultAsserts.resultWith()
												.resultThruAbi(
														ContractResources.BENCHMARK_GET_COUNTER,
														ContractFnResultAsserts.isLiteralResult(
																new Object[] { BigInteger.valueOf(1L) }
														)
												)
								)
				);
	}

	HapiApiSpec temporarySStoreRefundTest() {
		return defaultHapiSpec("TemporarySStoreRefundTest")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						fileCreate("bytecode").path(ContractResources.TEMPORARY_SSTORE_REFUND_CONTRACT),
						contractCreate("sStoreRefundContract").bytecode("bytecode")
				).when(
						contractCall(
								"sStoreRefundContract",
								ContractResources.TEMPORARY_SSTORE_HOLD_TEMPORARY_ABI, 10).via("tempHoldTx"),
						contractCall(
								"sStoreRefundContract",
								ContractResources.TEMPORARY_SSTORE_HOLD_PERMANENTLY_ABI, 10).via("permHoldTx")
				).then(
						withOpContext((spec, opLog) -> {
							final var subop01 = getTxnRecord("tempHoldTx")
									.saveTxnRecordToRegistry("tempHoldTxRec").logged();
							final var subop02 = getTxnRecord("permHoldTx")
									.saveTxnRecordToRegistry("permHoldTxRec").logged();

							CustomSpecAssert.allRunFor(spec, subop01, subop02);

							final var gasUsedForTemporaryHoldTx = spec.registry()
									.getTransactionRecord("tempHoldTxRec").getContractCallResult().getGasUsed();
							final var gasUsedForPermanentHoldTx = spec.registry()
									.getTransactionRecord("permHoldTxRec").getContractCallResult().getGasUsed();

							Assertions.assertTrue(gasUsedForTemporaryHoldTx < 23535L);
							Assertions.assertTrue(gasUsedForPermanentHoldTx > 20000L);
						}),
						UtilVerbs.resetAppPropertiesTo(
								"src/main/resource/bootstrap.properties")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
