package com.hedera.services.bdd.suites.contract.hapi;

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

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.utility.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isContractWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.bytecodePath;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadSingleInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.contractListWithPropertiesInheritedFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContractCreateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCreateSuite.class);

	private static final String defaultMaxGas =
			HapiSpecSetup.getDefaultNodeProps().get("contracts.maxGas");
	public static final String EMPTY_CONSTRUCTOR_CONTRACT = "EmptyConstructor";

	public static void main(String... args) {
		new ContractCreateSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
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
						cannotCreateTooLargeContract(),
						revertedTryExtCallHasNoSideEffects(),
						receiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix(),
						cannotSendToNonExistentAccount(),
						delegateContractIdRequiredForTransferInDelegateCall(),
						maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller(),
						minChargeIsTXGasUsedByContractCreate(),
						gasLimitOverMaxGasLimitFailsPrecheck(),
						vanillaSuccess(),
						propagatesNestedCreations(),
						blockTimestampChangesWithinFewSeconds(),
						contractWithAutoRenewNeedSignatures(),
						autoAssociationSlotsAppearsInInfo(),
						getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee(),
//						canCallPendingContractSafely(),
						createContractWithStakingFields()
				}
		);
	}

	HapiApiSpec createContractWithStakingFields() {
		final var contract = "CreateTrivial";
		return defaultHapiSpec("createContractWithStakingFields")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
								.adminKey(THRESHOLD)
								.declinedReward(true)
								.stakedNodeId(0),
						getContractInfo(contract)
								.has(contractWith()
										.isDeclinedReward(true)
										.noStakedAccountId()
										.stakedNodeId(0))
								.logged()
				).when(
						contractCreate(contract)
								.adminKey(THRESHOLD)
								.declinedReward(true)
								.stakedAccountId("0.0.10"),
						getContractInfo(contract)
								.has(contractWith()
										.isDeclinedReward(true)
										.noStakingNodeId()
										.stakedAccountId("0.0.10"))
								.logged()
				).then(
						contractCreate(contract)
								.adminKey(THRESHOLD)
								.declinedReward(false)
								.stakedNodeId(0),
						getContractInfo(contract)
								.has(contractWith()
										.isDeclinedReward(false)
										.noStakedAccountId()
										.stakedNodeId(0))
								.logged(),
						contractCreate(contract)
								.adminKey(THRESHOLD)
								.declinedReward(false)
								.stakedAccountId("0.0.10"),
						getContractInfo(contract)
								.has(contractWith()
										.isDeclinedReward(false)
										.noStakingNodeId()
										.stakedAccountId("0.0.10"))
								.logged()
				);
	}


	private HapiApiSpec autoAssociationSlotsAppearsInInfo() {
		final int maxAutoAssociations = 100;
		final int ADVENTUROUS_NETWORK = 1_000;
		final String CONTRACT = "Multipurpose";
		final String associationsLimitProperty = "entities.limitTokenAssociations";
		final String defaultAssociationsLimit =
				HapiSpecSetup.getDefaultNodeProps().get(associationsLimitProperty);

		return defaultHapiSpec("autoAssociationSlotsAppearsInInfo")
				.given(
						overridingTwo(
								"entities.limitTokenAssociations", "true",
								"tokens.maxPerAccount", "" + 1)
				).when().then(
						newKeyNamed(ADMIN_KEY),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
								.maxAutomaticTokenAssociations(maxAutoAssociations)
								.hasPrecheck(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),

						// Default is NOT to limit associations for entities
						overriding(associationsLimitProperty, defaultAssociationsLimit),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
								.maxAutomaticTokenAssociations(maxAutoAssociations),
						getContractInfo(CONTRACT)
								.has(ContractInfoAsserts.contractWith().maxAutoAssociations(maxAutoAssociations))
								.logged(),
						// Restore default
						overriding("tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK)
				);
	}

	private HapiApiSpec insufficientPayerBalanceUponCreation() {
		return defaultHapiSpec("InsufficientPayerBalanceUponCreation")
				.given(
						cryptoCreate("bankrupt")
								.balance(0L),
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				)
				.when()
				.then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.payingWith("bankrupt")
								.hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
				);
	}

	private HapiApiSpec canCallPendingContractSafely() {
		final var numSlots = 64;
		final var createBurstSize = 500;
		final int[] targets = { 19, 24 };
		final AtomicLong createdFileNum = new AtomicLong();
		final var callTxn = "callTxn";
		final var contract = "FibonacciPlus";

		return defaultHapiSpec("CanCallPendingContractSafely")
				.given(
						UtilVerbs.overriding("contracts.throttle.throttleByGas", "false"),
						uploadSingleInitCode(contract, 1000, GENESIS, createdFileNum::set),
						inParallel(IntStream.range(0, createBurstSize)
								.mapToObj(i ->
										contractCustomCreate(contract, String.valueOf(i), numSlots)
												.fee(ONE_HUNDRED_HBARS)
												.gas(300_000L)
												.payingWith(GENESIS)
												.noLogging()
												.deferStatusResolution()
												.bytecode(contract)
												.adminKey(THRESHOLD))
								.toArray(HapiSpecOperation[]::new))
				).when().then(
						sourcing(() ->
								contractCallWithFunctionAbi(
										"0.0." + (createdFileNum.get() + createBurstSize),
										getABIFor(FUNCTION, "addNthFib", contract), targets, 12
								)
										.payingWith(GENESIS)
										.gas(300_000L)
										.via(callTxn)),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	HapiApiSpec cannotSendToNonExistentAccount() {
		final var contract = "Multipurpose";
		Object[] donationArgs = new Object[] { 666666, "Hey, Ma!" };

		return defaultHapiSpec("CannotSendToNonExistentAccount").given(
				uploadInitCode(contract)
		).when(
				contractCreate(contract)
						.balance(666)
		).then(
				contractCall(contract, "donate", donationArgs)
						.hasKnownStatus(INVALID_SOLIDITY_ADDRESS)
		);
	}

	private HapiApiSpec createsVanillaContractAsExpectedWithOmittedAdminKey() {
		return defaultHapiSpec("CreatesVanillaContract")
				.given(
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when().then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.omitAdminKey(),
						getContractInfo(EMPTY_CONSTRUCTOR_CONTRACT)
								.has(contractWith().immutableContractKey(EMPTY_CONSTRUCTOR_CONTRACT))
								.logged()
				);
	}

	private HapiApiSpec childCreationsHaveExpectedKeysWithOmittedAdminKey() {
		final AtomicLong firstStickId = new AtomicLong();
		final AtomicLong secondStickId = new AtomicLong();
		final AtomicLong thirdStickId = new AtomicLong();
		final var txn = "creation";
		final var contract = "Fuse";

		return defaultHapiSpec("ChildCreationsHaveExpectedKeysWithOmittedAdminKey")
				.given(
						uploadInitCode(contract),
						contractCreate(contract).omitAdminKey().gas(300_000).via(txn),
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
						contractCall(contract, "light").via("lightTxn")
				).then(
						sourcing(() -> getContractInfo("0.0." + firstStickId.get())
								.has(contractWith().isDeleted())),
						sourcing(() -> getContractInfo("0.0." + secondStickId.get())
								.has(contractWith().isDeleted())),
						sourcing(() -> getContractInfo("0.0." + thirdStickId.get())
								.has(contractWith().isDeleted()))
				);
	}

	private HapiApiSpec createEmptyConstructor() {
		return defaultHapiSpec("EmptyConstructor")
				.given(
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when(

				).then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec propagatesNestedCreations() {
		final var call = "callTxn";
		final var creation = "createTxn";
		final var contract = "NestedCreations";

		final var adminKey = "adminKey";
		final var entityMemo = "JUST DO IT";
		final var customAutoRenew = 7776001L;
		final AtomicReference<String> firstLiteralId = new AtomicReference<>();
		final AtomicReference<String> secondLiteralId = new AtomicReference<>();
		final AtomicReference<ByteString> expectedFirstAddress = new AtomicReference<>();
		final AtomicReference<ByteString> expectedSecondAddress = new AtomicReference<>();

		return defaultHapiSpec("PropagatesNestedCreations")
				.given(
						newKeyNamed(adminKey),
						uploadInitCode(contract),
						contractCreate(contract)
								.proxy("0.0.3")
								.adminKey(adminKey)
								.entityMemo(entityMemo)
								.autoRenewSecs(customAutoRenew)
								.via(creation)
				).when(
						contractCall(contract, "propagate")
								.gas(4_000_000L)
								.via(call)
				).then(
						withOpContext((spec, opLog) -> {
							final var parentNum = spec.registry().getContractId(contract);
							final var firstId = ContractID.newBuilder()
									.setContractNum(parentNum.getContractNum() + 1L)
									.build();
							firstLiteralId.set(HapiPropertySource.asContractString(firstId));
							expectedFirstAddress.set(ByteString.copyFrom(asSolidityAddress(firstId)));
							final var secondId = ContractID.newBuilder()
									.setContractNum(parentNum.getContractNum() + 2L)
									.build();
							secondLiteralId.set(HapiPropertySource.asContractString(secondId));
							expectedSecondAddress.set(ByteString.copyFrom(asSolidityAddress(secondId)));
						}),
						sourcing(() -> childRecordsCheck(call, SUCCESS,
								recordWith()
										.contractCreateResult(resultWith().evmAddress(expectedFirstAddress.get()))
										.status(SUCCESS),
								recordWith()
										.contractCreateResult(resultWith().evmAddress(expectedSecondAddress.get()))
										.status(SUCCESS))),
						sourcing(() -> getContractInfo(firstLiteralId.get())
								.has(contractWith().propertiesInheritedFrom(contract)))
				);
	}

	private HapiApiSpec revertedTryExtCallHasNoSideEffects() {
		final var balance = 3_000;
		final int sendAmount = balance / 3;
		final var contract = "RevertingSendTry";
		final var aBeneficiary = "aBeneficiary";
		final var bBeneficiary = "bBeneficiary";
		final var txn = "txn";

		return defaultHapiSpec("RevertedTryExtCallHasNoSideEffects")
				.given(
						uploadInitCode(contract),
						contractCreate(contract).balance(balance),
						cryptoCreate(aBeneficiary).balance(0L),
						cryptoCreate(bBeneficiary).balance(0L)
				).when(
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							final var aNum = (int) registry.getAccountID(aBeneficiary).getAccountNum();
							final var bNum = (int) registry.getAccountID(bBeneficiary).getAccountNum();
							final var sendArgs = new Object[] { sendAmount, aNum, bNum };

							final var op = contractCall(contract, "sendTo", sendArgs
							)
									.gas(110_000)
									.via(txn);
							allRunFor(spec, op);
						})
				).then(
						getTxnRecord(txn).logged(),
						getAccountBalance(aBeneficiary).logged(),
						getAccountBalance(bBeneficiary).logged()
				);
	}

	private HapiApiSpec createFailsIfMissingSigs() {
		final var shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
		final var validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
		final var invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

		return defaultHapiSpec("CreateFailsIfMissingSigs")
				.given(
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when().then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.adminKeyShape(shape)
								.sigControl(forKey(EMPTY_CONSTRUCTOR_CONTRACT, invalidSig))
								.hasKnownStatus(INVALID_SIGNATURE),
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.adminKeyShape(shape)
								.sigControl(forKey(EMPTY_CONSTRUCTOR_CONTRACT, validSig))
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec rejectsInsufficientGas() {
		return defaultHapiSpec("RejectsInsufficientGas")
				.given(
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when().then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.gas(0L)
								.hasKnownStatus(INSUFFICIENT_GAS)
				);
	}

	private HapiApiSpec rejectsInvalidMemo() {
		return defaultHapiSpec("RejectsInvalidMemo")
				.given().when().then(
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT),
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.entityMemo(TxnUtils.nAscii(101))
								.hasPrecheck(MEMO_TOO_LONG),
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.entityMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING)
				);
	}

	private HapiApiSpec rejectsInsufficientFee() {
		return defaultHapiSpec("RejectsInsufficientFee")
				.given(
						cryptoCreate("payer"),
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when().then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.payingWith("payer")
								.fee(1L)
								.hasPrecheck(INSUFFICIENT_TX_FEE)
				);
	}

	private HapiApiSpec rejectsInvalidBytecode() {
		final var contract = "InvalidBytecode";
		return defaultHapiSpec("RejectsInvalidBytecode")
				.given(
						uploadInitCode(contract)
				).when().then(
						contractCreate(contract)
								.hasKnownStatus(ERROR_DECODING_BYTESTRING)
				);
	}

	private HapiApiSpec revertsNonzeroBalance() {
		return defaultHapiSpec("RevertsNonzeroBalance")
				.given(
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when().then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.balance(1L)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec delegateContractIdRequiredForTransferInDelegateCall() {
		final var justSendContract = "JustSend";
		final var sendInternalAndDelegateContract = "SendInternalAndDelegate";

		final var beneficiary = "civilian";
		final var totalToSend = 1_000L;
		final var origKey = KeyShape.threshOf(1, SIMPLE, CONTRACT);
		final var revisedKey = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
		final var newKey = "delegateContractKey";

		final AtomicLong justSendContractNum = new AtomicLong();
		final AtomicLong beneficiaryAccountNum = new AtomicLong();

		return defaultHapiSpec("DelegateContractIdRequiredForTransferInDelegateCall")
				.given(
						uploadInitCode(justSendContract, sendInternalAndDelegateContract),
						contractCreate(justSendContract)
								.gas(300_000L)
								.exposingNumTo(justSendContractNum::set),
						contractCreate(sendInternalAndDelegateContract)
								.gas(300_000L)
								.balance(2 * totalToSend)
				).when(
						cryptoCreate(beneficiary)
								.balance(0L)
								.keyShape(origKey.signedWith(sigs(ON, sendInternalAndDelegateContract)))
								.receiverSigRequired(true)
								.exposingCreatedIdTo(id -> beneficiaryAccountNum.set(id.getAccountNum()))
				).then(
						/* Without delegateContractId permissions, the second send via delegate call will
						 * fail, so only half of totalToSend will make it to the beneficiary. (Note the entire
						 * call doesn't fail because exceptional halts in "raw calls" don't automatically
						 * propagate up the stack like a Solidity revert does.) */
						sourcing(() -> contractCall(sendInternalAndDelegateContract, "sendRepeatedlyTo",
								justSendContractNum.get(), beneficiaryAccountNum.get(), totalToSend / 2)
						),
						getAccountBalance(beneficiary).hasTinyBars(totalToSend / 2),
						/* But now we update the beneficiary to have a delegateContractId */
						newKeyNamed(newKey).shape(revisedKey.signedWith(sigs(ON, sendInternalAndDelegateContract))),
						cryptoUpdate(beneficiary).key(newKey),
						sourcing(() -> contractCall(sendInternalAndDelegateContract, "sendRepeatedlyTo",
								justSendContractNum.get(), beneficiaryAccountNum.get(), totalToSend / 2)
						),
						getAccountBalance(beneficiary).hasTinyBars(3 * (totalToSend / 2))
				);
	}

	private HapiApiSpec receiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix() {
		final var sendInternalAndDelegateContract = "SendInternalAndDelegate";
		final var justSendContract = "JustSend";
		final var beneficiary = "civilian";
		final var balanceToDistribute = 1_000L;

		final AtomicLong justSendContractNum = new AtomicLong();
		final AtomicLong beneficiaryAccountNum = new AtomicLong();

		return defaultHapiSpec("ReceiverSigReqTransferRecipientMustSignWithFullPubKeyPrefix")
				.given(
						cryptoCreate(beneficiary)
								.balance(0L)
								.receiverSigRequired(true)
								.exposingCreatedIdTo(id -> beneficiaryAccountNum.set(id.getAccountNum())),
						uploadInitCode(sendInternalAndDelegateContract, justSendContract)
				).when(
						contractCreate(justSendContract)
								.gas(300_000L)
								.exposingNumTo(justSendContractNum::set),
						contractCreate(sendInternalAndDelegateContract)
								.gas(300_000L)
								.balance(balanceToDistribute)
				).then(
						/* Sending requires receiver signature */
						sourcing(() -> contractCall(sendInternalAndDelegateContract, "sendRepeatedlyTo",
										justSendContractNum.get(), beneficiaryAccountNum.get(), balanceToDistribute / 2
								)
										.hasKnownStatus(INVALID_SIGNATURE)
						),
						/* But it's not enough to just sign using an incomplete prefix */
						sourcing(() -> contractCall(sendInternalAndDelegateContract, "sendRepeatedlyTo",
										justSendContractNum.get(), beneficiaryAccountNum.get(), balanceToDistribute / 2
								)
										.signedBy(DEFAULT_PAYER, beneficiary)
										.hasKnownStatus(INVALID_SIGNATURE)
						),
						/* We have to specify the full prefix so the sig can be verified async */
						getAccountInfo(beneficiary).logged(),
						sourcing(() -> contractCall(sendInternalAndDelegateContract, "sendRepeatedlyTo",
										justSendContractNum.get(), beneficiaryAccountNum.get(), balanceToDistribute / 2
								)
										.alsoSigningWithFullPrefix(beneficiary)
						),
						getAccountBalance(beneficiary).logged()
				);
	}

	private HapiApiSpec getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee() {
		final var civilian = "civilian";
		final var creation = "creation";
		final var gasToOffer = 128_000L;
		final var civilianStartBalance = ONE_HUNDRED_HBARS;
		final AtomicLong gasFee = new AtomicLong();
		final AtomicLong offeredGasFee = new AtomicLong();
		final AtomicLong nodeAndNetworkFee = new AtomicLong();
		final AtomicLong maxSendable = new AtomicLong();

		return defaultHapiSpec("GetsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee")
				.given(
						cryptoCreate(civilian).balance(civilianStartBalance),
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
								.gas(gasToOffer)
								.payingWith(civilian)
								.balance(0L)
								.via(creation),
						withOpContext((spec, opLog) -> {
							final var lookup = getTxnRecord(creation).logged();
							allRunFor(spec, lookup);
							final var creationRecord = lookup.getResponseRecord();
							final var gasUsed = creationRecord.getContractCreateResult().getGasUsed();
							gasFee.set(tinybarCostOfGas(spec, ContractCreate, gasUsed));
							offeredGasFee.set(tinybarCostOfGas(spec, ContractCreate, gasToOffer));
							nodeAndNetworkFee.set(creationRecord.getTransactionFee() - gasFee.get());
							log.info("Network + node fees were {}, gas fee was {} (sum to {}, compare with {})",
									nodeAndNetworkFee::get, gasFee::get,
									() -> nodeAndNetworkFee.get() + gasFee.get(),
									creationRecord::getTransactionFee);
							maxSendable.set(
									civilianStartBalance
											- 2 * nodeAndNetworkFee.get()
											- gasFee.get()
											- offeredGasFee.get());
							log.info("Maximum amount send-able in precheck should be {}", maxSendable::get);
						})
				).then(
						sourcing(() -> getAccountBalance(civilian)
								.hasTinyBars(civilianStartBalance - nodeAndNetworkFee.get() - gasFee.get())),
						// Fire-and-forget a txn that will leave the civilian payer with 1 too few tinybars at consensus
						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(civilian, FUNDING, 1))
								.payingWith(GENESIS)
								.deferStatusResolution(),
						sourcing(() -> contractCustomCreate(EMPTY_CONSTRUCTOR_CONTRACT, "Clone")
								.gas(gasToOffer)
								.payingWith(civilian)
								.balance(maxSendable.get())
								.hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)
						)
				);
	}

	private long tinybarCostOfGas(
			final HapiApiSpec spec,
			final HederaFunctionality function,
			final long gasAmount
	) {
		final var gasThousandthsOfTinycentPrice = spec.fees()
				.getCurrentOpFeeData()
				.get(function)
				.get(DEFAULT)
				.getServicedata()
				.getGas();
		final var rates = spec.ratesProvider().rates();
		return (gasThousandthsOfTinycentPrice / 1000 * rates.getHbarEquiv()) / rates.getCentEquiv() * gasAmount;
	}

	private HapiApiSpec cannotCreateTooLargeContract() {
		ByteString contents;
		try {
			contents =
					ByteString.copyFrom(Files.readAllBytes(Path.of(bytecodePath("CryptoKitties"))));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		final var FILE_KEY = "fileKey";
		final var KEY_LIST = "keyList";
		final var ACCOUNT = "acc";
		return defaultHapiSpec("cannotCreateLargeContract")
				.given(
						newKeyNamed(FILE_KEY),
						newKeyListNamed(KEY_LIST, List.of(FILE_KEY)),
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS * 10).key(FILE_KEY),
						fileCreate("bytecode")
								.path(bytecodePath("CryptoKitties"))
								.hasPrecheck(TRANSACTION_OVERSIZE)
				)
				.when(
						fileCreate("bytecode").contents("").key(KEY_LIST),
						UtilVerbs.updateLargeFile(ACCOUNT, "bytecode", contents)
				)
				.then(
						contractCreate("contract")
								.bytecode("bytecode")
								.payingWith(ACCOUNT)
								.hasKnownStatus(INSUFFICIENT_GAS)
				);
	}

	private HapiApiSpec maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
		return defaultHapiSpec("MaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "5"),
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via("createTX")
				).then(
						withOpContext((spec, ignore) -> {
							final var subop01 = getTxnRecord("createTX").saveTxnRecordToRegistry("createTXRec");
							allRunFor(spec, subop01);

							final var gasUsed = spec.registry().getTransactionRecord("createTXRec")
									.getContractCreateResult().getGasUsed();
							assertEquals(285_000L, gasUsed);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec minChargeIsTXGasUsedByContractCreate() {
		return defaultHapiSpec("MinChargeIsTXGasUsedByContractCreate")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via("createTX")
				).then(
						withOpContext((spec, ignore) -> {
							final var subop01 = getTxnRecord("createTX").saveTxnRecordToRegistry("createTXRec");
							allRunFor(spec, subop01);

							final var gasUsed = spec.registry().getTransactionRecord("createTXRec")
									.getContractCreateResult().getGasUsed();
							assertTrue(gasUsed > 0L);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
		return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
				.given(
						UtilVerbs.overriding("contracts.maxGas", "100"),
						uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT)
				).when().then(
						contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(101L).hasPrecheck(
								MAX_GAS_LIMIT_EXCEEDED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	HapiApiSpec blockTimestampChangesWithinFewSeconds() {
		final var contract = "EmitBlockTimestamp";
		final var firstBlock = "firstBlock";
		final var timeLoggingTxn = "timeLoggingTxn";

		return defaultHapiSpec("BlockTimestampIsConsensusTime")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
						contractCall(contract, "logNow")
								.via(firstBlock),
						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(GENESIS, FUNDING, 1)),
						sleepFor(3_000),
						contractCall(contract, "logNow")
								.via(timeLoggingTxn)
				).then(
						withOpContext((spec, opLog) -> {
							final var firstBlockOp = getTxnRecord(firstBlock);
							final var recordOp = getTxnRecord(timeLoggingTxn);
							allRunFor(spec, firstBlockOp, recordOp);

							// First block info
							final var firstBlockRecord = firstBlockOp.getResponseRecord();
							final var firstBlockLogs = firstBlockRecord.getContractCallResult().getLogInfoList();
							final var firstBlockTimeLogData = firstBlockLogs.get(0).getData().toByteArray();
							final var firstBlockTimestamp = Longs.fromByteArray(
									Arrays.copyOfRange(firstBlockTimeLogData, 24, 32));
							final var firstBlockHashLogData = firstBlockLogs.get(1).getData().toByteArray();
							final var firstBlockNumber = Longs.fromByteArray(
									Arrays.copyOfRange(firstBlockHashLogData, 24, 32));
							final var firstBlockHash = Bytes32.wrap(
									Arrays.copyOfRange(firstBlockHashLogData, 32, 64));
							assertEquals(Bytes32.ZERO, firstBlockHash);

							// Second block info
							final var secondBlockRecord = recordOp.getResponseRecord();
							final var secondBlockLogs = secondBlockRecord.getContractCallResult().getLogInfoList();
							assertEquals(2, secondBlockLogs.size());
							final var secondBlockTimeLogData = secondBlockLogs.get(0).getData().toByteArray();
							final var secondBlockTimestamp = Longs.fromByteArray(
									Arrays.copyOfRange(secondBlockTimeLogData, 24, 32));
							assertNotEquals(firstBlockTimestamp, secondBlockTimestamp,
									"Block timestamps should change");

							final var secondBlockHashLogData = secondBlockLogs.get(1).getData().toByteArray();
							final var secondBlockNumber = Longs.fromByteArray(
									Arrays.copyOfRange(secondBlockHashLogData, 24, 32));
							assertNotEquals(firstBlockNumber, secondBlockNumber,
									"Wrong previous block number");
							final var secondBlockHash = Bytes32.wrap(
									Arrays.copyOfRange(secondBlockHashLogData, 32, 64));

							assertEquals(Bytes32.ZERO, secondBlockHash);
						}),
						contractCallLocal(contract, "getLastBlockHash")
								.exposingTypedResultsTo(results ->
										log.info("Results were {}", CommonUtils.hex((byte[]) results[0])))
				);
	}

	HapiApiSpec vanillaSuccess() {
		final var contract = "CreateTrivial";
		return defaultHapiSpec("VanillaSuccess")
				.given(
						uploadInitCode(contract),
						contractCreate(contract).adminKey(THRESHOLD).maxAutomaticTokenAssociations(10),
						getContractInfo(contract)
								.has(contractWith().maxAutoAssociations(10))
								.logged()
								.saveToRegistry("parentInfo"),
						upMaxGasTo(1_000_000L)
				).when(
						contractCall(contract, "create")
								.gas(1_000_000L)
								.via("createChildTxn"),
						contractCall(contract, "getIndirect")
								.gas(1_000_000L)
								.via("getChildResultTxn"),
						contractCall(contract, "getAddress")
								.gas(1_000_000L)
								.via("getChildAddressTxn")
				).then(
						getTxnRecord("createChildTxn")
								.saveCreatedContractListToRegistry("createChild")
								.logged(),
						getTxnRecord("getChildResultTxn")
								.hasPriority(recordWith().contractCallResult(
										resultWith()
												.resultThruAbi(getABIFor(FUNCTION, "getIndirect", contract),
														isLiteralResult(new Object[] { BigInteger.valueOf(7L) })))),
						getTxnRecord("getChildAddressTxn")
								.hasPriority(recordWith().contractCallResult(
										resultWith()
												.resultThruAbi(
														getABIFor(FUNCTION, "getAddress", contract),
														isContractWith(contractWith()
																.nonNullContractId()
																.propertiesInheritedFrom("parentInfo")))
												.logs(inOrder()))),
						contractListWithPropertiesInheritedFrom(
								"createChildCallResult", 1, "parentInfo"),
						restoreDefaultMaxGas()
				);
	}

	HapiApiSpec contractWithAutoRenewNeedSignatures() {
		final var contract = "CreateTrivial";
		final var autoRenewAccount = "autoRenewAccount";
		return defaultHapiSpec("contractWithAutoRenewNeedSignatures")
				.given(
						newKeyNamed(ADMIN_KEY),
						uploadInitCode(contract),
						cryptoCreate(autoRenewAccount).balance(ONE_HUNDRED_HBARS),
						contractCreate(contract)
								.adminKey(ADMIN_KEY)
								.autoRenewAccountId(autoRenewAccount)
								.signedBy(DEFAULT_PAYER, ADMIN_KEY)
								.hasKnownStatus(INVALID_SIGNATURE),
						contractCreate(contract)
								.adminKey(ADMIN_KEY)
								.autoRenewAccountId(autoRenewAccount)
								.signedBy(DEFAULT_PAYER, ADMIN_KEY, autoRenewAccount)
								.logged(),
						getContractInfo(contract)
								.has(ContractInfoAsserts.contractWith().autoRenewAccountId(autoRenewAccount))
								.logged()
				).when(
				).then(
				);
	}

	private HapiSpecOperation upMaxGasTo(final long amount) {
		return fileUpdate(APP_PROPERTIES)
				.fee(ONE_HUNDRED_HBARS)
				.payingWith(EXCHANGE_RATE_CONTROL)
				.overridingProps(Map.of(
						"contracts.maxGas", "" + amount
				));
	}

	private HapiSpecOperation restoreDefaultMaxGas() {
		return fileUpdate(APP_PROPERTIES)
				.fee(ONE_HUNDRED_HBARS)
				.payingWith(EXCHANGE_RATE_CONTROL)
				.overridingProps(Map.of(
						"contracts.maxGas", defaultMaxGas
				));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
