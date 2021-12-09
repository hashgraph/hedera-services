package com.hedera.services.bdd.suites.contract.precompile;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *
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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DISSOCIATE_TOKEN;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class DissociatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DissociatePrecompileSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new DissociatePrecompileSuite().runSuiteAsync();
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
				dissociatePrecompileWithSigsForFungibleWorks(),
				dissociatePrecompileWitContractIdSigForFungibleWorks(),
				dissociatePrecompileWithSigsForNFTWorks(),
				dissociatePrecompileWitContractIdSigForNFTWorks()
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
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
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

	public HapiApiSpec dissociatePrecompileWitContractIdSigForFungibleWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var contractKeyShape = DELEGATE_CONTRACT;
		final var contractKey = "meaning";
		final var multiKey = "purpose";

		AtomicReference<AccountID> accountID = new AtomicReference<>();
		AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();

		AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();
		return defaultHapiSpec("dissociatePrecompileWitContractIdSigForFungibleWorks")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed(multiKey),
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
								.adminKey(multiKey)
								.supplyKey(multiKey)
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
								.adminKey(multiKey)
								.supplyKey(multiKey)
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
												newKeyNamed(contractKey)
														.shape(contractKeyShape.signedWith("AssociateDissociate")),
												tokenUpdate("tkn1")
														.supplyKey(contractKey),
												getTokenInfo("tkn1").logged(),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												tokenUpdate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_ON_BY_DEFAULT).logged(),
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

	public HapiApiSpec dissociatePrecompileWithSigsForNFTWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var multiKey = "purpose";

		AtomicReference<AccountID> accountID = new AtomicReference<>();
		AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();

		AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();
		return defaultHapiSpec("dissociatePrecompileWithSigsForNFTWorks")
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
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						mintToken(FREEZABLE_TOKEN_ON_BY_DEFAULT, List.of(metadata("memo"))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
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
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
														.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.alsoSigningWithFullPrefix(multiKey)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
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

	public HapiApiSpec dissociatePrecompileWitContractIdSigForNFTWorks() {
		final var theAccount = "anybody";
		final var theContract = "associateDissociateContract";
		final var contractKeyShape = DELEGATE_CONTRACT;
		final var contractKey = "meaning";
		final var multiKey = "purpose";

		AtomicReference<AccountID> accountID = new AtomicReference<>();
		AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		AtomicReference<TokenID> freezeKeyOnTokenID = new AtomicReference<>();

		AtomicReference<TokenID> tk1TokenID = new AtomicReference<>();
		return defaultHapiSpec("dissociatePrecompileWitContractIdSigForNFTWorks")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed(multiKey),
						fileCreate(theContract)
								.path(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT),
						cryptoCreate(theAccount)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(multiKey)
								.supplyKey(multiKey)
								.initialSupply(0)
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> freezeKeyOnTokenID.set(asToken(id))),
						mintToken(FREEZABLE_TOKEN_ON_BY_DEFAULT, List.of(metadata("memo"))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.kycKey("kycKey"),
						tokenCreate("tkn1")
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.adminKey(multiKey)
								.supplyKey(multiKey)
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
												newKeyNamed(contractKey)
														.shape(contractKeyShape.signedWith("AssociateDissociate")),
												tokenUpdate("tkn1")
														.supplyKey(contractKey),
												getTokenInfo("tkn1").logged(),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(treasuryID.get()), asAddress(tk1TokenID.get()))
														.payingWith(theAccount)
														.via("tk1Txn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tk1Txn").andAllChildRecords().logged(),
												tokenUpdate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
														.supplyKey(contractKey),
												getTokenInfo(FREEZABLE_TOKEN_ON_BY_DEFAULT).logged(),
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
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
														.between(TOKEN_TREASURY, theAccount)),
												contractCall("AssociateDissociate", DISSOCIATE_TOKEN,
														asAddress(accountID.get()), asAddress(freezeKeyOnTokenID.get()))
														.payingWith(theAccount)
														.via("freezeKeyOnTxn2")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("freezeKeyOnTxn2").andAllChildRecords().logged(),
												cryptoTransfer(movingUnique(FREEZABLE_TOKEN_ON_BY_DEFAULT, 1)
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
