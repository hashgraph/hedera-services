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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractUpdateSuite.class);

	private static final long defaultMaxLifetime =
			Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));
	private static final long ONE_DAY = 60 * 60 * 24;
	private static final long ONE_MONTH = 30 * ONE_DAY;

	public static void main(String... args) {
		new ContractUpdateSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						updateWithBothMemoSettersWorks(),
						updatingExpiryWorks(),
						rejectsExpiryTooFarInTheFuture(),
						updateAutoRenewWorks(),
						updateAdminKeyWorks(),
						canMakeContractImmutableWithEmptyKeyList(),
						givenAdminKeyMustBeValid(),
						fridayThe13thSpec(),
						updateDoesNotChangeBytecode()
				}
		);
	}

	private HapiApiSpec updateWithBothMemoSettersWorks() {
		String firstMemo = "First";
		String secondMemo = "Second";
		String thirdMemo = "Third";
		return defaultHapiSpec("UpdateWithBothMemoSettersWorks")
				.given(
						newKeyNamed("adminKey"),
						contractCreate("contract")
								.adminKey("adminKey")
								.entityMemo(firstMemo)
				).when(
						contractUpdate("contract")
								.newMemo(secondMemo),
						contractUpdate("contract")
								.newMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						getContractInfo("contract").has(contractWith().memo(secondMemo))
				).then(
						contractUpdate("contract")
								.useDeprecatedMemoField()
								.newMemo(thirdMemo),
						getContractInfo("contract").has(contractWith().memo(thirdMemo))
				);
	}

	private HapiApiSpec updatingExpiryWorks() {
		final var newExpiry = Instant.now().getEpochSecond() + 5 * ONE_MONTH;
		return defaultHapiSpec("UpdatingExpiryWorks")
				.given(
						contractCreate("contract")
				)
				.when(
						contractUpdate("contract")
								.newExpirySecs(newExpiry)
				)
				.then(
						getContractInfo("contract").has(contractWith().expiry(newExpiry))
				);
	}

	private HapiApiSpec rejectsExpiryTooFarInTheFuture() {
		final var smallBuffer = 12_345L;
		final var excessiveExpiry = defaultMaxLifetime + Instant.now().getEpochSecond() + smallBuffer;

		return defaultHapiSpec("RejectsExpiryTooFarInTheFuture")
				.given(
						contractCreate("target")
				).when().then(
						contractUpdate("target")
								.newExpirySecs(excessiveExpiry)
								.hasKnownStatus(INVALID_EXPIRATION_TIME)
				);
	}

	private HapiApiSpec updateAutoRenewWorks() {
		return defaultHapiSpec("UpdateAutoRenewWorks")
				.given(
						newKeyNamed("admin"),
						contractCreate("contract")
								.adminKey("admin")
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS)
				)
				.when(
						contractUpdate("contract")
								.newAutoRenew(THREE_MONTHS_IN_SECONDS + ONE_DAY)
				)
				.then(
						getContractInfo("contract")
								.has(contractWith()
										.autoRenew(THREE_MONTHS_IN_SECONDS + ONE_DAY))
				);
	}

	private HapiApiSpec updateAdminKeyWorks() {
		return defaultHapiSpec("UpdateAdminKeyWorks")
				.given(
						newKeyNamed("oldAdminKey"),
						newKeyNamed("newAdminKey"),
						contractCreate("contract")
								.adminKey("oldAdminKey")
				).when(
						contractUpdate("contract")
								.newKey("newAdminKey")
				).then(
						contractUpdate("contract")
								.newMemo("some new memo"),
						getContractInfo("contract")
								.has(contractWith()
										.adminKey("newAdminKey")
										.memo("some new memo"))
				);
	}

	private HapiApiSpec canMakeContractImmutableWithEmptyKeyList() {
		return defaultHapiSpec("CanMakeContractImmutableWithEmptyKeyList")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("newAdminKey"),
						contractCreate("toBeImmutable")
								.adminKey("adminKey")
				).when(
						contractUpdate("toBeImmutable")
								.improperlyEmptyingAdminKey()
								.hasKnownStatus(INVALID_ADMIN_KEY),
						contractUpdate("toBeImmutable")
								.properlyEmptyingAdminKey()
				).then(
						contractUpdate("toBeImmutable")
								.newKey("newAdminKey")
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec givenAdminKeyMustBeValid() {
		return defaultHapiSpec("GivenAdminKeyMustBeValid")
				.given(
						fileCreate("bytecode").path(ContractResources.BALANCE_LOOKUP_BYTECODE_PATH),
						contractCreate("target").bytecode("bytecode")
				).when(
						getContractInfo("target").logged()
				).then(
						contractUpdate("target")
								.useDeprecatedAdminKey()
								.signedBy(GENESIS, "target")
								.hasKnownStatus(INVALID_ADMIN_KEY)
				);
	}

	HapiApiSpec fridayThe13thSpec() {
		long newExpiry = Instant.now().getEpochSecond() + DEFAULT_PROPS.defaultExpirationSecs() * 2;
		long betterExpiry = Instant.now().getEpochSecond() + DEFAULT_PROPS.defaultExpirationSecs() * 3;
		final String INITIAL_MEMO = "This is a memo string with only Ascii characters";
		final String NEW_MEMO = "Turning and turning in the widening gyre, the falcon cannot hear the falconer...";
		final String BETTER_MEMO = "This was Mr. Bleaney's room...";
		KeyShape initialKeyShape = KeyShape.SIMPLE;
		KeyShape newKeyShape = listOf(3);

		return defaultHapiSpec("FridayThe13thSpec")
				.given(
						newKeyNamed("initialAdminKey").shape(initialKeyShape),
						newKeyNamed("newAdminKey").shape(newKeyShape),
						cryptoCreate("payer")
								.balance(10 * ONE_HUNDRED_HBARS),
						fileCreate("bytecode")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH)
								.payingWith("payer")
				).when(
						contractCreate("immutableContract")
								.payingWith("payer")
								.omitAdminKey()
								.bytecode("bytecode"),
						contractCreate("contract")
								.payingWith("payer")
								.adminKey("initialAdminKey")
								.entityMemo(INITIAL_MEMO)
								.bytecode("bytecode"),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(INITIAL_MEMO)
										.adminKey("initialAdminKey"))
				).then(
						contractUpdate("contract")
								.payingWith("payer")
								.newKey("newAdminKey")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("contract")
								.payingWith("payer")
								.newKey("newAdminKey")
								.signedBy("payer", "newAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("contract")
								.payingWith("payer")
								.newKey("newAdminKey"),
						contractUpdate("contract")
								.payingWith("payer")
								.newExpirySecs(newExpiry)
								.newMemo(NEW_MEMO),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.solidityAddress("contract")
										.memo(NEW_MEMO)
										.expiry(newExpiry)),
						contractUpdate("contract")
								.payingWith("payer")
								.newMemo(BETTER_MEMO),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(BETTER_MEMO)
										.expiry(newExpiry)),
						contractUpdate("contract")
								.payingWith("payer")
								.newExpirySecs(betterExpiry),
						getContractInfo("contract")
								.payingWith("payer")
								.logged()
								.has(contractWith()
										.memo(BETTER_MEMO)
										.expiry(betterExpiry)),
						contractUpdate("contract")
								.payingWith("payer")
								.signedBy("payer")
								.newExpirySecs(newExpiry)
								.hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED),
						contractUpdate("contract")
								.payingWith("payer")
								.signedBy("payer")
								.newMemo(NEW_MEMO)
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("contract")
								.payingWith("payer")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractUpdate("immutableContract")
								.payingWith("payer")
								.newMemo(BETTER_MEMO)
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT),
						contractDelete("immutableContract")
								.payingWith("payer")
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT),
						contractUpdate("immutableContract")
								.payingWith("payer")
								.newExpirySecs(betterExpiry),
						contractDelete("contract")
								.payingWith("payer")
								.signedBy("payer", "initialAdminKey")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractDelete("contract")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_SIGNATURE),
						contractDelete("contract")
								.payingWith("payer")
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec updateDoesNotChangeBytecode() {
		return defaultHapiSpec("HSCS-DCPR-001")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.EMPTY_CONSTRUCTOR).via("fileCreate"),
						fileCreate("bytecode2")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH),
						contractCreate("contract")
								.bytecode("contractFile"),
						getContractBytecode("contract").saveResultTo("initialBytecode")
				)
				.when(
						contractUpdate("contract")
								.bytecode("bytecode2")
				)
				.then(
						withOpContext((spec, log) -> {
							var op = getContractBytecode("contract").hasBytecode(
									spec.registry().getBytes("initialBytecode"));
							allRunFor(spec, op);
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}