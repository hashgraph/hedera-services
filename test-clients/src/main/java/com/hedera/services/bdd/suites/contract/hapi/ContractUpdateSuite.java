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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.precompile.DynamicGasCostSuite.captureChildCreate2MetaFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;

public class ContractUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractUpdateSuite.class);

	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));
	private static final long ONE_DAY = 60 * 60 * 24;
	private static final long ONE_MONTH = 30 * ONE_DAY;
	public static final String ADMIN_KEY = "adminKey";
	public static final String NEW_ADMIN_KEY = "newAdminKey";
	private static final String CONTRACT = "Multipurpose";

	public static void main(String... args) {
		new ContractUpdateSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						updateWithBothMemoSettersWorks(),
						updatingExpiryWorks(),
						rejectsExpiryTooFarInTheFuture(),
						updateAutoRenewWorks(),
						updateAdminKeyWorks(),
						canMakeContractImmutableWithEmptyKeyList(),
						givenAdminKeyMustBeValid(),
						fridayThe13thSpec(),
						updateDoesNotChangeBytecode(),
						eip1014AddressAlwaysHasPriority(),
						immutableContractKeyFormIsStandard(),
						updateAutoRenewAccountWorks(),
						updateStakingFieldsWorks()
				}
		);
	}

	private HapiApiSpec updateStakingFieldsWorks() {
		final var newExpiry = Instant.now().getEpochSecond() + 5 * ONE_MONTH;
		return defaultHapiSpec("updateStakingFieldsWorks")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.declinedReward(true)
								.stakedNodeId(0),
						getContractInfo(CONTRACT)
								.has(contractWith()
										.isDeclinedReward(true)
										.noStakedAccountId()
										.stakedNodeId(0))
								.logged()
				)
				.when(
						contractUpdate(CONTRACT)
								.newDeclinedReward(false)
								.newStakedAccountId("0.0.10"),
						getContractInfo(CONTRACT)
								.has(contractWith()
										.isDeclinedReward(false)
										.noStakingNodeId()
										.stakedAccountId("0.0.10"))
								.logged()
				)
				.then(
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/2877
	private HapiApiSpec eip1014AddressAlwaysHasPriority() {
		final var contract = "VariousCreate2Calls";
		final var creationTxn = "creationTxn";
		final var callTxn = "callTxn";
		final var callcodeTxn = "callcodeTxn";
		final var staticcallTxn = "staticcallTxn";
		final var delegatecallTxn = "delegatecallTxn";

		final AtomicReference<String> childMirror = new AtomicReference<>();
		final AtomicReference<String> childEip1014 = new AtomicReference<>();

		return defaultHapiSpec("Eip1014AddressAlwaysHasPriority")
				.given(
						uploadInitCode(contract),
						contractCreate(contract).via(creationTxn)
				).when(
						captureChildCreate2MetaFor(
								2, 0,
								"setup", creationTxn, childMirror, childEip1014)
				).then(
						contractCall(contract, "makeNormalCall").via(callTxn),
						sourcing(() -> getTxnRecord(callTxn).logged().hasPriority(recordWith().contractCallResult(
								resultWith().resultThruAbi(
										getABIFor(FUNCTION, "makeNormalCall", contract),
										isLiteralResult(new Object[] { unhex(childEip1014.get()) }))))),
						contractCall(contract, "makeStaticCall").via(staticcallTxn),
						sourcing(() -> getTxnRecord(staticcallTxn).logged().hasPriority(recordWith().contractCallResult(
								resultWith().resultThruAbi(
										getABIFor(FUNCTION, "makeStaticCall", contract),
										isLiteralResult(new Object[] { unhex(childEip1014.get()) }))))),
						contractCall(contract, "makeDelegateCall").via(delegatecallTxn),
						sourcing(
								() -> getTxnRecord(delegatecallTxn).logged().hasPriority(recordWith().contractCallResult(
										resultWith().resultThruAbi(
												getABIFor(FUNCTION, "makeDelegateCall", contract),
												isLiteralResult(new Object[] { unhex(childEip1014.get()) }))))),
						contractCall(contract, "makeCallCode").via(callcodeTxn),
						sourcing(() -> getTxnRecord(callcodeTxn).logged().hasPriority(recordWith().contractCallResult(
								resultWith().resultThruAbi(
										getABIFor(FUNCTION, "makeCallCode", contract),
										isLiteralResult(new Object[] { unhex(childEip1014.get()) })))))
				);
	}

	private HapiApiSpec updateWithBothMemoSettersWorks() {
		final var firstMemo = "First";
		final var secondMemo = "Second";
		final var thirdMemo = "Third";

		return defaultHapiSpec("UpdateWithBothMemoSettersWorks")
				.given(
						newKeyNamed(ADMIN_KEY),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
								.entityMemo(firstMemo)
				).when(
						contractUpdate(CONTRACT)
								.newMemo(secondMemo),
						contractUpdate(CONTRACT)
								.newMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						getContractInfo(CONTRACT).has(contractWith().memo(secondMemo))
				).then(
						contractUpdate(CONTRACT)
								.useDeprecatedMemoField()
								.newMemo(thirdMemo),
						getContractInfo(CONTRACT).has(contractWith().memo(thirdMemo))
				);
	}

	private HapiApiSpec updatingExpiryWorks() {
		final var newExpiry = Instant.now().getEpochSecond() + 5 * ONE_MONTH;
		return defaultHapiSpec("UpdatingExpiryWorks")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				)
				.when(
						contractUpdate(CONTRACT)
								.newExpirySecs(newExpiry)
				)
				.then(
						getContractInfo(CONTRACT).has(contractWith().expiry(newExpiry))
				);
	}

	private HapiApiSpec rejectsExpiryTooFarInTheFuture() {
		final var smallBuffer = 12_345L;
		final var excessiveExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() + smallBuffer;

		return defaultHapiSpec("RejectsExpiryTooFarInTheFuture")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when().then(
						contractUpdate(CONTRACT)
								.newExpirySecs(excessiveExpiry)
								.hasKnownStatus(INVALID_EXPIRATION_TIME)
				);
	}

	private HapiApiSpec updateAutoRenewWorks() {
		return defaultHapiSpec("UpdateAutoRenewWorks")
				.given(
						newKeyNamed(ADMIN_KEY),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS)
				)
				.when(
						contractUpdate(CONTRACT)
								.newAutoRenew(THREE_MONTHS_IN_SECONDS + ONE_DAY)
				)
				.then(
						getContractInfo(CONTRACT)
								.has(contractWith()
										.autoRenew(THREE_MONTHS_IN_SECONDS + ONE_DAY))
				);
	}

	private HapiApiSpec updateAutoRenewAccountWorks() {
		final var autoRenewAccount = "autoRenewAccount";
		final var newAutoRenewAccount = "newAutoRenewAccount";
		return defaultHapiSpec("UpdateAutoRenewAccountWorks")
				.given(
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(autoRenewAccount),
						cryptoCreate(newAutoRenewAccount),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
								.autoRenewAccountId(autoRenewAccount),
						getContractInfo(CONTRACT)
								.has(ContractInfoAsserts.contractWith().autoRenewAccountId(autoRenewAccount))
								.logged()
				)
				.when(
						contractUpdate(CONTRACT)
								.newAutoRenewAccount(newAutoRenewAccount)
								.signedBy(DEFAULT_PAYER, ADMIN_KEY)
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate(CONTRACT)
								.newAutoRenewAccount(newAutoRenewAccount)
								.signedBy(DEFAULT_PAYER, ADMIN_KEY, newAutoRenewAccount)
				)
				.then(
						getContractInfo(CONTRACT)
								.has(ContractInfoAsserts.contractWith().autoRenewAccountId(newAutoRenewAccount))
								.logged()
				);
	}

	private HapiApiSpec updateAdminKeyWorks() {
		return defaultHapiSpec("UpdateAdminKeyWorks")
				.given(
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(NEW_ADMIN_KEY),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
				).when(
						contractUpdate(CONTRACT)
								.newKey(NEW_ADMIN_KEY)
				).then(
						contractUpdate(CONTRACT)
								.newMemo("some new memo"),
						getContractInfo(CONTRACT)
								.has(contractWith()
										.adminKey(NEW_ADMIN_KEY)
										.memo("some new memo"))
				);
	}

	// https://github.com/hashgraph/hedera-services/issues/3037
	private HapiApiSpec immutableContractKeyFormIsStandard() {
		return defaultHapiSpec("ImmutableContractKeyFormIsStandard")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT).immutable()
				).when().then(
						getContractInfo(CONTRACT)
								.has(contractWith().immutableContractKey(CONTRACT))
				);
	}

	private HapiApiSpec canMakeContractImmutableWithEmptyKeyList() {
		return defaultHapiSpec("CanMakeContractImmutableWithEmptyKeyList")
				.given(
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(NEW_ADMIN_KEY),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.adminKey(ADMIN_KEY)
				).when(
						contractUpdate(CONTRACT)
								.improperlyEmptyingAdminKey()
								.hasKnownStatus(INVALID_ADMIN_KEY),
						contractUpdate(CONTRACT)
								.properlyEmptyingAdminKey()
				).then(
						contractUpdate(CONTRACT)
								.newKey(NEW_ADMIN_KEY)
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec givenAdminKeyMustBeValid() {
		final var contract = "BalanceLookup";
		return defaultHapiSpec("GivenAdminKeyMustBeValid")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
						getContractInfo(contract).logged()
				).then(
						contractUpdate(contract)
								.useDeprecatedAdminKey()
								.signedBy(GENESIS, contract)
								.hasKnownStatus(INVALID_ADMIN_KEY)
				);
	}

	HapiApiSpec fridayThe13thSpec() {
		final var contract = "SimpleStorage";
		final var suffix = "Clone";
		final var newExpiry = Instant.now().getEpochSecond() + DEFAULT_PROPS.defaultExpirationSecs() * 2;
		final var betterExpiry = Instant.now().getEpochSecond() + DEFAULT_PROPS.defaultExpirationSecs() * 3;
		final var INITIAL_MEMO = "This is a memo string with only Ascii characters";
		final var NEW_MEMO = "Turning and turning in the widening gyre, the falcon cannot hear the falconer...";
		final var BETTER_MEMO = "This was Mr. Bleaney's room...";
		final var initialKeyShape = KeyShape.SIMPLE;
		final var newKeyShape = listOf(3);

		return defaultHapiSpec("FridayThe13thSpec")
				.given(
						newKeyNamed("initialAdminKey").shape(initialKeyShape),
						newKeyNamed("newAdminKey").shape(newKeyShape),
						cryptoCreate("payer")
								.balance(10 * ONE_HUNDRED_HBARS),
						uploadInitCode(contract)
				).when(
						contractCreate(contract)
								.payingWith("payer")
								.omitAdminKey(),
						contractCustomCreate(contract, suffix)
								.payingWith("payer")
								.adminKey("initialAdminKey")
								.entityMemo(INITIAL_MEMO),
						getContractInfo(contract + suffix)
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(INITIAL_MEMO)
										.adminKey("initialAdminKey"))
				).then(
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.newKey("newAdminKey")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.newKey("newAdminKey")
								.signedBy("payer", "newAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.newKey("newAdminKey"),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.newExpirySecs(newExpiry)
								.newMemo(NEW_MEMO),
						getContractInfo(contract + suffix)
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.solidityAddress(contract + suffix)
										.memo(NEW_MEMO)
										.expiry(newExpiry)),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.newMemo(BETTER_MEMO),
						getContractInfo(contract + suffix)
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(BETTER_MEMO)
										.expiry(newExpiry)),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.newExpirySecs(betterExpiry),
						getContractInfo(contract + suffix)
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(BETTER_MEMO)
										.expiry(betterExpiry)),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.signedBy("payer")
								.newExpirySecs(newExpiry)
								.hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.signedBy("payer")
								.newMemo(NEW_MEMO)
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate(contract + suffix)
								.payingWith("payer")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate(contract)
								.payingWith("payer")
								.newMemo(BETTER_MEMO)
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT),
						contractDelete(contract)
								.payingWith("payer")
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT),
						contractUpdate(contract)
								.payingWith("payer")
								.newExpirySecs(betterExpiry),
						contractDelete(contract + suffix)
								.payingWith("payer")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractDelete(contract + suffix)
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractDelete(contract + suffix)
								.payingWith("payer")
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec updateDoesNotChangeBytecode() {
		final var simpleStorageContract = "SimpleStorage";
		final var emptyConstructorContract = "EmptyConstructor";
		return defaultHapiSpec("HSCS-DCPR-001")
				.given(
						uploadInitCode(simpleStorageContract, emptyConstructorContract),
						contractCreate(simpleStorageContract),
						getContractBytecode(simpleStorageContract).saveResultTo("initialBytecode")
				)
				.when(
						contractUpdate(simpleStorageContract).bytecode(emptyConstructorContract)
				)
				.then(
						withOpContext(
								(spec, log) -> {
									var op = getContractBytecode(simpleStorageContract).hasBytecode(
											spec.registry().getBytes("initialBytecode"));
									allRunFor(spec, op);
								}
						)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}