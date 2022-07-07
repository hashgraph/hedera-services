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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class ContractDeleteSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractDeleteSuite.class);
	private static final String CONTRACT = "Multipurpose";
	private static final String PAYABLE_CONSTRUCTOR = "PayableConstructor";

	public static void main(String... args) {
		new ContractDeleteSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						rejectsWithoutProperSig(),
						systemCannotDeleteOrUndeleteContracts(),
						deleteWorksWithMutableContract(),
						deleteFailsWithImmutableContract(),
						deleteTransfersToAccount(),
						deleteTransfersToContract(),
						cannotDeleteOrSelfDestructTokenTreasury(),
						cannotDeleteOrSelfDestructContractWithNonZeroBalance()
				}
		);
	}

	HapiApiSpec cannotDeleteOrSelfDestructTokenTreasury() {
		final var someToken = "someToken";
		final var selfDestructCallable = "SelfDestructCallable";
		final var multiKey = "multi";
		final var escapeRoute = "civilian";

		return defaultHapiSpec("CannotDeleteOrSelfDestructTokenTreasury")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(escapeRoute),

						uploadInitCode(selfDestructCallable),
						contractCustomCreate(selfDestructCallable, "1")
								.adminKey(multiKey)
								.balance(123),
						contractCustomCreate(selfDestructCallable, "2")
								.adminKey(multiKey)
								.balance(321),
						tokenCreate(someToken)
								.adminKey(multiKey)
								.treasury(selfDestructCallable + "1")
				).when(
						contractDelete(selfDestructCallable + "1")
								.hasKnownStatus(ACCOUNT_IS_TREASURY),
						tokenAssociate(selfDestructCallable + "2", someToken),
						tokenUpdate(someToken).treasury(selfDestructCallable + "2"),
						contractDelete(selfDestructCallable + "1"),
						contractCall(selfDestructCallable + "2", "destroy")
								.hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION),
						tokenAssociate(escapeRoute, someToken),
						tokenUpdate(someToken).treasury(escapeRoute)
				).then(
						contractCall(selfDestructCallable + "2", "destroy")
				);
	}

	HapiApiSpec cannotDeleteOrSelfDestructContractWithNonZeroBalance() {
		final var someToken = "someToken";
		final var multiKey = "multi";
		final var selfDestructableContract = "SelfDestructCallable";
		final var otherMiscContract = "PayReceivable";

		return defaultHapiSpec("CannotDeleteOrSelfDestructContractWithNonZeroBalance")
				.given(
						newKeyNamed(multiKey),
						uploadInitCode(selfDestructableContract),
						contractCreate(selfDestructableContract)
								.adminKey(multiKey)
								.balance(123),
						uploadInitCode(otherMiscContract),
						contractCreate(otherMiscContract),
						tokenCreate(someToken)
								.initialSupply(0L)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.treasury(selfDestructableContract)
								.supplyType(TokenSupplyType.INFINITE)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
				).when(
						mintToken(someToken, List.of(ByteString.copyFromUtf8("somemetadata"))),
						tokenAssociate(otherMiscContract, someToken),
						cryptoTransfer(TokenMovement.movingUnique(someToken, 1)
								.between(selfDestructableContract, otherMiscContract))
				).then(
						contractDelete(otherMiscContract)
								.hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
						contractCall(selfDestructableContract, "destroy")
								.hasKnownStatus(CONTRACT_EXECUTION_EXCEPTION)
				);

	}

	HapiApiSpec rejectsWithoutProperSig() {
		return defaultHapiSpec("ScDelete")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when().then(
						contractDelete(CONTRACT)
								.signedBy(GENESIS)
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec systemCannotDeleteOrUndeleteContracts() {
		return defaultHapiSpec("SystemCannotDeleteOrUndeleteContracts")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when().then(
						systemContractDelete(CONTRACT)
								.payingWith(SYSTEM_DELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED),
						systemContractUndelete(CONTRACT)
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED),
						getContractInfo(CONTRACT).hasAnswerOnlyPrecheck(OK)
				);
	}

	private HapiApiSpec deleteWorksWithMutableContract() {
		return defaultHapiSpec("DeleteWorksWithMutableContract")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when().then(
						contractDelete(CONTRACT),
						getContractInfo(CONTRACT)
								.has(contractWith().isDeleted())
				);
	}

	private HapiApiSpec deleteFailsWithImmutableContract() {
		return defaultHapiSpec("DeleteFailsWithImmutableContract")
				.given(
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT).omitAdminKey()
				).when().then(
						contractDelete(CONTRACT).hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec deleteTransfersToAccount() {
		return defaultHapiSpec("DeleteTransfersToAccount")
				.given(
						cryptoCreate("receiver").balance(0L),
						uploadInitCode(PAYABLE_CONSTRUCTOR),
						contractCreate(PAYABLE_CONSTRUCTOR).balance(1L)
				).when(
						contractDelete(PAYABLE_CONSTRUCTOR).transferAccount("receiver")
				).then(
						getAccountBalance("receiver").hasTinyBars(1L)
				);
	}

	private HapiApiSpec deleteTransfersToContract() {
		final var suffix = "Receiver";

		return defaultHapiSpec("DeleteTransfersToContract")
				.given(
						uploadInitCode(PAYABLE_CONSTRUCTOR),
						contractCreate(PAYABLE_CONSTRUCTOR).balance(0L),
						contractCustomCreate(PAYABLE_CONSTRUCTOR, suffix).balance(1L)
				).when(
						contractDelete(PAYABLE_CONSTRUCTOR).transferContract(PAYABLE_CONSTRUCTOR + suffix)
				).then(
						getAccountBalance(PAYABLE_CONSTRUCTOR + suffix).hasTinyBars(1L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
