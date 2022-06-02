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
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class ContractKeysHTSSuite extends HapiApiSuite {
    private static final long GAS_TO_OFFER = 1_500_000L;

	private static final Logger log = LogManager.getLogger(ContractKeysHTSSuite.class);
	private static final String TOKEN_TREASURY = "treasury";
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String NFT = "nft";

	private static final String ACCOUNT = "sender";
	private static final String RECEIVER = "receiver";

	private static final KeyShape CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT);
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);

	private static final String UNIVERSAL_KEY = "Multipurpose";
	private static final String DELEGATE_KEY = "Delegate Contract Key";
	private static final String CONTRACT_KEY = "Contract Key";
	private static final String FROZEN_TOKEN = "Frozen Token";
	private static final String KYC_TOKEN = "KYC Token";
	private static final String FREEZE_KEY = "Freeze Key";
	private static final String KYC_KEY = "KYC Key";
	private static final String MULTI_KEY = "Multi Key";
	private static final String SUPPLY_KEY = "Supply Key";


	private static final String ORDINARY_CALLS_CONTRACT = "HTSCalls";
	private static final String ASSOCIATE_DISSOCIATE_CONTRACT = "AssociateDissociate";
	private static final String BURN_TOKEN = "BurnToken";


	public static void main(String... args) {
		new ContractKeysHTSSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

    @Override
	public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
				HSCS_KEY_1(),
				HSCS_KEY_2(),
				HSCS_KEY_3(),
				HSCS_KEY_4(),
				HSCS_KEY_5(),
				HSCS_KEY_6(),
				HSCS_KEY_7(),
				HSCS_KEY_8(),
				HSCS_KEY_10()
		);
	}

	List<HapiApiSpec> HSCS_KEY_1() {
		return List.of(
				callForMintWithContractKey(),
				callForTransferWithContractKey(),
				callForAssociateWithContractKey(),
				callForDissociateWithContractKey(),
				callForBurnWithContractKey(),
				delegateCallForAssociatePrecompileSignedWithContractKeyFails(),
				delegateCallForDissociatePrecompileSignedWithContractKeyFails()
		);
	}

	List<HapiApiSpec> HSCS_KEY_2() {
		return List.of(
				staticCallForTransferWithContractKey(),
				staticCallForBurnWithContractKey(),
				staticCallForMintWithContractKey(),
				delegateCallForTransferWithContractKey(),
				delegateCallForBurnWithContractKey(),
				delegateCallForMintWithContractKey(),
				staticCallForDissociatePrecompileFails()
		);
	}

	List<HapiApiSpec> HSCS_KEY_3() {
		return List.of(
				callForMintWithDelegateContractKey(),
				callForTransferWithDelegateContractKey(),
				callForAssociateWithDelegateContractKey(),
				callForDissociateWithDelegateContractKey(),
				callForBurnWithDelegateContractKey(),
				delegateCallForAssociatePrecompileSignedWithDelegateContractKeyWorks(),
				delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks()
		);
	}

	List<HapiApiSpec> HSCS_KEY_4() {
		return List.of(
				associatePrecompileWithDelegateContractKeyForFungibleVanilla(),
				associatePrecompileWithDelegateContractKeyForFungibleFrozen(),
				associatePrecompileWithDelegateContractKeyForFungibleWithKYC(),
				associatePrecompileWithDelegateContractKeyForNonFungibleVanilla(),
				associatePrecompileWithDelegateContractKeyForNonFungibleFrozen(),
				associatePrecompileWithDelegateContractKeyForNonFungibleWithKYC(),
				dissociatePrecompileWithDelegateContractKeyForFungibleVanilla(),
				dissociatePrecompileWithDelegateContractKeyForFungibleFrozen(),
				dissociatePrecompileWithDelegateContractKeyForFungibleWithKYC(),
				dissociatePrecompileWithDelegateContractKeyForNonFungibleVanilla(),
				dissociatePrecompileWithDelegateContractKeyForNonFungibleFrozen(),
				dissociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC()
		);
	}

	List<HapiApiSpec> HSCS_KEY_5() {
		return List.of(
				staticCallForTransferWithDelegateContractKey(),
				staticCallForBurnWithDelegateContractKey(),
				staticCallForMintWithDelegateContractKey(),
				staticCallForAssociatePrecompileFails()
		);
	}

	List<HapiApiSpec> HSCS_KEY_6() {
		return List.of(
				burnWithKeyAsPartOf1OfXThreshold()
		);
	}

	List<HapiApiSpec> HSCS_KEY_7() {
		return List.of(
				transferWithKeyAsPartOf2OfXThreshold()
		);
	}

	List<HapiApiSpec> HSCS_KEY_8() {
		return List.of(
				burnTokenWithFullPrefixAndPartialPrefixKeys()
		);
	}

	List<HapiApiSpec> HSCS_KEY_10() {
		return List.of(
				mixedFramesScenarios()
		);
	}

	private HapiApiSpec burnWithKeyAsPartOf1OfXThreshold() {
		final var token = "Token";
		final var delegateContractKeyShape = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
		final var contractKeyShape = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT);

		return defaultHapiSpec("burnWithKeyAsPartOf1OfXThreshold")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(BURN_TOKEN),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(BURN_TOKEN, asAddress(spec.registry().getTokenID(token))
												)
														.via("creationTx")
										)
						)
				)
				.when(
						newKeyNamed(DELEGATE_KEY).shape(delegateContractKeyShape.signedWith(sigs(ON, BURN_TOKEN))),
						tokenUpdate(token).supplyKey(DELEGATE_KEY),
						contractCall(BURN_TOKEN, "burnToken", 1, new ArrayList<Long>()
						)
								.via("burn with delegate contract key")
								.gas(GAS_TO_OFFER),
						childRecordsCheck("burn with delegate contract key", SUCCESS, recordWith()
								.status(SUCCESS)
								.contractCallResult(
										resultWith()
												.contractCallResult(htsPrecompileResult()
														.forFunction(HTSPrecompileResult.FunctionType.BURN)
														.withStatus(SUCCESS)
														.withTotalSupply(49)
												)
								)
								.tokenTransfers(
										changingFungibleBalances()
												.including(token, TOKEN_TREASURY, -1)
								)
								.newTotalSupply(49)
						),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(token, 49)
				)
				.then(
						newKeyNamed(CONTRACT_KEY).shape(contractKeyShape.signedWith(sigs(ON, BURN_TOKEN))),
						tokenUpdate(token)
								.supplyKey(CONTRACT_KEY),
						contractCall(BURN_TOKEN, "burnToken", 1, new ArrayList<Long>()
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
								.tokenTransfers(
										changingFungibleBalances()
												.including(token, TOKEN_TREASURY, -1)
								)
						)
				);
	}

	private HapiApiSpec transferWithKeyAsPartOf2OfXThreshold() {
		final var outerContract = "DelegateContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> receiverID = new AtomicReference<>();
		final var delegateContractKeyShape = KeyShape.threshOf(2, SIMPLE, SIMPLE, DELEGATE_CONTRACT, KeyShape.CONTRACT);

		return defaultHapiSpec("transferWithKeyAsPartOf2OfXThreshold")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(RECEIVER)
								.exposingCreatedIdTo(receiverID::set),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract),
						tokenAssociate(nestedContract, VANILLA_TOKEN),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(RECEIVER, VANILLA_TOKEN),
						cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
								.payingWith(GENESIS)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												tokenAssociate(outerContract, VANILLA_TOKEN),
												newKeyNamed(DELEGATE_KEY).shape(delegateContractKeyShape.signedWith(sigs(ON, ON, outerContract, nestedContract))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(outerContract, "transferDelegateCall",
														asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
														asAddress(receiverID.get()), 1L
												)
														.payingWith(GENESIS)
														.alsoSigningWithFullPrefix(ACCOUNT)
														.via("delegateTransferCallWithDelegateContractKeyTxn")
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck("delegateTransferCallWithDelegateContractKeyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 0),
						getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 1)
				);
	}


	private HapiApiSpec delegateCallForTransferWithContractKey() {
		final var outerContract = "DelegateContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> receiverID = new AtomicReference<>();

		return defaultHapiSpec("delegateCallForTransferWithContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(RECEIVER)
								.exposingCreatedIdTo(receiverID::set),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract),
						tokenAssociate(nestedContract, VANILLA_TOKEN),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(RECEIVER, VANILLA_TOKEN),
						cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
								.payingWith(GENESIS)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												tokenAssociate(outerContract, VANILLA_TOKEN),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												contractCall(outerContract, "transferDelegateCall",
														asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
														asAddress(receiverID.get()), 1L)
														.payingWith(GENESIS)
														.via("delegateTransferCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck("delegateTransferCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1),
						getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 0)
				);
	}

	private HapiApiSpec delegateCallForBurnWithContractKey() {
		final var outerContract = "DelegateContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("delegateCallForBurnWithContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0L)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!"))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),
												contractCall(outerContract, "burnDelegateCall",
														asAddress(vanillaTokenTokenID.get()), 0, List.of(1L))
														.payingWith(GENESIS)
														.via("delegateBurnCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				)
				.then(
						childRecordsCheck("delegateBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BURN)
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 2)
				);
	}

	private HapiApiSpec delegateCallForMintWithContractKey() {
		final var outerContract = "DelegateContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("delegateCallForMintWithContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyKey(SUPPLY_KEY)
								.adminKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(50L)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),
												contractCall(outerContract, "mintDelegateCall",
														asAddress(vanillaTokenTokenID.get()), 1)
														.payingWith(GENESIS)
														.via("delegateBurnCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				)
				.then(
						childRecordsCheck("delegateBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.MINT)
																.withStatus(INVALID_SIGNATURE)
																.withSerialNumbers()
														)
										)

						),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 50)
				);
	}

	private HapiApiSpec staticCallForDissociatePrecompileFails() {
		final var outerContract = "NestedAssociateDissociate";
		final var nestedContract = "AssociateDissociate";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("StaticCallForDissociatePrecompileFails")
				.given(
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												contractCall(outerContract, "dissociateStaticCall",
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("staticDissociateCallTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						emptyChildRecordsCheck("staticDissociateCallTxn", CONTRACT_REVERT_EXECUTED),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec staticCallForTransferWithContractKey() {
		final var outerContract = "StaticContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> receiverID = new AtomicReference<>();

		return defaultHapiSpec("staticCallForTransferWithContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(RECEIVER)
								.exposingCreatedIdTo(receiverID::set),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract),
						tokenAssociate(nestedContract, VANILLA_TOKEN),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(RECEIVER, VANILLA_TOKEN),
						cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
								.payingWith(GENESIS)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												tokenAssociate(outerContract, VANILLA_TOKEN),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												contractCall(outerContract, "transferStaticCall",
														asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
														asAddress(receiverID.get()), 1L)
														.payingWith(GENESIS)
														.via("staticTransferCallWithContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)

				).then(
						emptyChildRecordsCheck("staticTransferCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec staticCallForBurnWithContractKey() {
		final var outerContract = "StaticContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("staticCallForBurnWithContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0L)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!"))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),
												contractCall(outerContract, "burnStaticCall",
														asAddress(vanillaTokenTokenID.get()), 0, List.of(1L)
												)
														.payingWith(GENESIS)
														.via("staticBurnCallWithContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				)
				.then(
						emptyChildRecordsCheck("staticBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec staticCallForMintWithContractKey() {
		final var outerContract = "StaticContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("staticCallForMintWithContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyKey(SUPPLY_KEY)
								.adminKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(50L)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												tokenUpdate(VANILLA_TOKEN).supplyKey(CONTRACT_KEY),

												contractCall(outerContract, "mintStaticCall",
														asAddress(vanillaTokenTokenID.get()), 1)
														.payingWith(GENESIS)
														.via("staticBurnCallWithContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				)
				.then(
						emptyChildRecordsCheck("staticBurnCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec staticCallForTransferWithDelegateContractKey() {
		final var outerContract = "StaticContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> receiverID = new AtomicReference<>();

		return defaultHapiSpec("staticCallForTransferWithDelegateContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(RECEIVER)
								.exposingCreatedIdTo(receiverID::set),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract),
						tokenAssociate(nestedContract, VANILLA_TOKEN),
						tokenAssociate(ACCOUNT, VANILLA_TOKEN),
						tokenAssociate(RECEIVER, VANILLA_TOKEN),
						cryptoTransfer(movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT))
								.payingWith(GENESIS)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												tokenAssociate(outerContract, VANILLA_TOKEN),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(outerContract, "transferStaticCall",
														asAddress(vanillaTokenTokenID.get()), asAddress(accountID.get()),
														asAddress(receiverID.get()), 1L)
														.payingWith(GENESIS)
														.via("staticTransferCallWithDelegateContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)

				).then(
						emptyChildRecordsCheck("staticTransferCallWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec staticCallForBurnWithDelegateContractKey() {
		final var outerContract = "StaticContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("staticCallForBurnWithDelegateContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0L)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
						mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!"))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),
												contractCall(outerContract, "burnStaticCall",
														asAddress(vanillaTokenTokenID.get()), 0, List.of(1L)
												)
														.payingWith(GENESIS)
														.via("staticBurnCallWithDelegateContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				)
				.then(
						emptyChildRecordsCheck("staticBurnCallWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED)

				);
	}

	private HapiApiSpec staticCallForMintWithDelegateContractKey() {
		final var outerContract = "StaticContract";
		final var nestedContract = "ServiceContract";
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("staticCallForMintWithDelegateContractKey")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyKey(SUPPLY_KEY)
								.adminKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)
								.initialSupply(50L)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),
												contractCall(outerContract, "mintStaticCall",
														asAddress(vanillaTokenTokenID.get()), 1
												)
														.payingWith(GENESIS)
														.via("staticBurnCallWithDelegateContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				)
				.then(
						emptyChildRecordsCheck("staticBurnCallWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec staticCallForAssociatePrecompileFails() {
		final var outerContract = "NestedAssociateDissociate";
		final var nestedContract = "AssociateDissociate";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("StaticCallForAssociatePrecompileFails")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(ONE_MILLION_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												contractCall(outerContract, "associateStaticCall",
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get())
												)
														.payingWith(ACCOUNT)
														.via("staticAssociateCallTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						emptyChildRecordsCheck("staticAssociateCallTxn", CONTRACT_REVERT_EXECUTED),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec callForMintWithContractKey() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var firstMintTxn = "firstMintTxn";
		final var amount = 10L;

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("callForMintWithContractKey")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
						uploadInitCode(ORDINARY_CALLS_CONTRACT),
						contractCreate(ORDINARY_CALLS_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														ORDINARY_CALLS_CONTRACT))),
												tokenUpdate(fungibleToken).supplyKey(CONTRACT_KEY),
												contractCall(ORDINARY_CALLS_CONTRACT, "mintTokenCall",
														asAddress(spec.registry().getTokenID(fungibleToken)), amount,
														new byte[]{}
												)
														.via(firstMintTxn)
														.payingWith(theAccount)
										)
						)
				).then(
						childRecordsCheck(firstMintTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.MINT)
																.withStatus(SUCCESS)
																.withTotalSupply(10)
																.withSerialNumbers()
														)
										)
								.tokenTransfers(changingFungibleBalances()
										.including(fungibleToken, TOKEN_TREASURY, 10)
								)
								.newTotalSupply(10)
						),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
				);
	}

	private HapiApiSpec callForMintWithDelegateContractKey() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var firstMintTxn = "firstMintTxn";
		final var amount = 10L;

		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("callForMintWithDelegateContractKey")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
						uploadInitCode(ORDINARY_CALLS_CONTRACT),
						contractCreate(ORDINARY_CALLS_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														ORDINARY_CALLS_CONTRACT))),
												tokenUpdate(fungibleToken)
														.supplyKey(DELEGATE_KEY),
												contractCall(ORDINARY_CALLS_CONTRACT, "mintTokenCall",
														asAddress(spec.registry().getTokenID(fungibleToken)), amount,
														new byte[]{}
												)
														.via(firstMintTxn)
														.payingWith(theAccount)
										)
						)
				).then(
						childRecordsCheck(firstMintTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.MINT)
																.withStatus(SUCCESS)
																.withTotalSupply(10)
																.withSerialNumbers()
														)
								)
								.tokenTransfers(
										changingFungibleBalances()
												.including(fungibleToken, TOKEN_TREASURY, 10)
								)
								.newTotalSupply(10)
						),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
				);
	}

	private HapiApiSpec callForTransferWithContractKey() {
		return defaultHapiSpec("callForTransferWithContractKey")
				.given(
						newKeyNamed(UNIVERSAL_KEY),
						cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(UNIVERSAL_KEY)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(ACCOUNT, NFT),
						mintToken(NFT, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						uploadInitCode(ORDINARY_CALLS_CONTRACT),
						contractCreate(ORDINARY_CALLS_CONTRACT).via("creationTx")
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														ORDINARY_CALLS_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												tokenAssociate(ORDINARY_CALLS_CONTRACT, List.of(NFT)),
												tokenAssociate(RECEIVER, List.of(NFT)),
												cryptoTransfer(movingUnique(NFT, 1).between(TOKEN_TREASURY, ACCOUNT)),
												contractCall(ORDINARY_CALLS_CONTRACT, "transferNFTCall",
														asAddress(spec.registry().getTokenID(NFT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECEIVER)),
														1L
												)
														.fee(ONE_HBAR)
														.hasKnownStatus(SUCCESS)
														.payingWith(GENESIS)
														.gas(GAS_TO_OFFER)
														.via("distributeTx")
										)
						)
				).then(
						getTokenInfo(NFT).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT, 1),
						getAccountInfo(ACCOUNT).hasOwnedNfts(0),
						getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 0),
						childRecordsCheck("distributeTx", SUCCESS,
								recordWith()
									.status(SUCCESS)
									.contractCallResult(
											resultWith()
												.contractCallResult(htsPrecompileResult()
														.withStatus(SUCCESS)
												)
								)
								.tokenTransfers(
										NonFungibleTransfers.changingNFTBalances()
												.including(NFT, ACCOUNT, RECEIVER, 1L)
								)
						)
				);
	}

	private HapiApiSpec callForTransferWithDelegateContractKey() {
		return defaultHapiSpec("callForTransferWithDelegateContractKey")
				.given(
						newKeyNamed(UNIVERSAL_KEY),
						cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(UNIVERSAL_KEY)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(ACCOUNT, NFT),
						mintToken(NFT, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						uploadInitCode(ORDINARY_CALLS_CONTRACT),
						contractCreate(ORDINARY_CALLS_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
														ORDINARY_CALLS_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												tokenAssociate(ORDINARY_CALLS_CONTRACT, List.of(NFT)),
												tokenAssociate(RECEIVER, List.of(NFT)),
												cryptoTransfer(movingUnique(NFT, 1).between(TOKEN_TREASURY, ACCOUNT)),
												contractCall(ORDINARY_CALLS_CONTRACT, "transferNFTCall",
														asAddress(spec.registry().getTokenID(NFT)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECEIVER)),
														1L
												)
														.fee(ONE_HBAR)
														.hasKnownStatus(SUCCESS)
														.payingWith(GENESIS)
														.gas(GAS_TO_OFFER)
														.via("distributeTx")
										)
						)
				).then(
						getTokenInfo(NFT).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT, 1),
						getAccountInfo(ACCOUNT).hasOwnedNfts(0),
						getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 0),
						childRecordsCheck("distributeTx", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
								)
								.tokenTransfers(
										NonFungibleTransfers.changingNFTBalances().including(NFT, ACCOUNT, RECEIVER, 1L)
								)
						)
				);
	}

	private HapiApiSpec callForAssociateWithDelegateContractKey() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("callAssociateWithDelegateContractKey")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec callForAssociateWithContractKey() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("callAssociateWithContractKey")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}


	public HapiApiSpec callForDissociateWithDelegateContractKey() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final var totalSupply = 1_000;

		return defaultHapiSpec("callDissociateWithDelegateContractKey")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(totalSupply)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_KEY),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(TOKEN_TREASURY, ACCOUNT)),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(ACCOUNT, TOKEN_TREASURY)),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("tokenDissociateWithDelegateContractKeyHappyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
														)
										)
						),
						childRecordsCheck("tokenDissociateWithDelegateContractKeyHappyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	public HapiApiSpec callForDissociateWithContractKey() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final var totalSupply = 1_000;

		return defaultHapiSpec("callDissociateWithContractKey")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(totalSupply)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												cryptoUpdate(TOKEN_TREASURY).key(CONTRACT_KEY),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(TOKEN_TREASURY, ACCOUNT)),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("nonZeroTokenBalanceDissociateWithContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(ACCOUNT, TOKEN_TREASURY)),

												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("tokenDissociateWithContractKeyHappyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("nonZeroTokenBalanceDissociateWithContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES))
										)
						),
						childRecordsCheck("tokenDissociateWithContractKeyHappyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec callForBurnWithDelegateContractKey() {
		final var token = "Token";

		return defaultHapiSpec("callBurnWithDelegateContractKey")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(token)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(BURN_TOKEN),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(BURN_TOKEN, asAddress(spec.registry().getTokenID(token))
												)
														.via("creationTx")
										)
						)
				)
				.when(
						newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))),
						tokenUpdate(token).supplyKey(DELEGATE_KEY),
						contractCall(BURN_TOKEN, "burnToken", 1, new ArrayList<Long>()
						)
								.via("burn with contract key")
								.gas(GAS_TO_OFFER),
						childRecordsCheck("burn with contract key", SUCCESS, recordWith()
								.status(SUCCESS)
								.contractCallResult(
										resultWith()
												.contractCallResult(htsPrecompileResult()
														.forFunction(HTSPrecompileResult.FunctionType.BURN)
														.withStatus(SUCCESS)
														.withTotalSupply(49)
												)
								)
								.tokenTransfers(
										changingFungibleBalances()
												.including(token, TOKEN_TREASURY, -1)
								)
								.newTotalSupply(49)
						)
				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(token, 49)
				);
	}

	private HapiApiSpec delegateCallForAssociatePrecompileSignedWithDelegateContractKeyWorks() {
		final var outerContract = "NestedAssociateDissociate";
		final var nestedContract = "AssociateDissociate";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DelegateCallForAssociatePrecompileSignedWithDelegateContractKeyWorks")
				.given(
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(outerContract, "associateDelegateCall",
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("delegateAssociateCallWithDelegateContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck("delegateAssociateCallWithDelegateContractKeyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks() {
		final var outerContract = "NestedAssociateDissociate";
		final var nestedContract = "AssociateDissociate";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks")
				.given(
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(outerContract, "dissociateDelegateCall",
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("delegateDissociateCallWithDelegateContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck("delegateDissociateCallWithDelegateContractKeyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForNonFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(KYC_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycNFTAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycNFTAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycNFTSecondAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						childRecordsCheck("kycNFTAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						childRecordsCheck("kycNFTAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						childRecordsCheck("kycNFTSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked))
				);
	}

	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForFungibleVanilla")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(treasuryID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("tokenDissociateFromTreasuryFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("tokenDissociateWithDelegateContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(TOKEN_TREASURY, ACCOUNT)
												),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(ACCOUNT, TOKEN_TREASURY)
												),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("tokenDissociateWithDelegateContractKeyHappyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("tokenDissociateFromTreasuryFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(ACCOUNT_IS_TREASURY)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(ACCOUNT_IS_TREASURY)
														)
										)
						),
						childRecordsCheck("tokenDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
														)
										)
						),
						childRecordsCheck("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
														)
										)
						),
						childRecordsCheck("tokenDissociateWithDelegateContractKeyHappyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeDefault(true)
								.freezeKey(FREEZE_KEY)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)

				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												tokenAssociate(ACCOUNT, FROZEN_TOKEN),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenTokenAssociateWithDelegateContractKeyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenUnfreeze(FROZEN_TOKEN, ACCOUNT),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("UnfrozenTokenAssociateWithDelegateContractKeyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("frozenTokenAssociateWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(ACCOUNT_FROZEN_FOR_TOKEN)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(ACCOUNT_FROZEN_FOR_TOKEN)
														)
										)
						),
						childRecordsCheck("UnfrozenTokenAssociateWithDelegateContractKeyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(FROZEN_TOKEN)
				);
	}

	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(KYC_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycTokenDissociateWithDelegateContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, KYC_TOKEN),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycTokenDissociateWithDelegateContractKeyHappyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("kycTokenDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
														)
										)
						),
						childRecordsCheck("kycTokenDissociateWithDelegateContractKeyHappyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KYC_TOKEN)
				);
	}

	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForNonFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForNonFungibleVanilla")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(metadata("memo"))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(treasuryID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("NFTDissociateFromTreasuryFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("NFTDissociateWithDelegateContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												cryptoTransfer(movingUnique(VANILLA_TOKEN, 1)
														.between(TOKEN_TREASURY, ACCOUNT)),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("nonZeroNFTBalanceDissociateWithDelegateContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												cryptoTransfer(movingUnique(VANILLA_TOKEN, 1)
														.between(ACCOUNT, TOKEN_TREASURY)),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("NFTDissociateWithDelegateContractKeyHappyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("NFTDissociateFromTreasuryFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(ACCOUNT_IS_TREASURY)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(ACCOUNT_IS_TREASURY)
														)
										)
						),
						childRecordsCheck("NFTDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
														)
										)
						),
						childRecordsCheck("nonZeroNFTBalanceDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(ACCOUNT_STILL_OWNS_NFTS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(ACCOUNT_STILL_OWNS_NFTS)
														)
										)
						),
						childRecordsCheck("NFTDissociateWithDelegateContractKeyHappyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForNonFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForNonFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeDefault(true)
								.freezeKey(FREEZE_KEY)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												tokenAssociate(ACCOUNT, FROZEN_TOKEN),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenNFTAssociateWithDelegateContractKeyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenUnfreeze(FROZEN_TOKEN, ACCOUNT),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("UnfrozenNFTAssociateWithDelegateContractKeyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("frozenNFTAssociateWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(ACCOUNT_FROZEN_FOR_TOKEN)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(ACCOUNT_FROZEN_FOR_TOKEN)
														)
										)
						),
						childRecordsCheck("UnfrozenNFTAssociateWithDelegateContractKeyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(FROZEN_TOKEN)
				);
	}

	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(KYC_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycNFTDissociateWithDelegateContractKeyFailedTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, KYC_TOKEN),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenDissociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycNFTDissociateWithDelegateContractKeyHappyTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck("kycNFTDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
														)
										)
						),
						childRecordsCheck("kycNFTDissociateWithDelegateContractKeyHappyTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KYC_TOKEN)
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForNonFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForNonFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenNFTAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenNFTAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenNFTSecondAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						childRecordsCheck("frozenNFTAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						childRecordsCheck("frozenNFTAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						childRecordsCheck("frozenNFTSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(FROZEN_TOKEN).freeze(Frozen))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForNonFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForNonFungibleVanilla")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaNFTAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaNFTAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaNFTSecondAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						childRecordsCheck("vanillaNFTAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						childRecordsCheck("vanillaNFTAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
						),
						childRecordsCheck("vanillaNFTSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(KYC_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycTokenAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(kycTokenID.get())
												)
														.payingWith(GENESIS)
														.via("kycTokenSecondAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						childRecordsCheck("kycTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE))
										)
						),
						childRecordsCheck("kycTokenAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),
						childRecordsCheck("kycTokenSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT))
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(KYC_TOKEN).kyc(Revoked))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenTokenAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(frozenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("frozenTokenSecondAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						childRecordsCheck("frozenTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE))
										)
						),
						childRecordsCheck("frozenTokenAssociateTxn", SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS))
										)
						),
						childRecordsCheck("frozenTokenSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT))
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(FROZEN_TOKEN).freeze(Frozen))
				);
	}

	private HapiApiSpec associatePrecompileWithDelegateContractKeyForFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("AssociatePrecompileWithDelegateContractKeyForFungibleVanilla")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						uploadInitCode(ASSOCIATE_DISSOCIATE_CONTRACT),
						contractCreate(ASSOCIATE_DISSOCIATE_CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, ASSOCIATE_DISSOCIATE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaTokenAssociateTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS),
												contractCall(ASSOCIATE_DISSOCIATE_CONTRACT, "tokenAssociate",
														asAddress(accountID.get()), asAddress(vanillaTokenID.get())
												)
														.payingWith(GENESIS)
														.via("vanillaTokenSecondAssociateFailsTxn")
														.gas(GAS_TO_OFFER)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)
						)
				).then(
						childRecordsCheck("vanillaTokenAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)
								.contractCallResult(
										resultWith()
												.contractCallResult(htsPrecompileResult()
														.withStatus(INVALID_SIGNATURE))
								)

						),
						childRecordsCheck("vanillaTokenAssociateTxn", SUCCESS, recordWith()
								.status(SUCCESS)
								.contractCallResult(
										resultWith()
												.contractCallResult(htsPrecompileResult()
														.withStatus(SUCCESS))
								)
						),
						childRecordsCheck("vanillaTokenSecondAssociateFailsTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
								.contractCallResult(
										resultWith()
												.contractCallResult(htsPrecompileResult()
														.withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT))
								)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec delegateCallForAssociatePrecompileSignedWithContractKeyFails() {
		final var outerContract = "NestedAssociateDissociate";
		final var nestedContract = "AssociateDissociate";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DelegateCallForAssociatePrecompileSignedWithContractKeyFails")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												contractCall(outerContract, "associateDelegateCall",
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("delegateAssociateCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck("delegateAssociateCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	private HapiApiSpec delegateCallForDissociatePrecompileSignedWithContractKeyFails() {
		final var outerContract = "NestedAssociateDissociate";
		final var nestedContract = "AssociateDissociate";
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DelegateCallForDissociatePrecompileSignedWithContractKeyFails")
				.given(
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, outerContract))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(outerContract, "dissociateDelegateCall",
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get())
												)
														.payingWith(GENESIS)
														.via("delegateDissociateCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck("delegateDissociateCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	private HapiApiSpec callForBurnWithContractKey() {
		final var token = "Token";

		return defaultHapiSpec("callBurnWithContractKey")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(token)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(BURN_TOKEN),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(BURN_TOKEN, asAddress(spec.registry().getTokenID(token)))
														.via("creationTx")
										)
						)
				)
				.when(
						newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))),
						tokenUpdate(token).supplyKey(CONTRACT_KEY),
						contractCall(BURN_TOKEN, "burnToken", 1, new ArrayList<Long>()
						)
								.via("burn with contract key")
								.gas(GAS_TO_OFFER),
						childRecordsCheck("burn with contract key", SUCCESS, recordWith()
								.status(SUCCESS)
								.contractCallResult(
										resultWith()
												.contractCallResult(htsPrecompileResult()
														.forFunction(HTSPrecompileResult.FunctionType.BURN)
														.withStatus(SUCCESS)
														.withTotalSupply(49)
												)
								)
								.tokenTransfers(
										changingFungibleBalances()
												.including(token, TOKEN_TREASURY, -1)
								)
								.newTotalSupply(49)
						)
				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(token, 49)
				);
	}

	private HapiApiSpec burnTokenWithFullPrefixAndPartialPrefixKeys() {
		final var theAccount = "anybody";
		final var fungibleToken = "fungibleToken";
		final var firstBurnTxn = "firstBurnTxn";
		final var secondBurnTxn = "secondBurnTxn";
		final var amount = 99L;
		final AtomicLong fungibleNum = new AtomicLong();

		return defaultHapiSpec("burnTokenWithFullPrefixAndPartialPrefixKeys")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(100)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(idLit -> fungibleNum.set(asDotDelimitedLongArray(idLit)[2])),
						uploadInitCode(ORDINARY_CALLS_CONTRACT),
						contractCreate(ORDINARY_CALLS_CONTRACT)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ORDINARY_CALLS_CONTRACT, "burnTokenCall",
														asAddress(spec.registry().getTokenID(fungibleToken)),
														1, new ArrayList<Long>()
												)
														.via(firstBurnTxn)
														.payingWith(theAccount)
														.signedBy(MULTI_KEY)
														.signedBy(theAccount)
														.hasKnownStatus(SUCCESS),
												contractCall(ORDINARY_CALLS_CONTRACT, "burnTokenCall",
														asAddress(spec.registry().getTokenID(fungibleToken)),
														1, new ArrayList<Long>()
												)
														.via(secondBurnTxn).payingWith(theAccount)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck(firstBurnTxn, SUCCESS,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BURN)
																.withStatus(INVALID_SIGNATURE)
														)
										)
						),
						childRecordsCheck(secondBurnTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BURN)
																.withStatus(SUCCESS)
																.withTotalSupply(99)
														)
										)
										.newTotalSupply(99)
						),
						getTokenInfo(fungibleToken).hasTotalSupply(amount),
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, amount)
				);
	}

	private HapiApiSpec mixedFramesScenarios() {
		final var theAccount = "theAccount";
		final var fungibleToken = "fungibleToken";
		final var nestedContract = "MixedMintToken";
		final var outerContract = "MixedFramesScenarios";
		final var delegateContractDelegateContractShape = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT,
				DELEGATE_CONTRACT);
		final var contractDelegateContractShape = KeyShape.threshOf(1, SIMPLE, KeyShape.CONTRACT, DELEGATE_CONTRACT);
		final var delegateContractDelegateContractKey = "delegateContractDelegateContractKey";
		final var contractDelegateContractKey = "contractDelegateContractKey";

		return defaultHapiSpec("HSCS_KEY_MIXED_FRAMES_SCENARIOS")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(fungibleToken)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(50L)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(outerContract, nestedContract),
						contractCreate(nestedContract)
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(outerContract, getNestedContractAddress(nestedContract, spec)
												)
														.via("creationTx"),
												newKeyNamed(delegateContractDelegateContractKey).shape(delegateContractDelegateContractShape.signedWith(sigs(ON,
														nestedContract, outerContract))),
												tokenUpdate(fungibleToken)
														.supplyKey(delegateContractDelegateContractKey),
												contractCall(outerContract,
														"burnCallAfterNestedMintCallWithPrecompileCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via("burnCallAfterNestedMintCallWithPrecompileCall"),
												contractCall(outerContract,
														"burnDelegateCallAfterNestedMintCallWithPrecompileDelegateCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via("burnDelegateCallAfterNestedMintCallWithPrecompileDelegateCall"),
												contractCall(outerContract,
														"burnDelegateCallAfterNestedMintDelegateCallWithPrecompileDelegateCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via("burnDelegateCallAfterNestedMintDelegateCallWithPrecompileDelegateCall"),
												contractCall(outerContract,
														"burnCallAfterNestedMintDelegateCallWithPrecompileDelegateCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via("burnCallAfterNestedMintDelegateCallWithPrecompileDelegateCall")
										)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(contractDelegateContractKey).shape(contractDelegateContractShape.signedWith(sigs(ON,
														nestedContract, outerContract))),
												tokenUpdate(fungibleToken)
														.supplyKey(contractDelegateContractKey),
												contractCall(outerContract,
														"burnDelegateCallAfterNestedMintCallWithPrecompileCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via("burnDelegateCallAfterNestedMintCallWithPrecompileCall"),
												contractCall(outerContract,
														"burnDelegateCallAfterNestedMintDelegateCallWithPrecompileCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via(
																"burnDelegateCallAfterNestedMintDelegateCallWithPrecompileCall"),
												contractCall(outerContract,
														"burnCallAfterNestedMintDelegateCallWithPrecompileCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via(
																"burnCallAfterNestedMintDelegateCallWithPrecompileCall")
										)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												newKeyNamed(contractDelegateContractKey).shape(contractDelegateContractShape.signedWith(sigs(ON,
														outerContract, nestedContract))),
												tokenUpdate(fungibleToken)
														.supplyKey(contractDelegateContractKey),
												contractCall(outerContract, "burnCallAfterNestedMintCallWithPrecompileDelegateCall",
														1, asAddress(spec.registry().getTokenID(fungibleToken))
												)
														.payingWith(theAccount)
														.via("burnCallAfterNestedMintCallWithPrecompileDelegateCall")
										)),
						childRecordsCheck("burnCallAfterNestedMintCallWithPrecompileCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintCallWithPrecompileCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintDelegateCallWithPrecompileCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnCallAfterNestedMintDelegateCallWithPrecompileCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnCallAfterNestedMintCallWithPrecompileDelegateCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintCallWithPrecompileDelegateCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnDelegateCallAfterNestedMintDelegateCallWithPrecompileDelegateCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						),
						childRecordsCheck("burnCallAfterNestedMintDelegateCallWithPrecompileDelegateCall", SUCCESS, recordWith()
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
														.including(fungibleToken, TOKEN_TREASURY, 1)
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
														.including(fungibleToken, TOKEN_TREASURY, -1)
										)
										.newTotalSupply(50)
						)
				)
				.then(
						getAccountBalance(TOKEN_TREASURY).hasTokenBalance(fungibleToken, 50)
				);
	}

	@NotNull
	private String getNestedContractAddress(String contract, HapiApiSpec spec) {
        return AssociatePrecompileSuite.getNestedContractAddress(contract, spec);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}