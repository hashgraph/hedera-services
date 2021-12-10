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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DISSOCIATE_TOKEN;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.VERSATILE_CRYPTO_TRANSFERS;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.VERSATILE_TRANSFERS_TOKENS;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ZENOS_BANK_DEPOSIT_TOKENS;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ZENOS_BANK_WITHDRAW_TOKENS;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class ContractHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractHTSSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String A_TOKEN = "TokenA";
	private static final String TOKEN_TREASURY = "treasury";


	private static final String ACCOUNT = "receiver";
	private static final String NFT = "nft";
	private static final String SUPPLY_KEY = "supplyKey";


	public static void main(String... args) {
		new ContractHTSSuite().runSuiteAsync();
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
		return List.of();
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				distributeTokens(),
				depositAndWithdraw(),
				associateToken(),
				dissociateToken()
		);
	}

	private HapiApiSpec depositAndWithdraw() {
		final var theAccount = "anybody";
		final var theReceiver = "somebody";
		final var theKey = "multipurpose";
		final var theContract = "zeno's bank";
		return defaultHapiSpec("depositAndWithdraw")
				.given(
						newKeyNamed(theKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(theReceiver),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("bytecode").payingWith(theAccount),
						updateLargeFile(theAccount, "bytecode", extractByteCode(ContractResources.ZENOS_BANK_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, ContractResources.ZENOS_BANK_CONSTRUCTOR,
														asAddress(spec.registry().getTokenID(A_TOKEN)))
														.payingWith(theAccount)
														.bytecode("bytecode")
														.via("creationTx")
														.gas(28_000))),
						getTxnRecord("creationTx").logged(),
						tokenAssociate(theAccount, List.of(A_TOKEN)),
						tokenAssociate(theContract, List.of(A_TOKEN)),
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
				).when(
						contractCall(theContract, ZENOS_BANK_DEPOSIT_TOKENS, 50)
								.payingWith(theAccount)
								.gas(48_000)
								.via("zeno"),
						getTxnRecord("zeno").andAllChildRecords().logged()
				).then(
						contractCall(theContract, ZENOS_BANK_WITHDRAW_TOKENS)
								.payingWith(theReceiver)
								.alsoSigningWithFullPrefix(theContract)
								.gas(70_000)
								.via("receiver"),
						getTxnRecord("receiver").andAllChildRecords().logged()
				);
	}

	private HapiApiSpec distributeTokens() {
		final var theAccount = "anybody";
		final var theReceiver = "somebody";
		final var theSecondReceiver = "somebody2";
		final var theKey = "multipurpose";
		final var theContract = "versatileTransfers";
		return defaultHapiSpec("distributeTokens")
				.given(
						newKeyNamed(theKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(theReceiver),
						cryptoCreate(theSecondReceiver),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("bytecode").payingWith(theAccount),
						updateLargeFile(theAccount, "bytecode", extractByteCode(ContractResources.VERSATILE_TRANSFERS_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.payingWith(theAccount)
														.bytecode("bytecode")
														.via("creationTx")
														.gas(28_000))),
						getTxnRecord("creationTx").logged(),
						tokenAssociate(theAccount, List.of(A_TOKEN)),
						tokenAssociate(theContract, List.of(A_TOKEN)),
						tokenAssociate(theReceiver, List.of(A_TOKEN)),
						tokenAssociate(theSecondReceiver, List.of(A_TOKEN)),
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var sender = asAddress(spec.registry().getAccountID(theAccount));
									final var receiver1 = asAddress(spec.registry().getAccountID(theReceiver));
									final var receiver2 = asAddress(spec.registry().getAccountID(theSecondReceiver));

									final var accounts = List.of(sender, receiver1, receiver2);
									final var amounts = List.of(-10L, 5L, 5L);

									allRunFor(
											spec,
											contractCall(theContract, VERSATILE_TRANSFERS_TOKENS,
													asAddress(spec.registry().getTokenID(A_TOKEN)),
													accounts.toArray(),
													amounts.toArray()
											)
													.payingWith(theAccount)
													.gas(48_000)
													.via("distribute"),
											getTxnRecord("distribute").logged());
								})

				).then(
				);
	}

	private HapiApiSpec distributeCryptoTransfers() {
		final var theAccount = "anybody";
		final var theReceiver = "somebody";
		final var theSecondReceiver = "somebody2";
		final var theKey = "multipurpose";
		final var theContract = "versatileTransfers";
		return defaultHapiSpec("distributeCryptoTransfers")
				.given(
						newKeyNamed(theKey),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(theReceiver),
						cryptoCreate(theSecondReceiver),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("bytecode").payingWith(theAccount),
						updateLargeFile(theAccount, "bytecode", extractByteCode(ContractResources.VERSATILE_TRANSFERS_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.payingWith(theAccount)
														.bytecode("bytecode")
														.via("creationTx")
														.gas(28_000))),
						getTxnRecord("creationTx").logged(),
						tokenAssociate(theAccount, List.of(A_TOKEN)),
						tokenAssociate(theContract, List.of(A_TOKEN)),
						tokenAssociate(theReceiver, List.of(A_TOKEN)),
						tokenAssociate(theSecondReceiver, List.of(A_TOKEN)),
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
				).when(
						withOpContext(
								(spec, opLog) -> {
									final var sender = asAddress(spec.registry().getAccountID(theAccount));
									final var receiver1 = asAddress(spec.registry().getAccountID(theReceiver));
									final var receiver2 = asAddress(spec.registry().getAccountID(theSecondReceiver));

									final var accounts = List.of(sender, receiver1, receiver2);
									final var amounts = List.of(-10L, 5L, 5L);

									allRunFor(
											spec,
											contractCall(theContract, VERSATILE_CRYPTO_TRANSFERS,
													asAddress(spec.registry().getTokenID(A_TOKEN)),
													accounts.toArray(),
													amounts.toArray()
											)
													.payingWith(theAccount)
													.gas(48_000)
													.via("distribute"),
											getTxnRecord("distribute").logged());
								})

				).then(
				);
	}

	private HapiApiSpec associateToken() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		return defaultHapiSpec("associateHappyPath")
				.given(
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("associateDissociateContractByteCode").payingWith(theAccount),
						updateLargeFile(theAccount, "associateDissociateContractByteCode",
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, ContractResources.ASSOCIATE_DISSOCIATE_CONSTRUCTOR,
														asAddress(spec.registry().getTokenID(A_TOKEN)))
														.payingWith(theAccount)
														.bytecode("associateDissociateContractByteCode")
														.via("associateTxn")
														.gas(100000),
												cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
														.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										)
						)
				).when(
						contractCall(theContract, ContractResources.ASSOCIATE_TOKEN).payingWith(theAccount).via("associateMethodCall")
				).then(
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
								.hasKnownStatus(ResponseCodeEnum.SUCCESS)
				);
	}

	private HapiApiSpec dissociateToken() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		return defaultHapiSpec("dissociateHappyPath")
				.given(
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						fileCreate("associateDissociateContractByteCode").payingWith(theAccount),
						updateLargeFile(theAccount, "associateDissociateContractByteCode",
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract, ContractResources.ASSOCIATE_DISSOCIATE_CONSTRUCTOR,
														asAddress(spec.registry().getTokenID(A_TOKEN)))
														.payingWith(theAccount)
														.bytecode("associateDissociateContractByteCode")
														.via("associateTxn")
														.gas(100000),
												contractCall(theContract, ContractResources.ASSOCIATE_TOKEN).payingWith(theAccount).via("associateMethodCall"),
												cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
														.hasKnownStatus(SUCCESS),
												cryptoTransfer(moving(200, A_TOKEN).between(theAccount, TOKEN_TREASURY))
														.hasKnownStatus(SUCCESS)
										)
						)
				).when(
						contractCall(theContract, DISSOCIATE_TOKEN).payingWith(theAccount).via("dissociateMethodCall")
				).then(
						cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, theAccount))
								.hasKnownStatus(ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
				);
	}

	private HapiApiSpec transferSingleNft() {
		final var contract = "transferNftContract";

		return defaultHapiSpec("transferSingleNftHappyPath")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(ACCOUNT),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(ACCOUNT, NFT),
						mintToken(NFT, Collections.singletonList(metadata("memo"))),
						fileCreate("transferNftContractByteCode").payingWith(GENESIS),
						updateLargeFile(GENESIS, "transferNftContractByteCode", extractByteCode(ContractResources.TRANSFER_NFT_CONTRACT))
				)
				.when(withOpContext(
						(spec, opLog) -> allRunFor(
								spec,
								contractCreate(contract)
										.payingWith(GENESIS)
										.bytecode("transferNftContractByteCode")
										.via("transferSingleNftTxn")
										.gas(100000),
								contractCall(
										contract,
										ContractResources.TRANSFER_NFT,
										asAddress(spec.registry().getTokenID(NFT)),
										asAddress(spec.registry().getTokenID(TOKEN_TREASURY)),
										asAddress(spec.registry().getTokenID(ACCOUNT)),
										1L
								)
										.payingWith(GENESIS).via("transferSingleNftCall")
						)
				))
				.then(
						getTokenInfo(NFT).hasTotalSupply(1),
						getAccountInfo(ACCOUNT).hasOwnedNfts(1),
						getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 1)
				);
	}

	private HapiApiSpec transferMultipleNfts() {
		final var contract = "transferNftContract";

		return defaultHapiSpec("transferMultipleNftsHappyPath")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(ACCOUNT),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(ACCOUNT, NFT),
						mintToken(NFT, List.of(metadata("firstMemo"), metadata("secondMemo"))),
						fileCreate("transferNftContractByteCode").payingWith(GENESIS),
						updateLargeFile(GENESIS, "transferNftContractByteCode", extractByteCode(ContractResources.TRANSFER_NFT_CONTRACT))
				)
				.when(withOpContext(
						(spec, opLog) -> allRunFor(
								spec,
								contractCreate(contract)
										.payingWith(GENESIS)
										.bytecode("transferNftContractByteCode")
										.via("transferMultipleNftsTxn")
										.gas(100000),
								contractCall(
										contract,
										ContractResources.TRANSFER_NFTS,
										asAddress(spec.registry().getTokenID(NFT)),
										List.of(asAddress(spec.registry().getTokenID(TOKEN_TREASURY))),
										List.of(asAddress(spec.registry().getTokenID(ACCOUNT))),
										List.of(1L, 2L)
								)
										.payingWith(GENESIS).via("transferMultipleNftsCall")
						)
				))
				.then(
						getTokenInfo(NFT).hasTotalSupply(2),
						getAccountInfo(ACCOUNT).hasOwnedNfts(2),
						getAccountBalance(ACCOUNT).hasTokenBalance(NFT, 2)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
