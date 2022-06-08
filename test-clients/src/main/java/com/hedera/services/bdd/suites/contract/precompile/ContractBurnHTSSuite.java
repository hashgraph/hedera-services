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
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class ContractBurnHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractBurnHTSSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final String THE_CONTRACT = "BurnToken";

	private static final String ALICE = "Alice";
	private static final String TOKEN = "Token";
	private static final String TOKEN_TREASURY = "TokenTreasury";
	private static final String MULTI_KEY = "purpose";
	private static final String CONTRACT_KEY = "Contract key";
	private static final String SUPPLY_KEY = "Supply key";

	public static void main(String... args) {
		new ContractBurnHTSSuite().runSuiteSync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
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
		return defaultHapiSpec("HSCS_PREC_004_token_burn_of_fungible_token_units")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(THE_CONTRACT),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT, asAddress(spec.registry().getTokenID(TOKEN)))
														.payingWith(ALICE)
														.via("creationTx")
														.gas(GAS_TO_OFFER)
										)
						),
						getTxnRecord("creationTx").logged()
				)
				.when(
						contractCall(THE_CONTRACT, "burnTokenWithEvent", 1, new ArrayList<Long>()
						)
								.payingWith(ALICE)
								.alsoSigningWithFullPrefix(MULTI_KEY)
								.gas(GAS_TO_OFFER)
								.via("burn"),
						getTxnRecord("burn").hasPriority(
								recordWith().contractCallResult(
										resultWith().logs(inOrder(logWith().noData().withTopicsInOrder(
												List.of(parsedToByteString(49))))))),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 49),
						childRecordsCheck("burn", SUCCESS, recordWith()
								.status(SUCCESS)
								.contractCallResult(
										resultWith()
												.contractCallResult(htsPrecompileResult()
														.forFunction(HTSPrecompileResult.FunctionType.BURN)
														.withStatus(SUCCESS)
														.withTotalSupply(49)
												)
								)
								.newTotalSupply(49)
								.tokenTransfers(
										changingFungibleBalances()
												.including(TOKEN, TOKEN_TREASURY, -1)
								)
								.newTotalSupply(49)
						),
						newKeyNamed(CONTRACT_KEY)
								.shape(DELEGATE_CONTRACT.signedWith(THE_CONTRACT)),
						tokenUpdate(TOKEN)
								.supplyKey(CONTRACT_KEY),
						contractCall(THE_CONTRACT, "burnToken", 1, new ArrayList<Long>()
						)
								.via("burn with contract key")
								.gas(GAS_TO_OFFER),

						childRecordsCheck("burn with contract key", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BURN)
																.withStatus(SUCCESS)
																.withTotalSupply(48)
														)
										)
										.newTotalSupply(48)
										.tokenTransfers(
												changingFungibleBalances()
														.including(TOKEN, TOKEN_TREASURY, -1)
										)
						)
				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 48)
				);
	}

	private HapiApiSpec HSCS_PREC_005_token_burn_of_NFT() {
		return defaultHapiSpec("HSCS_PREC_005_token_burn_of_NFT")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.supplyKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						mintToken(TOKEN, List.of(copyFromUtf8("First!"))),
						mintToken(TOKEN, List.of(copyFromUtf8("Second!"))),
						uploadInitCode(THE_CONTRACT),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT, asAddress(spec.registry().getTokenID(TOKEN)))
														.payingWith(ALICE)
														.via("creationTx")
														.gas(GAS_TO_OFFER)
										)
						)
						,
						getTxnRecord("creationTx").logged()
				)
				.when(
						withOpContext(
								(spec, opLog) -> {
									final var serialNumbers = new ArrayList<>();
									serialNumbers.add(1L);
									allRunFor(
											spec,
											contractCall(THE_CONTRACT, "burnToken", 0, serialNumbers
											)
													.payingWith(ALICE)
													.alsoSigningWithFullPrefix(MULTI_KEY)
													.gas(GAS_TO_OFFER)
													.via("burn")
									);
								}
						),

						childRecordsCheck("burn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BURN)
																.withStatus(SUCCESS)
																.withTotalSupply(1)
														)
										)
										.newTotalSupply(1)
						)
				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 1)
				);
	}

	private HapiApiSpec HSCS_PREC_011_burn_after_nested_mint() {
		final var innerContract = "MintToken";
		final var outerContract = "NestedBurn";
		final var revisedKey = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT, DELEGATE_CONTRACT);

		return defaultHapiSpec("HSCS_PREC_011_burn_after_nested_mint")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ALICE).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(innerContract, outerContract),
						contractCreate(innerContract)
								.gas(GAS_TO_OFFER),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(innerContract, spec)
												)
														.payingWith(ALICE)
														.via("creationTx")
														.gas(GAS_TO_OFFER)
										)
						),
						getTxnRecord("creationTx").logged()
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(CONTRACT_KEY).shape(revisedKey.signedWith(sigs(ON,
														innerContract, outerContract))),
												tokenUpdate(TOKEN)
														.supplyKey(CONTRACT_KEY),
												contractCall(outerContract, "burnAfterNestedMint",
														1, asAddress(spec.registry().getTokenID(TOKEN)), new ArrayList<>()
												)
														.payingWith(ALICE)
														.via("burnAfterNestedMint")
										)
						),
						childRecordsCheck("burnAfterNestedMint", SUCCESS, recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.MINT)
																.withStatus(SUCCESS)
																.withTotalSupply(51)
																.withSerialNumbers()
														)
										)
										.tokenTransfers(
												changingFungibleBalances()
														.including(TOKEN, TOKEN_TREASURY, 1)
										)
										.newTotalSupply(51),
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BURN)
																.withStatus(SUCCESS)
																.withTotalSupply(50)
														)
										)
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
		final var tokenWithHbarFee = "tokenWithHbarFee";
		final var theContract = "TransferAndBurn";

		return defaultHapiSpec("HSCS_PREC_020_rollback_burn_that_fails_after_a_precompile_transfer")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(bob).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(feeCollector).balance(0L),
						tokenCreate(tokenWithHbarFee)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0L)
								.treasury(TOKEN_TREASURY)
								.withCustom(fixedHbarFee(300 * ONE_HBAR, feeCollector)),
						mintToken(tokenWithHbarFee, List.of(copyFromUtf8("First!"))),
						mintToken(tokenWithHbarFee, List.of(copyFromUtf8("Second!"))),
						uploadInitCode(theContract),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, asAddress(spec.registry().getTokenID(tokenWithHbarFee))
												)
														.payingWith(bob)
														.gas(GAS_TO_OFFER)
										)
						),
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
									final var serialNumbers = new ArrayList<>();
									serialNumbers.add(1L);
									allRunFor(
											spec,
											contractCall(theContract, "transferBurn",
													asAddress(spec.registry().getAccountID(ALICE)),
													asAddress(spec.registry().getAccountID(bob)), 0,
													2L, serialNumbers
											)
													.alsoSigningWithFullPrefix(ALICE, SUPPLY_KEY)
													.gas(GAS_TO_OFFER)
													.via("contractCallTxn")
													.hasKnownStatus(CONTRACT_REVERT_EXECUTED));
								}),

						childRecordsCheck("contractCallTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(REVERTED_SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BURN)
																.withStatus(SUCCESS)
																.withTotalSupply(1)
														)
										),
								recordWith()
										.status(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)
														)
										)
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
		return HapiPropertySource.asHexedSolidityAddress(spec.registry().getContractId(outerContract));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
