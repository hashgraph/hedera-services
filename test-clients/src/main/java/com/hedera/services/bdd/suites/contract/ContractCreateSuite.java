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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.EMPTY_CONSTRUCTOR;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STORAGE_ACCESS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContractCreateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCreateSuite.class);

	public static void main(String... args) {
		new ContractCreateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				createEmptyConstructor(),
				insufficientPayerBalanceUponCreation(),
				rejectsInvalidMemo(),
				rejectsInsufficientFee(),
				rejectsInvalidBytecode(),
				revertsNonzeroBalance(),
				createFailsIfMissingSigs(),
				rejectsInsufficientGas(),
				createsVanillaContractAsExpectedWithOmittedAdminKey(),
				childCreationsHaveExpectedKeysWithOmittedAdminKey(),
				contractCreateAccountAndStorageKeysWarmUpReducesGasCost(),
				rejectsInvalidStorageAccessKey()
		);
	}

	private HapiApiSpec insufficientPayerBalanceUponCreation() {
		return defaultHapiSpec("InsufficientPayerBalanceUponCreation")
				.given(
						cryptoCreate("bankrupt")
								.balance(0L),
						fileCreate("contractCode")
								.path(EMPTY_CONSTRUCTOR)
				)
				.when()
				.then(
						contractCreate("defaultContract")
								.bytecode("contractCode")
								.payingWith("bankrupt")
								.hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
				);
	}

	private HapiApiSpec createsVanillaContractAsExpectedWithOmittedAdminKey() {
		final var name = "testContract";

		return defaultHapiSpec("CreatesVanillaContract")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate(name)
								.omitAdminKey()
								.bytecode("contractFile"),
						getContractInfo(name)
								.has(contractWith().immutableContractKey(name))
								.logged()
				);
	}

	private HapiApiSpec childCreationsHaveExpectedKeysWithOmittedAdminKey() {
		final AtomicLong firstStickId = new AtomicLong();
		final AtomicLong secondStickId = new AtomicLong();
		final AtomicLong thirdStickId = new AtomicLong();
		final String txn = "creation";

		return defaultHapiSpec("ChildCreationsHaveExpectedKeysWithOmittedAdminKey")
				.given(
						fileCreate("bytecode").path(ContractResources.FUSE_BYTECODE_PATH),
						contractCreate("fuse").bytecode("bytecode").omitAdminKey().via(txn),
						withOpContext((spec, opLog) -> {
							final var op = getTxnRecord(txn);
							allRunFor(spec, op);
							final var record = op.getResponseRecord();
							final var creationResult = record.getContractCreateResult();
							final var createdIds = creationResult.getCreatedContractIDsList();
							assertEquals(
									4, createdIds.size(),
									"Expected four creations but got " + createdIds);
							firstStickId.set(createdIds.get(1).getContractNum());
							secondStickId.set(createdIds.get(2).getContractNum());
							thirdStickId.set(createdIds.get(3).getContractNum());
						})
				).when(
						sourcing(() -> getContractInfo("0.0." + firstStickId.get())
								.has(contractWith().immutableContractKey("0.0." + firstStickId.get()))
								.logged()),
						sourcing(() -> getContractInfo("0.0." + secondStickId.get())
								.has(contractWith().immutableContractKey("0.0." + secondStickId.get()))
								.logged()),
						sourcing(() -> getContractInfo("0.0." + thirdStickId.get())
								.logged()),
						contractCall("fuse", ContractResources.LIGHT_ABI).via("lightTxn")
				).then(
						sourcing(() -> getContractInfo("0.0." + firstStickId.get())
								.hasCostAnswerPrecheck(CONTRACT_DELETED)),
						sourcing(() -> getContractInfo("0.0." + secondStickId.get())
								.hasCostAnswerPrecheck(CONTRACT_DELETED)),
						sourcing(() -> getContractInfo("0.0." + thirdStickId.get())
								.hasCostAnswerPrecheck(CONTRACT_DELETED))
				);
	}

	private HapiApiSpec createEmptyConstructor() {
		return defaultHapiSpec("EmptyConstructor")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.EMPTY_CONSTRUCTOR)
				).when(

				).then(
						contractCreate("emptyConstructorTest")
								.bytecode("contractFile")
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec createFailsIfMissingSigs() {
		KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
		SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
		SigControl invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

		return defaultHapiSpec("CreateFailsIfMissingSigs")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.adminKeyShape(shape)
								.bytecode("contractFile")
								.sigControl(forKey("testContract", invalidSig))
								.hasKnownStatus(INVALID_SIGNATURE),
						contractCreate("testContract")
								.adminKeyShape(shape)
								.bytecode("contractFile")
								.sigControl(forKey("testContract", validSig))
				);
	}

	private HapiApiSpec rejectsInsufficientGas() {
		return defaultHapiSpec("RejectsInsufficientGas")
				.given(
						fileCreate("simpleStorageBytecode")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH)
				).when().then(
						contractCreate("simpleStorage")
								.bytecode("simpleStorageBytecode")
								.gas(0L)
								.hasKnownStatus(INSUFFICIENT_GAS)
				);
	}

	private HapiApiSpec rejectsInvalidMemo() {
		return defaultHapiSpec("RejectsInvalidMemo")
				.given().when().then(
						contractCreate("testContract")
								.entityMemo(TxnUtils.nAscii(101))
								.hasPrecheck(MEMO_TOO_LONG),
						contractCreate("testContract")
								.entityMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING)
				);
	}

	private HapiApiSpec rejectsInsufficientFee() {
		return defaultHapiSpec("RejectsInsufficientFee")
				.given(
						cryptoCreate("payer"),
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.bytecode("contractFile")
								.payingWith("payer")
								.fee(1L)
								.hasPrecheck(INSUFFICIENT_TX_FEE)
				);
	}

	private HapiApiSpec rejectsInvalidBytecode() {
		return defaultHapiSpec("RejectsInvalidBytecode")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.INVALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.bytecode("contractFile")
								.hasKnownStatus(ERROR_DECODING_BYTESTRING)
				);
	}

	private HapiApiSpec revertsNonzeroBalance() {
		return defaultHapiSpec("RevertsNonzeroBalance")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.balance(1L)
								.bytecode("contractFile")
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec contractCreateAccountAndStorageKeysWarmUpReducesGasCost() {


		return defaultHapiSpec("ContractCreateAccountAndStorageKeysWarmUpReducesGasCost")
				.given(
						fileCreate("contractInteractionBaseBytecode").path(ContractResources.CONTRACT_INTERACTIONS_BASE),
						fileCreate("contractInteractionExtraBytecode").path(ContractResources.CONTRACT_INTERACTIONS_EXTRA)
				).when(
						contractCreate("baseContract").bytecode("contractInteractionBaseBytecode").gas(1_000_000L).via("baseContractCreateTx")
				).then(
						withOpContext((spec, opLog) -> {
							var baseContractId = spec.registry().getContractId("baseContract");
							var baseContractAddress = CommonUtils.calculateSolidityAddress(
									(int) baseContractId.getShardNum(),
									baseContractId.getRealmNum(),
									baseContractId.getContractNum());

							var baseContractSetParamsCall = contractCall("baseContract",
									ContractResources.BASE_CONTRACT_SET_PARAMS_ABI,
									1, 1).hasKnownStatus(SUCCESS).via("baseContractSetParamsCallTx");
							var contractCreate = contractCreate("extraContract",
									ContractResources.EXTRA_CONTRACT_CONSTRUCTOR_ABI, baseContractAddress)
									.bytecode("contractInteractionExtraBytecode")
									.gas(1_000_000L)
									.via("contractCreateTx");

							var contractCreateWithAccountWarmUp =
									contractCreate("extraContractWithAccountWarmUp",
											ContractResources.EXTRA_CONTRACT_CONSTRUCTOR_ABI, baseContractAddress)
											.bytecode("contractInteractionExtraBytecode")
											.withAccessList(baseContractId)
											.gas(1_000_000L)
											.via("contractCreateWithAccountWarmUpTx");

							var contractCreateWithAccountAndStorageKeyWarmUp =
									contractCreate("extraContractWithAccountAndStorageWarmUp",
											ContractResources.EXTRA_CONTRACT_CONSTRUCTOR_ABI, baseContractAddress)
											.bytecode("contractInteractionExtraBytecode")
											.withAccessList(baseContractId, ByteString.copyFromUtf8("\000"), ByteString.copyFromUtf8("\001"))
											.gas(1_000_000L)
											.via("contractCreateWithAccountAndStorageKeyWarmUpTx");

							final var contractCreateTxRec =
									getTxnRecord("contractCreateTx")
											.saveTxnRecordToRegistry("contractCreateTxRec")
											.logged();
							final var contractCreateWithAccountWarmUpTxRec =
									getTxnRecord("contractCreateWithAccountWarmUpTx")
											.saveTxnRecordToRegistry("contractCreateWithAccountWarmUpTxRec")
											.logged();
							final var contractCreateWithAccountAndStorageKeyWarmUpTxRec =
									getTxnRecord("contractCreateWithAccountAndStorageKeyWarmUpTx")
											.saveTxnRecordToRegistry("contractCreateWithAccountAndStorageKeyWarmUpTxRec")
											.logged();

							CustomSpecAssert.allRunFor(spec,
									baseContractSetParamsCall,
									contractCreate,
									contractCreateWithAccountWarmUp,
									contractCreateWithAccountAndStorageKeyWarmUp,
									contractCreateTxRec,
									contractCreateWithAccountWarmUpTxRec,
									contractCreateWithAccountAndStorageKeyWarmUpTxRec);

							final var gasUsedForContractCreateTx = spec.registry()
									.getTransactionRecord("contractCreateTxRec")
									.getContractCreateResult()
									.getGasUsed();
							final var gasUsedForContractCreateWithAccountWarmUpTx = spec.registry()
									.getTransactionRecord("contractCreateWithAccountWarmUpTxRec")
									.getContractCreateResult()
									.getGasUsed();
							final var gasUsedForContractCreateWithAccountAndStorageKeyWarmUpTx = spec.registry()
									.getTransactionRecord("contractCreateWithAccountAndStorageKeyWarmUpTxRec")
									.getContractCreateResult()
									.getGasUsed();

							Assertions.assertEquals(
									100L,
									gasUsedForContractCreateTx - gasUsedForContractCreateWithAccountWarmUpTx);
							Assertions.assertEquals(
									200L,
									gasUsedForContractCreateWithAccountWarmUpTx - gasUsedForContractCreateWithAccountAndStorageKeyWarmUpTx);
							Assertions.assertEquals(
									300L,
									gasUsedForContractCreateTx - gasUsedForContractCreateWithAccountAndStorageKeyWarmUpTx);
						})
				);
	}

	private HapiApiSpec rejectsInvalidStorageAccessKey() {

		return defaultHapiSpec("RejectsInvalidStorageAccessKey")
				.given(
						fileCreate("contractInteractionBaseBytecode").path(ContractResources.CONTRACT_INTERACTIONS_BASE),
						fileCreate("contractInteractionExtraBytecode").path(ContractResources.CONTRACT_INTERACTIONS_EXTRA)
				).when(
						contractCreate("baseContract").bytecode("contractInteractionBaseBytecode").gas(1_000_000L).via("baseContractCreateTx")
				).then(
						withOpContext((spec, opLog) -> {
							var baseContractId = spec.registry().getContractId("baseContract");
							var baseContractAddress = CommonUtils.calculateSolidityAddress(
									(int) baseContractId.getShardNum(),
									baseContractId.getRealmNum(),
									baseContractId.getContractNum());

							var baseContractSetParamsCall = contractCall("baseContract",
									ContractResources.BASE_CONTRACT_SET_PARAMS_ABI,
									1, 1).hasKnownStatus(SUCCESS).via("baseContractSetParamsCallTx");
							var contractCreate = contractCreate("extraContract",
									ContractResources.EXTRA_CONTRACT_CONSTRUCTOR_ABI, baseContractAddress)
									.bytecode("contractInteractionExtraBytecode")
									.withAccessList(baseContractId,
											ByteString.copyFromUtf8("\000\000\000\000\000\000\000\000\000\000\000\000" +
													"\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000" +
													"\000\000\000\000\000"),
											ByteString.copyFromUtf8("\001"))
									.gas(1_000_000L)
									.hasKnownStatus(INVALID_STORAGE_ACCESS_KEY);

							CustomSpecAssert.allRunFor(spec,
									baseContractSetParamsCall,
									contractCreate);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
