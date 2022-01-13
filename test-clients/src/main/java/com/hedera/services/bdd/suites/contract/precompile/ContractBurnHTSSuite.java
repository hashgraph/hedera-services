package com.hedera.services.bdd.suites.contract.precompile;

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
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_AFTER_NESTED_MINT_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_TOKEN_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_TOKEN_WITH_EVENT_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.TRANSFER_BURN_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractBurnHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractBurnHTSSuite.class);

	private static final String ALICE = "Alice";
	private static final String TOKEN = "Token";
	private static final String TOKEN_TREASURY = "TokenTreasury";

	public static void main(String... args) {
		new ContractBurnHTSSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return List.of(
				HSCS_PREC_020_rollback_burn_that_fails_after_a_precompile_transfer()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				HSCS_PREC_004_token_burn_of_fungible_token_units(),
				HSCS_PREC_005_token_burn_of_NFT(),
				HSCS_PREC_011_burn_after_nested_mint()
		);
	}

	private HapiApiSpec HSCS_PREC_004_token_burn_of_fungible_token_units() {
		final var theContract = "burn token";
		final var multiKey = "purpose";

		return defaultHapiSpec("HSCS_PREC_004_token_burn_of_fungible_token_units")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(multiKey)
								.adminKey(multiKey)
								.treasury(TOKEN_TREASURY),
						fileCreate("bytecode").payingWith(ALICE),
						updateLargeFile(ALICE, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
														asAddress(spec.registry().getTokenID(TOKEN)))
														.payingWith(ALICE)
														.bytecode("bytecode")
														.via("creationTx")
														.gas(100_000))),
						getTxnRecord("creationTx").logged()
				)
				.when(
						contractCall(theContract, BURN_TOKEN_WITH_EVENT_ABI, 1, new ArrayList<Long>())
								.payingWith(ALICE)
								.alsoSigningWithFullPrefix(multiKey)
								.gas(48_000)
								.via("burn"),
						getTxnRecord("burn").hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(parsedToByteString(49))))))),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49),

						childRecordsCheck("burn", SUCCESS, recordWith()
								.status(SUCCESS)
								.tokenTransfers(
										changingFungibleBalances()
												.including(TOKEN, TOKEN_TREASURY, -1)
								)
								.newTotalSupply(49)
						),

						newKeyNamed("contractKey")
								.shape(DELEGATE_CONTRACT.signedWith(theContract)),
						tokenUpdate(TOKEN)
								.supplyKey("contractKey"),
						contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
								.via("burn with contract key")
								.gas(48_000),

						childRecordsCheck("burn with contract key", SUCCESS, recordWith()
								.status(SUCCESS)
								.tokenTransfers(
										changingFungibleBalances()
												.including(TOKEN, TOKEN_TREASURY, -1)
								)
								.newTotalSupply(48)
						)

				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 48)

				);
	}

	private HapiApiSpec HSCS_PREC_005_token_burn_of_NFT() {
		final var theContract = "burn token";
		final var multiKey = "purpose";

		return defaultHapiSpec("HSCS_PREC_005_token_burn_of_NFT")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.supplyKey(multiKey)
								.treasury(TOKEN_TREASURY),
						mintToken(TOKEN, List.of(copyFromUtf8("First!"))),
						mintToken(TOKEN, List.of(copyFromUtf8("Second!"))),
						fileCreate("bytecode").payingWith(ALICE),
						updateLargeFile(ALICE, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
														asAddress(spec.registry().getTokenID(TOKEN)))
														.payingWith(ALICE)
														.bytecode("bytecode")
														.via("creationTx")
														.gas(100_000))),
						getTxnRecord("creationTx").logged()
				)
				.when(
						withOpContext(
								(spec, opLog) -> {
									var serialNumbers = new ArrayList<>();
									serialNumbers.add(1L);
									allRunFor(
											spec,
											contractCall(theContract, BURN_TOKEN_ABI, 0, serialNumbers)
													.payingWith(ALICE)
													.alsoSigningWithFullPrefix(multiKey)
													.gas(48_000)
													.via("burn"));
								}
						),

						childRecordsCheck("burn", SUCCESS, recordWith()
								.status(SUCCESS)
								.newTotalSupply(1)
						)

				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 1)

				);
	}

	private HapiApiSpec HSCS_PREC_011_burn_after_nested_mint() {
		final var innerContract = "BurnTokenContract";
		final var outerContract = "NestedBurnContract";
		final var multiKey = "purpose";
		final var revisedKey = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT, DELEGATE_CONTRACT);

		return defaultHapiSpec("HSCS_PREC_011_burn_after_nested_mint")
				.given(
						newKeyNamed(multiKey),
						cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(multiKey)
								.adminKey(multiKey)
								.treasury(TOKEN_TREASURY),
						fileCreate(innerContract).path(ContractResources.MINT_TOKEN_CONTRACT),
						fileCreate(outerContract).path(ContractResources.NESTED_BURN),
						contractCreate(innerContract)
								.bytecode(innerContract)
								.gas(100_000),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, ContractResources.NESTED_BURN_CONSTRUCTOR_ABI,
														getNestedContractAddress(innerContract, spec))
														.payingWith(ALICE)
														.bytecode(outerContract)
														.via("creationTx")
														.gas(100_000))),
						getTxnRecord("creationTx").logged()

				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed("contractKey").shape(revisedKey.signedWith(sigs(ON,
														innerContract, outerContract))),
												tokenUpdate(TOKEN)
														.supplyKey("contractKey"),
												contractCall(outerContract, BURN_AFTER_NESTED_MINT_ABI,
														1, asAddress(spec.registry().getTokenID(TOKEN)), new ArrayList<>())
														.payingWith(ALICE)
														.via("burnAfterNestedMint"))),

						childRecordsCheck("burnAfterNestedMint", SUCCESS, recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(TOKEN, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.tokenTransfers(
												changingFungibleBalances()
														.including(TOKEN, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						)

				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 50)
				);
	}

	private HapiApiSpec HSCS_PREC_020_rollback_burn_that_fails_after_a_precompile_transfer() {
		final var bob = "bob";
		final var feeCollector = "feeCollector";
		final var supplyKey = "supplyKey";
		final var tokenWithHbarFee = "tokenWithHbarFee";
		final var theContract = "theContract";

		return defaultHapiSpec("HSCS_PREC_020_rollback_burn_that_fails_after_a_precompile_transfer")
				.given(
						newKeyNamed(supplyKey),
						cryptoCreate(ALICE).balance(ONE_HBAR),
						cryptoCreate(bob).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(feeCollector).balance(0L),
						tokenCreate(tokenWithHbarFee)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(supplyKey)
								.initialSupply(0L)
								.treasury(TOKEN_TREASURY)
								.withCustom(fixedHbarFee(2 * ONE_HBAR, feeCollector)),
						mintToken(tokenWithHbarFee, List.of(copyFromUtf8("First!"))),
						mintToken(tokenWithHbarFee, List.of(copyFromUtf8("Second!"))),
						fileCreate("bytecode").payingWith(bob),
						updateLargeFile(bob, "bytecode",
								extractByteCode(ContractResources.TRANSFER_AND_BURN)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, ContractResources.TRANSFER_AND_BURN_CONSTRUCTOR_ABI,
														asAddress(spec.registry().getTokenID(tokenWithHbarFee)))
														.payingWith(bob)
														.bytecode("bytecode")
														.gas(100_000))),
						tokenAssociate(ALICE, tokenWithHbarFee),
						tokenAssociate(bob, tokenWithHbarFee),
						tokenAssociate(theContract, tokenWithHbarFee),
						cryptoTransfer(movingUnique(tokenWithHbarFee, 2L).between(TOKEN_TREASURY, ALICE))
								.payingWith(GENESIS),
						getAccountInfo(feeCollector).has(AccountInfoAsserts.accountWith().balance(0L))
				)
				.when(
						withOpContext(
								(spec, opLog) -> {
									var serialNumbers = new ArrayList<>();
									serialNumbers.add(1L);
									allRunFor(
											spec,
											contractCall(theContract, TRANSFER_BURN_ABI,
													asAddress(spec.registry().getAccountID(ALICE)),
													asAddress(spec.registry().getAccountID(bob)), 0,
													2L, serialNumbers)
													.payingWith(ALICE)
													.alsoSigningWithFullPrefix(ALICE)
													.alsoSigningWithFullPrefix(supplyKey)
													.gas(70_000)
													.via("contractCallTxn")
													.hasKnownStatus(CONTRACT_REVERT_EXECUTED));
								}),

						childRecordsCheck("contractCallTxn", CONTRACT_REVERT_EXECUTED, recordWith()
										.status(REVERTED_SUCCESS),
								recordWith()
										.status(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)
						)
				)
				.then(
						getAccountBalance(bob).hasTokenBalance(tokenWithHbarFee, 0),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(tokenWithHbarFee, 1),
						getAccountBalance(ALICE).hasTokenBalance(tokenWithHbarFee, 1)
				);
	}


	@NotNull
	private String getNestedContractAddress(String outerContract, HapiApiSpec spec) {
		return CommonUtils.calculateSolidityAddress(
				(int) spec.registry().getContractId(outerContract).getShardNum(),
				spec.registry().getContractId(outerContract).getRealmNum(),
				spec.registry().getContractId(outerContract).getContractNum());
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
