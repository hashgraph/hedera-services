package com.hedera.services.bdd.suites.contract.precompile;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_FUNGIBLE_WITH_FEES_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_NON_FUNGIBLE_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_NON_FUNGIBLE_WITH_FEES_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TOKEN_CREATE_CONTRACT;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;

public class CreatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreatePrecompileSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;

	public static void main(String... args) {
		new CreatePrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
	//TODO: implement the E2E scenarios from the test plan
		return allOf(
				positiveSpecs()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				fungibleTokenCreate()
//				fungibleWithFeesTokenCreate()
//				nonFungibleTokenCreate(),
//				nonFungibleWithFeesTokenCreate()
		);
	}

	private HapiApiSpec fungibleTokenCreate() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var firstCreateTxn = "firstCreateTxn";

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<String> contractID = new AtomicReference<>();
		final String ACCOUNT = "account";
		return defaultHapiSpec("HelloWorldFungibleMint")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
								.exposingNumTo(num -> contractID.set(asHexedSolidityAddress(0, 0, num)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_FUNGIBLE_ABI,
														asAddress(spec.registry().getAccountID(ACCOUNT)))
														.via(firstCreateTxn)
														.gas(GAS_TO_OFFER)
														.sending(30 * ONE_HBAR)
														.payingWith(ACCOUNT)
										)
						)
				).then(
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged(),
						getAccountBalance(ACCOUNT).logged(),
						getAccountBalance(tokenCreateContract).logged()
				);
	}

	private HapiApiSpec fungibleWithFeesTokenCreate() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var firstCreateTxn = "firstCreateTxn";

		final String ACCOUNT = "account";
		return defaultHapiSpec("HelloWorldFungibleMint")
				.given(
						cryptoCreate(ACCOUNT),
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_FUNGIBLE_WITH_FEES_ABI,
														asAddress(spec.registry().getAccountID(ACCOUNT)))
														.via(firstCreateTxn)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec nonFungibleTokenCreate() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var firstCreateTxn = "firstCreateTxn";

		return defaultHapiSpec("HelloWorldFungibleMint")
				.given(
						cryptoCreate("treasury"),
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_NON_FUNGIBLE_ABI)
														.via(firstCreateTxn)
										)
						)
				).then(
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec nonFungibleWithFeesTokenCreate() {
		final var contractName = "contractName";
		final var tokenCreateContract = "tokenCreateContract";
		final var firstCreateTxn = "firstCreateTxn";

		return defaultHapiSpec("HelloWorldFungibleMint")
				.given(
						cryptoCreate("treasury"),
						fileCreate(contractName),
						updateLargeFile(GENESIS, contractName, extractByteCode(TOKEN_CREATE_CONTRACT)),
						contractCreate(tokenCreateContract)
								.bytecode(contractName)
								.gas(GAS_TO_OFFER)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(tokenCreateContract, CREATE_NON_FUNGIBLE_WITH_FEES_ABI,
														asAddress(spec.registry().getAccountID("treasury")))
														.via(firstCreateTxn)
										)
						)
				).then(
						getTxnRecord(firstCreateTxn).andAllChildRecords().logged()
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
