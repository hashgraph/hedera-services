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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;

public class SStoreSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SStoreSuite.class);
	public static final int MAX_CONTRACT_STORAGE_KB = 1024;
	public static final int MAX_CONTRACT_GAS = 15_000_000;
	AtomicReference<ByteString> legacyProps = new AtomicReference<>();

	public static void main(String... args) {
		new SStoreSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				setupAppProperties(),
				multipleSStoreOpsSucceed(),
				benchmarkSingleSetter(),
				childStorage(),
				temporarySStoreRefundTest(),
				cleanupAppProperties()
		});
	}

	private HapiApiSpec setupAppProperties() {
		return HapiApiSpec.defaultHapiSpec("Setup")
				.given(
						withOpContext((spec, opLog) -> {
							final var lookup = getFileContents(APP_PROPERTIES);
							allRunFor(spec, lookup);
							final var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
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

	// This test is failing with CONSENSUS_GAS_EXHAUSTED prior the refactor.
	HapiApiSpec multipleSStoreOpsSucceed() {
		final var contract = "GrowArray";
		final var GAS_TO_OFFER = 6_000_000L;
		return HapiApiSpec.defaultHapiSpec("MultipleSStoresShouldWork")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"contracts.maxGas", "" + GAS_TO_OFFER
								)),
						uploadInitCode(contract),
						contractCreate(contract)
				)
				.when(
						withOpContext((spec, opLog) -> {
							final var step = 16;
							List<HapiSpecOperation> subOps = new ArrayList<>();

							for (int sizeNow = step; sizeNow < MAX_CONTRACT_STORAGE_KB; sizeNow += step) {
								final var subOp1 = contractCall(contract, "growTo", sizeNow)
										.gas(GAS_TO_OFFER)
										.logged();
								subOps.add(subOp1);
							}
							CustomSpecAssert.allRunFor(spec, subOps);
						})
				)
				.then(
						withOpContext((spec, opLog) -> {
							final var numberOfIterations = 10;
							List<HapiSpecOperation> subOps = new ArrayList<>();

							for (int i = 0; i < numberOfIterations; i++) {
								final var subOp1 = contractCall(contract, "changeArray",
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
		final var contract = "ChildStorage";
		return defaultHapiSpec("ChildStorage")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"contracts.maxGas", "" + MAX_CONTRACT_GAS
								)),
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
						withOpContext((spec, opLog) -> {
							final var almostFullKb = MAX_CONTRACT_STORAGE_KB * 3 / 4;
							final var kbPerStep = 16;

							for (int childKbStorage = 0; childKbStorage <= almostFullKb; childKbStorage += kbPerStep) {
								final var subOp1 = contractCall(contract, "growChild",
										0, kbPerStep, 17)
										.gas(MAX_CONTRACT_GAS)
										.via("small" + childKbStorage);
								final var subOp2 = contractCall(contract, "growChild",
										1, kbPerStep, 19)
										.gas(MAX_CONTRACT_GAS)
										.via("large" + childKbStorage);
								final var subOp3 = getTxnRecord("small" + childKbStorage).logged();
								final var subOp4 = getTxnRecord("large" + childKbStorage).logged();
								CustomSpecAssert.allRunFor(spec, subOp1, subOp2, subOp3, subOp4);
							}
						})
				).then(flattened(
						valuesMatch(contract, 19, 17, 19),
						contractCall(contract, "setZeroReadOne", 23),
						valuesMatch(contract, 23, 23, 19),
						contractCall(contract, "setBoth", 29),
						valuesMatch(contract, 29, 29, 29)
				));
	}

	private HapiSpecOperation[] valuesMatch(final String contract, final long parent, final long child0, final long child1) {
		return new HapiSpecOperation[]{
				contractCallLocal(contract, "getChildValue", 0)
						.has(resultWith().resultThruAbi(getABIFor(FUNCTION, "getChildValue", contract),
						isLiteralResult(new Object[]{BigInteger.valueOf(child0)}))),
				contractCallLocal(contract, "getChildValue", 1)
						.has(resultWith().resultThruAbi(getABIFor(FUNCTION, "getChildValue", contract),
						isLiteralResult(new Object[]{BigInteger.valueOf(child1)}))),
				contractCallLocal(contract, "getMyValue")
						.has(resultWith().resultThruAbi(getABIFor(FUNCTION, "getMyValue", contract),
						isLiteralResult(new Object[]{BigInteger.valueOf(parent)}))),
		};
	}

	private HapiApiSpec benchmarkSingleSetter() {
		final var contract = "Benchmark";
		final var GAS_LIMIT = 1_000_000;
		return defaultHapiSpec("SimpleStorage")
				.given(
						cryptoCreate("payer")
								.balance(10 * ONE_HUNDRED_HBARS),
						uploadInitCode(contract)
				)
				.when(
						contractCreate(contract)
								.payingWith("payer")
								.via("creationTx")
								.gas(GAS_LIMIT),
						contractCall(contract, "twoSSTOREs", Bytes.fromHexString("0x05").toArray()
						)
								.gas(GAS_LIMIT)
								.via("storageTx")
				).then(
						getTxnRecord("storageTx").logged(),
						contractCallLocal(contract, "counter")
								.nodePayment(1_234_567)
								.has(
										ContractFnResultAsserts.resultWith()
												.resultThruAbi(
														Utils.getABIFor(FUNCTION, "counter", contract),
														ContractFnResultAsserts.isLiteralResult(
																new Object[]{BigInteger.valueOf(1L)}
														)
												)
								)
				);
	}

	HapiApiSpec temporarySStoreRefundTest() {
		final var contract = "TemporarySStoreRefund";
		return defaultHapiSpec("TemporarySStoreRefundTest")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
						contractCall(contract, "holdTemporary", 10).via("tempHoldTx"),
						contractCall(contract, "holdPermanently", 10).via("permHoldTx")
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
						UtilVerbs.resetToDefault("contracts.maxRefundPercentOfGasLimit")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
