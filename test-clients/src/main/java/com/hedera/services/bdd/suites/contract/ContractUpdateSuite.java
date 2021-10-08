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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;

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
		return List.of(
				updateWithBothMemoSettersWorks(),
				updatingExpiryWorks(),
				rejectsExpiryTooFarInTheFuture(),
				updateAutoRenewWorks(),
				updateAdminKeyWorks(),
				canMakeContractImmutableWithEmptyKeyList(),
				givenAdminKeyMustBeValid()
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
				).when( ).then(
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
							.autoRenewSecs(ONE_MONTH)
				)
				.when(
						contractUpdate("contract")
							.newAutoRenew(ONE_MONTH + ONE_DAY)
				)
				.then(
						getContractInfo("contract")
							.has(contractWith()
								.autoRenew(ONE_MONTH + ONE_DAY))
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

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
