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
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.token.TokenAssociationSpecs;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ASSOCIATE_TOKEN;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DISSOCIATE_TOKEN;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ZENOS_BANK_DEPOSIT_TOKENS;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ZENOS_BANK_WITHDRAW_TOKENS;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class ContractHTSSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractHTSSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String A_TOKEN = "TokenA";
	private static final String TOKEN_TREASURY = "treasury";
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
				depositAndWithdraw(),
				associatePrecompileWithSignatureWorksWorksForFungible(),
				associatePrecompileWithoutSignatureWorksWorksForFungible(),
				dissociatePrecompileWithSigsForFungibleWorks(),
				dissociatePrecompileWithoutSigsForFungibleWorks()
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
						getTxnRecord("zeno").logged(),
						contractCall(theContract, ZENOS_BANK_WITHDRAW_TOKENS)
								.payingWith(theReceiver)
								.alsoSigningWithFullPrefix(theContract)
								.gas(70_000)
								.via("receiver"),
						getTxnRecord("receiver").logged()
				).then(
				);
	}

	private HapiApiSpec associatePrecompileWithSignatureWorksWorksForFungible() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		AtomicReference<AccountID> accountID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOffTokenID = new AtomicReference<>();
		AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWithSignatureWorksWorksForFungible")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> freezeKeyOffTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey")
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOffTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOffTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(knowableTokenTokenID.get()))
														.payingWith(theAccount)
														.via("knowableTokenTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("vanillaTokenTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(getAccountInfo(theAccount)
						.hasToken(
								relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT)
										.kyc(KycNotApplicable)
										.freeze(Frozen))
						.hasToken(
								relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT)
										.kyc(KycNotApplicable)
										.freeze(Unfrozen))
						.hasToken(
								relationshipWith(TokenAssociationSpecs.KNOWABLE_TOKEN)
										.kyc(Revoked)
										.freeze(FreezeNotApplicable))
						.hasToken(
								relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN)
										.kyc(KycNotApplicable)
										.freeze(FreezeNotApplicable))
						.logged()
				);
	}

	private HapiApiSpec associatePrecompileWithoutSignatureWorksWorksForFungible() {
		final var theAccount = "anybody";
		final var theContract = "AssociatePrecompileWithoutSignatureWorksWorksForFungible";

		AtomicReference<AccountID> accountID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOffTokenID = new AtomicReference<>();
		AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("associatePrecompileWorks")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeKey("freezeKey")
								.freezeDefault(false)
								.exposingCreatedIdTo(id -> freezeKeyOffTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey")
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id))),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOffTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOffTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("freezeKeyOffTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(knowableTokenTokenID.get()))
														.payingWith(theAccount)
														.via("knowableTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("knowableTokenTxn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", ASSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(theAccount)
														.via("vanillaTokenTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("vanillaTokenTxn").andAllChildRecords().logged()
										)
						)
				).then(getAccountInfo(theAccount)
						.hasToken(
								relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT)
										.kyc(KycNotApplicable)
										.freeze(Frozen))
						.hasToken(
								relationshipWith(TokenAssociationSpecs.FREEZABLE_TOKEN_OFF_BY_DEFAULT)
										.kyc(KycNotApplicable)
										.freeze(Unfrozen))
						.hasToken(
								relationshipWith(TokenAssociationSpecs.KNOWABLE_TOKEN)
										.kyc(Revoked)
										.freeze(FreezeNotApplicable))
						.hasToken(
								relationshipWith(TokenAssociationSpecs.VANILLA_TOKEN)
										.kyc(KycNotApplicable)
										.freeze(FreezeNotApplicable))
						.logged()
				);
	}

	private HapiApiSpec dissociateNonFungibleToken() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		return defaultHapiSpec("dissociateNFTHappyPath")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(theAccount, NFT),
						mintToken(NFT, List.of(metadata("memo"))),
						fileCreate("associateDissociateContractByteCode").payingWith(theAccount),
						updateLargeFile(theAccount, "associateDissociateContractByteCode",
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.payingWith(theAccount)
														.bytecode("associateDissociateContractByteCode")
														.via("associateTxn")
														.gas(100000),
												cryptoTransfer(TokenMovement.movingUnique(NFT, 1)
														.between(TOKEN_TREASURY, theAccount)).hasKnownStatus(SUCCESS),
												cryptoTransfer(TokenMovement.movingUnique(NFT, 1)
														.between(theAccount, TOKEN_TREASURY)).hasKnownStatus(SUCCESS)
										)
						)
				).when(
						contractCall(theContract, DISSOCIATE_TOKEN).payingWith(theAccount).via("dissociateMethodCall")
				).then(
						cryptoTransfer(TokenMovement.movingUnique(NFT, 1)
								.between(TOKEN_TREASURY, theAccount))
								.hasKnownStatus(ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						getAccountInfo(theAccount).hasNoTokenRelationship(NFT)
				);
	}

	private HapiApiSpec associateNonFungibleToken() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		return defaultHapiSpec("associateNFTHappyPath")
				.given(
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						mintToken(NFT, List.of(metadata("memo"))),
						fileCreate("associateDissociateContractByteCode").payingWith(theAccount),
						updateLargeFile(theAccount, "associateDissociateContractByteCode",
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(theContract)
														.payingWith(theAccount)
														.bytecode("associateDissociateContractByteCode")
														.via("associateTxn")
														.gas(100000),
												cryptoTransfer(TokenMovement.movingUnique(NFT, 1)
														.between(TOKEN_TREASURY, theAccount))
														.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
										)
						)
				).when(
						contractCall(theContract, ContractResources.ASSOCIATE_TOKEN).payingWith(theAccount).via("associateMethodCall")
				).then(
						cryptoTransfer(TokenMovement.movingUnique(NFT, 1)
								.between(TOKEN_TREASURY, theAccount)).hasKnownStatus(SUCCESS),
						getAccountInfo(theAccount).hasOwnedNfts(1),
						getAccountBalance(theAccount).hasTokenBalance(NFT, 1)
				);
	}

	public HapiApiSpec dissociatePrecompileWithSigsForFungibleWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		AtomicReference<AccountID> accountID = new AtomicReference<>();
		AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();

		AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();
		return defaultHapiSpec("DissociatePrecompileWithSigsForFungibleWorks")
				.given(
						newKeyNamed(multiKey),
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> tk1TokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.alsoSigningWithFullPrefix(multiKey, TOKEN_TREASURY)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeOnTxn").andAllChildRecords().logged(),
												tokenAssociate(theAccount, FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, theAccount),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(theAccount, TOKEN_TREASURY)),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn3")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(SUCCESS),
												getTxnRecord("freezeKeyOnTxn3").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
								.hasToken(relationshipWith(KNOWABLE_TOKEN))
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.logged()
				);
	}

	public HapiApiSpec dissociatePrecompileWithoutSigsForFungibleWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";

		AtomicReference<AccountID> accountID = new AtomicReference<>();
		AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();

		AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();
		return defaultHapiSpec("DissociatePrecompileWithoutSigsForFungibleWorks")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> tk1TokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate("AssociateDissociate")
														.bytecode(theContract)
														.gas(100_000),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeOnTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeOnTxn").andAllChildRecords().logged(),
												tokenAssociate(theAccount, FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, theAccount),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(
														moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
																.between(theAccount, TOKEN_TREASURY)),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn3")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("freezeKeyOnTxn3").andAllChildRecords().logged()
										)
						)
				).then(
						getAccountInfo(theAccount)
								.hasToken(relationshipWith(KNOWABLE_TOKEN))
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}
}
