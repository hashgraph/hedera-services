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

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.assertions.NonFungibleTransfers;
import com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AutoAssocAsserts.accountTokenPairs;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.accountAmount;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nftTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferList;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.tokenTransferLists;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CryptoTransferHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoTransferHTSSuite.class);

	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String FUNGIBLE_TOKEN = "TokenA";
	private static final String NFT_TOKEN = "Token_NFT";
	private static final String TOKEN_TREASURY = "treasury";
	private static final String RECEIVER = "receiver";
	private static final String RECEIVER2 = "receiver2";
	private static final String SENDER = "sender";
	private static final String SENDER2 = "sender2";
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, KeyShape.SIMPLE,
			DELEGATE_CONTRACT);

	private static final String DELEGATE_KEY = "contractKey";
	private static final String CONTRACT = "CryptoTransfer";
	private static final String MULTI_KEY = "purpose";

	public static void main(String... args) {
		new CryptoTransferHTSSuite().runSuiteAsync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
						nonNestedCryptoTransferForFungibleToken(),
						nonNestedCryptoTransferForFungibleTokenWithMultipleReceivers(),
						nonNestedCryptoTransferForNonFungibleToken(),
						nonNestedCryptoTransferForMultipleNonFungibleTokens(),
						nonNestedCryptoTransferForFungibleAndNonFungibleToken(),
						nonNestedCryptoTransferForFungibleTokenWithMultipleSendersAndReceiversAndNonFungibleTokens(),
						repeatedTokenIdsAreAutomaticallyConsolidated(),
						activeContractInFrameIsVerifiedWithoutNeedForSignature()
				}
		);
	}

	private HapiApiSpec repeatedTokenIdsAreAutomaticallyConsolidated() {
		final var repeatedIdsPrecompileXferTxn = "repeatedIdsPrecompileXfer";
		final var senderStartBalance = 200L;
		final var receiverStartBalance = 0L;
		final var toSendEachTuple = 50L;

		return defaultHapiSpec("RepeatedTokenIdsAreAutomaticallyConsolidated")
				.given(
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER)
								.balance(2 * ONE_HUNDRED_HBARS)
								.receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(senderStartBalance, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						withOpContext((spec, opLog) -> {
									final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var receiver = spec.registry().getAccountID(RECEIVER);

									allRunFor(
											spec,
											newKeyNamed(DELEGATE_KEY).shape(
													DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
															CONTRACT))),
											cryptoUpdate(SENDER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
											contractCall(CONTRACT, "transferMultipleTokens", Tuple.singleton(
													new Tuple[] {
															tokenTransferList()
																	.forToken(token)
																	.isSingleList(false)
																	.withAccountAmounts(
																			accountAmount(sender, -toSendEachTuple),
																			accountAmount(receiver, toSendEachTuple)
																	).build(),
															tokenTransferList()
																	.forToken(token)
																	.isSingleList(false)
																	.withAccountAmounts(
																			accountAmount(sender, -toSendEachTuple),
																			accountAmount(receiver, toSendEachTuple)
																	).build()
													}
											))
													.payingWith(GENESIS)
													.via(repeatedIdsPrecompileXferTxn)
													.gas(GAS_TO_OFFER)
									);
								}
						),
						getTxnRecord(repeatedIdsPrecompileXferTxn).andAllChildRecords().logged()
				).then(
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, receiverStartBalance + 2 * toSendEachTuple),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - 2 * toSendEachTuple),
						childRecordsCheck(repeatedIdsPrecompileXferTxn, SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .withStatus(SUCCESS)
                                                        )
                                        )
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(FUNGIBLE_TOKEN, SENDER, -2 * toSendEachTuple)
                                                        .including(FUNGIBLE_TOKEN, RECEIVER, 2 * toSendEachTuple))));
	}

	private HapiApiSpec nonNestedCryptoTransferForFungibleToken() {
		final var cryptoTransferTxn = "cryptoTransferTxn";

		return defaultHapiSpec("NonNestedCryptoTransferForFungibleToken")
				.given(
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT).maxAutomaticTokenAssociations(1),
						getContractInfo(CONTRACT)
								.has(ContractInfoAsserts.contractWith().maxAutoAssociations(1))
								.logged()
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var receiver = spec.registry().getAccountID(RECEIVER);
									final var amountToBeSent = 50L;

									allRunFor(
											spec,
											newKeyNamed(DELEGATE_KEY).shape(
													DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
															CONTRACT))),
											cryptoUpdate(SENDER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
											contractCall(CONTRACT, "transferMultipleTokens",
													tokenTransferList().forToken(token).withAccountAmounts(
															accountAmount(sender, -amountToBeSent),
															accountAmount(receiver, amountToBeSent)).build()
											)
													.payingWith(GENESIS)
													.via(cryptoTransferTxn)
													.gas(GAS_TO_OFFER)
									);
								}
						),
						getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged()
				).then(
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 50),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 150),
						getTokenInfo(FUNGIBLE_TOKEN).logged(),
						childRecordsCheck(cryptoTransferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
										.tokenTransfers(
												SomeFungibleTransfers.changingFungibleBalances()
														.including(FUNGIBLE_TOKEN, SENDER, -50)
														.including(FUNGIBLE_TOKEN, RECEIVER, 50))));
	}

	private HapiApiSpec nonNestedCryptoTransferForFungibleTokenWithMultipleReceivers() {
		final var cryptoTransferTxn = "cryptoTransferTxn";

		return defaultHapiSpec("NonNestedCryptoTransferForFungibleTokenWithMultipleReceivers")
				.given(
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS).receiverSigRequired(true),
						cryptoCreate(RECEIVER2).balance(ONE_HUNDRED_HBARS).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER2, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var receiver = spec.registry().getAccountID(RECEIVER);
									final var receiver2 = spec.registry().getAccountID(RECEIVER2);

									allRunFor(
											spec,
											newKeyNamed(DELEGATE_KEY).shape(
													DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
															CONTRACT))),
											cryptoUpdate(SENDER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
											contractCall(CONTRACT, "transferMultipleTokens",
													tokenTransferList().forToken(token).withAccountAmounts(
															accountAmount(sender, -50L),
															accountAmount(receiver, 30L),
															accountAmount(receiver2, 20L)).build()
											)
													.gas(GAS_TO_OFFER)
													.payingWith(GENESIS)
													.via(cryptoTransferTxn)
									);
								}
						),
						getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged()
				).then(
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 30),
						getAccountBalance(RECEIVER2)
								.hasTokenBalance(FUNGIBLE_TOKEN, 20),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 150),
						getTokenInfo(FUNGIBLE_TOKEN).logged(),
						childRecordsCheck(cryptoTransferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
										.tokenTransfers(
												SomeFungibleTransfers.changingFungibleBalances()
														.including(FUNGIBLE_TOKEN, SENDER, -50)
														.including(FUNGIBLE_TOKEN, RECEIVER, 30)
														.including(FUNGIBLE_TOKEN, RECEIVER2, 20))));
	}

	private HapiApiSpec nonNestedCryptoTransferForNonFungibleToken() {
		final var cryptoTransferTxn = "cryptoTransferTxn";

		return defaultHapiSpec("NonNestedCryptoTransferForNonFungibleToken")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(SENDER, List.of(NFT_TOKEN)),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
						cryptoTransfer(
								TokenMovement.movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER)).payingWith(
								SENDER),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) ->
								{
									final var token = spec.registry().getTokenID(NFT_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var receiver = spec.registry().getAccountID(RECEIVER);

									allRunFor(
											spec,
											newKeyNamed(DELEGATE_KEY).shape(
													DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
															CONTRACT))),
											cryptoUpdate(SENDER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
											contractCall(CONTRACT, "transferMultipleTokens",
													tokenTransferList().forToken(token).
															withNftTransfers(nftTransfer(sender, receiver,
																	1L)).build()
											)
													.payingWith(GENESIS)
													.via(cryptoTransferTxn)
													.gas(GAS_TO_OFFER)
									);
								}
						),
						getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged()

				).then(
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER).hasOwnedNfts(0),
						getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged(),
						childRecordsCheck("cryptoTransferTxn", SUCCESS,
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
														.including(NFT_TOKEN, SENDER, RECEIVER, 1L))));
	}

	private HapiApiSpec nonNestedCryptoTransferForMultipleNonFungibleTokens() {
		final var cryptoTransferTxn = "cryptoTransferTxn";

		return defaultHapiSpec("NonNestedCryptoTransferForMultipleNonFungibleTokens")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).receiverSigRequired(true),
						cryptoCreate(RECEIVER2).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(SENDER, List.of(NFT_TOKEN)),
						tokenAssociate(SENDER2, List.of(NFT_TOKEN)),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(RECEIVER, List.of(NFT_TOKEN)),
						tokenAssociate(RECEIVER2, List.of(NFT_TOKEN)),
						cryptoTransfer(
								movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER)).payingWith(
								SENDER),
						cryptoTransfer(
								TokenMovement.movingUnique(NFT_TOKEN, 2).between(TOKEN_TREASURY, SENDER2)).payingWith(
								SENDER2),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var token = spec.registry().getTokenID(NFT_TOKEN);
									final var sender = spec.registry().getAccountID(SENDER);
									final var sender2 = spec.registry().getAccountID(SENDER2);
									final var receiver = spec.registry().getAccountID(RECEIVER);
									final var receiver2 = spec.registry().getAccountID(RECEIVER2);

									allRunFor(
											spec,
											newKeyNamed(DELEGATE_KEY).shape(
													DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
															CONTRACT))),
											cryptoUpdate(SENDER).key(DELEGATE_KEY),
											cryptoUpdate(SENDER2).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
											contractCall(CONTRACT, "transferMultipleTokens",
													tokenTransferList().forToken(token).
															withNftTransfers(
																	nftTransfer(sender, receiver, 1L),
																	nftTransfer(sender2, receiver2, 2L))
															.build()
											)
													.payingWith(GENESIS)
													.via(cryptoTransferTxn).gas(GAS_TO_OFFER)
									);
								}
						),
						getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged()
				).then(
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER).hasOwnedNfts(0),
						getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
						getAccountInfo(RECEIVER2).hasOwnedNfts(1),
						getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER2).hasOwnedNfts(0),
						getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged(),
						childRecordsCheck(cryptoTransferTxn, SUCCESS,
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
														.including(NFT_TOKEN, SENDER, RECEIVER, 1L)
														.including(NFT_TOKEN, SENDER2, RECEIVER2, 2L))));
	}

	private HapiApiSpec nonNestedCryptoTransferForFungibleAndNonFungibleToken() {
		final var cryptoTransferTxn = "cryptoTransferTxn";

		return defaultHapiSpec("NonNestedCryptoTransferForFungibleAndNonFungibleToken")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).receiverSigRequired(true),
						cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER2).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(SENDER2, List.of(NFT_TOKEN)),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECEIVER2, List.of(NFT_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER),
						cryptoTransfer(
								TokenMovement.movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER2)).payingWith(
								SENDER2),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var fungibleToken = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var nonFungibleToken = spec.registry().getTokenID(NFT_TOKEN);
									final var fungibleTokenSender = spec.registry().getAccountID(SENDER);
									final var fungibleTokenReceiver = spec.registry().getAccountID(RECEIVER);
									final var nonFungibleTokenSender = spec.registry().getAccountID(SENDER2);
									final var nonFungibleTokenReceiver = spec.registry().getAccountID(RECEIVER2);

									allRunFor(
											spec,
											newKeyNamed(DELEGATE_KEY).shape(
													DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
															CONTRACT))),
											cryptoUpdate(SENDER).key(DELEGATE_KEY),
											cryptoUpdate(SENDER2).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
											contractCall(CONTRACT, "transferMultipleTokens",
													tokenTransferLists().withTokenTransferList(
																	tokenTransferList().isSingleList(false).forToken(
																					fungibleToken).
																			withAccountAmounts(
																					accountAmount(fungibleTokenSender,
																							-45L),
																					accountAmount(fungibleTokenReceiver,
																							45L)).build(),
																	tokenTransferList().isSingleList(false).forToken(
																					nonFungibleToken).
																			withNftTransfers(
																					nftTransfer(nonFungibleTokenSender,
																							nonFungibleTokenReceiver,
																							1L)).build())
															.build()
											)
													.payingWith(GENESIS)
													.via(cryptoTransferTxn)
													.gas(GAS_TO_OFFER)
									);
								}
						),
						getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged()
				).then(
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 45),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 155),
						getTokenInfo(FUNGIBLE_TOKEN).logged(),
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER2).hasOwnedNfts(1),
						getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER2).hasOwnedNfts(0),
						getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged(),
						childRecordsCheck(cryptoTransferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))
										.tokenTransfers(
												SomeFungibleTransfers.changingFungibleBalances()
														.including(FUNGIBLE_TOKEN, SENDER, -45L)
														.including(FUNGIBLE_TOKEN, RECEIVER, 45L)
										).tokenTransfers(NonFungibleTransfers.changingNFTBalances()
												.including(NFT_TOKEN, SENDER2, RECEIVER2, 1L))));
	}

	private HapiApiSpec nonNestedCryptoTransferForFungibleTokenWithMultipleSendersAndReceiversAndNonFungibleTokens() {
		final var cryptoTransferTxn = "cryptoTransferTxn";

		return defaultHapiSpec(
				"NonNestedCryptoTransferForFungibleTokenWithMultipleSendersAndReceiversAndNonFungibleTokens")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER).receiverSigRequired(true),
						cryptoCreate(SENDER2).balance(5 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECEIVER2).receiverSigRequired(true),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
						tokenAssociate(SENDER2, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
						tokenAssociate(RECEIVER2, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
						cryptoTransfer(moving(200, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)).payingWith(SENDER),
						cryptoTransfer(moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER2)).payingWith(SENDER2),
						cryptoTransfer(
								movingUnique(NFT_TOKEN, 1).between(TOKEN_TREASURY, SENDER)).payingWith(
								SENDER),
						cryptoTransfer(
								TokenMovement.movingUnique(NFT_TOKEN, 2).between(TOKEN_TREASURY, SENDER2)).payingWith(
								SENDER2),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var fungibleToken = spec.registry().getTokenID(FUNGIBLE_TOKEN);
									final var nonFungibleToken = spec.registry().getTokenID(NFT_TOKEN);
									final var firstSender = spec.registry().getAccountID(SENDER);
									final var firstReceiver = spec.registry().getAccountID(RECEIVER);
									final var secondSender = spec.registry().getAccountID(SENDER2);
									final var secondReceiver = spec.registry().getAccountID(RECEIVER2);

									allRunFor(
											spec,
											newKeyNamed(DELEGATE_KEY).shape(
													DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON,
															CONTRACT))),
											cryptoUpdate(SENDER).key(DELEGATE_KEY),
											cryptoUpdate(SENDER2).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER).key(DELEGATE_KEY),
											cryptoUpdate(RECEIVER2).key(DELEGATE_KEY),
											contractCall(CONTRACT, "transferMultipleTokens",
													tokenTransferLists().withTokenTransferList(
																	tokenTransferList().isSingleList(false).forToken(
																					fungibleToken).
																			withAccountAmounts(
																					accountAmount(firstSender, -45L),
																					accountAmount(firstReceiver, 45L),
																					accountAmount(secondSender, -32L),
																					accountAmount(secondReceiver, 32L)).build(),
																	tokenTransferList().isSingleList(false).forToken(
																					nonFungibleToken).
																			withNftTransfers(
																					nftTransfer(firstSender,
																							firstReceiver, 1L),
																					nftTransfer(secondSender,
																							secondReceiver,
																							2L)).
																			build())
															.build()
											)
													.payingWith(GENESIS)
													.via(cryptoTransferTxn)
													.gas(GAS_TO_OFFER)
									);
								}
						),
						getTxnRecord(cryptoTransferTxn).andAllChildRecords().logged()
				).then(
						getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(TOTAL_SUPPLY),
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 45),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, 155),
						getAccountBalance(RECEIVER2)
								.hasTokenBalance(FUNGIBLE_TOKEN, 32),
						getAccountBalance(SENDER2)
								.hasTokenBalance(FUNGIBLE_TOKEN, 68),
						getTokenInfo(FUNGIBLE_TOKEN).logged(),
						getTokenInfo(NFT_TOKEN).hasTotalSupply(2),
						getAccountInfo(RECEIVER).hasOwnedNfts(1),
						getAccountBalance(RECEIVER).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER).hasOwnedNfts(0),
						getAccountBalance(SENDER).hasTokenBalance(NFT_TOKEN, 0),
						getAccountInfo(RECEIVER2).hasOwnedNfts(1),
						getAccountBalance(RECEIVER2).hasTokenBalance(NFT_TOKEN, 1),
						getAccountInfo(SENDER2).hasOwnedNfts(0),
						getAccountBalance(SENDER2).hasTokenBalance(NFT_TOKEN, 0),
						getTokenInfo(NFT_TOKEN).logged(),
						childRecordsCheck(cryptoTransferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)
														)
										)
										.tokenTransfers(
												SomeFungibleTransfers.changingFungibleBalances()
														.including(FUNGIBLE_TOKEN, SENDER, -45L)
														.including(FUNGIBLE_TOKEN, RECEIVER, 45L)
														.including(FUNGIBLE_TOKEN, SENDER2, -32L)
														.including(FUNGIBLE_TOKEN, RECEIVER2, 32L)
										).tokenTransfers(NonFungibleTransfers.changingNFTBalances()
												.including(NFT_TOKEN, SENDER, RECEIVER, 1L)
												.including(NFT_TOKEN, SENDER2, RECEIVER2, 2L))));
	}

	private HapiApiSpec activeContractInFrameIsVerifiedWithoutNeedForSignature() {
		final var revertedFungibleTransferTxn = "revertedFungibleTransferTxn";
		final var successfulFungibleTransferTxn = "successfulFungibleTransferTxn";
		final var revertedNftTransferTxn = "revertedNftTransferTxn";
		final var successfulNftTransferTxn = "successfulNftTransferTxn";
		final var senderStartBalance = 200L;
		final var receiverStartBalance = 0L;
		final var toSendEachTuple = 50L;
		final var multiKey = "purpose";
		final var senderKey = "senderKey";
		final var contractKey = "contractAdminKey";

		return defaultHapiSpec("ActiveContractIsVerifiedWithoutCheckingSignatures")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed(senderKey),
						newKeyNamed(contractKey),
						cryptoCreate(SENDER).balance(10 * ONE_HUNDRED_HBARS).key(senderKey),
						cryptoCreate(RECEIVER).balance(2 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						uploadInitCode(CONTRACT),
						contractCreate(CONTRACT)
								.payingWith(GENESIS)
								.adminKey(contractKey)
								.gas(GAS_TO_OFFER),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenCreate(NFT_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT_TOKEN, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						tokenAssociate(SENDER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
						tokenAssociate(RECEIVER, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
						tokenAssociate(CONTRACT, List.of(FUNGIBLE_TOKEN, NFT_TOKEN)),
						cryptoTransfer(moving(senderStartBalance, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, SENDER)),
						cryptoTransfer(movingUnique(NFT_TOKEN, 1L).between(TOKEN_TREASURY, SENDER)),
						cryptoTransfer(moving(senderStartBalance, FUNGIBLE_TOKEN).between(TOKEN_TREASURY,
								CONTRACT), movingUnique(NFT_TOKEN, 2L).between(TOKEN_TREASURY,
								CONTRACT))
				).when(
						withOpContext((spec, opLog) -> {
							final var token = spec.registry().getTokenID(FUNGIBLE_TOKEN);
							final var nftToken = spec.registry().getTokenID(NFT_TOKEN);
							final var sender = spec.registry().getAccountID(SENDER);
							final var receiver = spec.registry().getAccountID(RECEIVER);
							final var contractId = spec.registry().getAccountID(CONTRACT);
							allRunFor(
									spec,
									contractCall(CONTRACT, "transferMultipleTokens", Tuple.singleton(
											new Tuple[] {
													tokenTransferList()
															.forToken(token)
															.isSingleList(false)
															.withAccountAmounts(
																	accountAmount(contractId, -toSendEachTuple),
																	accountAmount(receiver, toSendEachTuple)
															).build(),
													tokenTransferList()
															.forToken(token)
															.isSingleList(false)
															.withAccountAmounts(
																	accountAmount(sender, -toSendEachTuple),
																	accountAmount(receiver, toSendEachTuple)
															).build()
											}
									))
											.payingWith(GENESIS)
											.via(revertedFungibleTransferTxn)
											.gas(GAS_TO_OFFER)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
									contractCall(CONTRACT, "transferMultipleTokens", Tuple.singleton(
											new Tuple[] {
													tokenTransferList()
															.forToken(token)
															.isSingleList(false)
															.withAccountAmounts(
																	accountAmount(contractId, -toSendEachTuple),
																	accountAmount(receiver, toSendEachTuple)
															).build(),
													tokenTransferList()
															.forToken(token)
															.isSingleList(false)
															.withAccountAmounts(
																	accountAmount(sender, -toSendEachTuple),
																	accountAmount(receiver, toSendEachTuple)
															).build()
											}
									))
											.payingWith(GENESIS)
											.alsoSigningWithFullPrefix(senderKey)
											.via(successfulFungibleTransferTxn)
											.gas(GAS_TO_OFFER)
											.hasKnownStatus(SUCCESS),
									contractCall(CONTRACT, "transferMultipleTokens", Tuple.singleton(
											new Tuple[] {
													tokenTransferList()
															.forToken(nftToken)
															.isSingleList(false)
															.withNftTransfers(nftTransfer(contractId, receiver, 2L))
															.build(),
													tokenTransferList()
															.forToken(nftToken)
															.isSingleList(false)
															.withNftTransfers(nftTransfer(sender, receiver, 1L))
															.build()
											}
									))
											.payingWith(GENESIS)
											.via(revertedNftTransferTxn)
											.gas(GAS_TO_OFFER)
											.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
									contractCall(CONTRACT, "transferMultipleTokens", Tuple.singleton(
											new Tuple[] {
													tokenTransferList()
															.forToken(nftToken)
															.isSingleList(false)
															.withNftTransfers(nftTransfer(contractId, receiver, 2L))
															.build(),
													tokenTransferList()
															.forToken(nftToken)
															.isSingleList(false)
															.withNftTransfers(nftTransfer(sender, receiver, 1L))
															.build()
											}
									))
											.payingWith(GENESIS)
											.via(successfulNftTransferTxn)
											.alsoSigningWithFullPrefix(senderKey)
											.gas(GAS_TO_OFFER)
											.hasKnownStatus(SUCCESS));
						})
				).then(
						getAccountBalance(RECEIVER)
								.hasTokenBalance(FUNGIBLE_TOKEN, receiverStartBalance + 2 * toSendEachTuple)
								.hasTokenBalance(NFT_TOKEN, 2L),
						getAccountBalance(SENDER)
								.hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - toSendEachTuple)
								.hasTokenBalance(NFT_TOKEN, 0L),
						getAccountBalance(CONTRACT)
								.hasTokenBalance(FUNGIBLE_TOKEN, senderStartBalance - toSendEachTuple)
								.hasTokenBalance(NFT_TOKEN, 0L),
                        childRecordsCheck(revertedFungibleTransferTxn, CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)))),
                        childRecordsCheck(successfulFungibleTransferTxn, SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .withStatus(SUCCESS)
                                                        )
                                        )
                                        .tokenTransfers(
                                                SomeFungibleTransfers.changingFungibleBalances()
                                                        .including(FUNGIBLE_TOKEN, SENDER, -toSendEachTuple)
                                                        .including(FUNGIBLE_TOKEN, CONTRACT, -toSendEachTuple)
                                                        .including(FUNGIBLE_TOKEN, RECEIVER, 2 * toSendEachTuple))),
						childRecordsCheck(revertedNftTransferTxn, CONTRACT_REVERT_EXECUTED,
								recordWith()
										.status(INVALID_SIGNATURE)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(INVALID_SIGNATURE)))),
						childRecordsCheck(successfulNftTransferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.withStatus(SUCCESS)))
										.tokenTransfers(
												NonFungibleTransfers.changingNFTBalances()
														.including(NFT_TOKEN, SENDER, RECEIVER, 1L)
														.including(NFT_TOKEN, CONTRACT, RECEIVER, 2L))));
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
