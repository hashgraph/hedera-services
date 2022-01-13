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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.DELEGATE_DISSOCIATE_CALL_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MULTIPLE_TOKENS_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.NESTED_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SINGLE_TOKEN_DISSOCIATE;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.STATIC_DISSOCIATE_CALL_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.FREEZABLE_TOKEN_ON_BY_DEFAULT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.TBD_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class DissociatePrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DissociatePrecompileSuite.class);
	private static final long TOTAL_SUPPLY = 1_000;
	private static final KeyShape CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, CONTRACT);
	private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE = KeyShape.threshOf(1, SIMPLE, DELEGATE_CONTRACT);
	private static final String TOKEN_TREASURY = "treasury";
	private static final String OUTER_CONTRACT = "Nested Associate/Dissociate Contract";
	private static final String INNER_CONTRACT = "Associate/Dissociate Contract";
	private static final String THE_CONTRACT = "Associate/Dissociate Contract";
	private static final String ACCOUNT = "anybody";
	private static final String FROZEN_TOKEN = "Frozen token";
	private static final String KYC_TOKEN = "KYC token";
	private static final String CONTRACT_KEY = "Contract key";
	private static final String DELEGATE_KEY = "Delegate key";
	private static final String FREEZE_KEY = "Freeze key";
	private static final String KYC_KEY = "KYC key";
	private static final String MULTI_KEY = "Multi key";

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
		return List.of(
				staticCallForDissociatePrecompileFails(),
				delegateCallForDissociatePrecompileSignedWithContractKeyFails()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return List.of(
				dissociatePrecompileWithDelegateContractKeyForFungibleVanilla(),
				dissociatePrecompileWithDelegateContractKeyForFungibleFrozen(),
				dissociatePrecompileWithDelegateContractKeyForFungibleWithKYC(),
				dissociatePrecompileWithDelegateContractKeyForNonFungibleVanilla(),
				dissociatePrecompileWithDelegateContractKeyForNonFungibleFrozen(),
				dissociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC(),
				dissociatePrecompileHasExpectedSemanticsForDeletedTokens(),
				nestedDissociateWorksAsExpected(),
				multiplePrecompileDissociationWithSigsForFungibleWorks(),
				delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks()
		);
	}

	/* -- HSCS-KEY-4 from HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForFungibleVanilla")
				.given(
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("tokenDissociateFromTreasuryFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tokenDissociateFromTreasuryFailedTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("tokenDissociateWithDelegateContractKeyFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("tokenDissociateWithDelegateContractKeyFailedTxn").andAllChildRecords().logged(),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(TOKEN_TREASURY, ACCOUNT)),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn").andAllChildRecords().logged(),
												cryptoTransfer(
														moving(1, VANILLA_TOKEN)
																.between(ACCOUNT, TOKEN_TREASURY)),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("tokenDissociateWithDelegateContractKeyHappyTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("tokenDissociateWithDelegateContractKeyHappyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("tokenDissociateFromTreasuryFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(ACCOUNT_IS_TREASURY)),
						childRecordsCheck("tokenDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
						childRecordsCheck("nonZeroTokenBalanceDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)),
						childRecordsCheck("tokenDissociateWithDelegateContractKeyHappyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN).logged()
				);
	}

	/* -- HSCS-KEY-4 from HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.freezeDefault(true)
								.freezeKey(FREEZE_KEY)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												tokenAssociate(ACCOUNT, FROZEN_TOKEN),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenTokenAssociateWithDelegateContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("frozenTokenAssociateWithDelegateContractKeyTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FROZEN_TOKEN, ACCOUNT),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("UnfrozenTokenAssociateWithDelegateContractKeyTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("UnfrozenTokenAssociateWithDelegateContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("frozenTokenAssociateWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(ACCOUNT_FROZEN_FOR_TOKEN)),
						childRecordsCheck("UnfrozenTokenAssociateWithDelegateContractKeyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(FROZEN_TOKEN).logged()
				);
	}

	/* -- HSCS-KEY-4 from HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(KYC_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycTokenDissociateWithDelegateContractKeyFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("kycTokenDissociateWithDelegateContractKeyFailedTxn").andAllChildRecords().logged(),
												tokenAssociate(ACCOUNT, KYC_TOKEN),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycTokenDissociateWithDelegateContractKeyHappyTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("kycTokenDissociateWithDelegateContractKeyHappyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("kycTokenDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
						childRecordsCheck("kycTokenDissociateWithDelegateContractKeyHappyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KYC_TOKEN).logged()
				);
	}

	/* -- HSCS-KEY-4 from HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForNonFungibleVanilla() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForNonFungibleVanilla")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.supplyKey(MULTI_KEY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						mintToken(VANILLA_TOKEN, List.of(metadata("memo")))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												cryptoUpdate(TOKEN_TREASURY).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("NFTDissociateFromTreasuryFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("NFTDissociateFromTreasuryFailedTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("NFTDissociateWithDelegateContractKeyFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("NFTDissociateWithDelegateContractKeyFailedTxn").andAllChildRecords().logged(),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												cryptoTransfer(movingUnique(VANILLA_TOKEN, 1)
														.between(TOKEN_TREASURY, ACCOUNT)),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("nonZeroNFTBalanceDissociateWithDelegateContractKeyFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("nonZeroNFTBalanceDissociateWithDelegateContractKeyFailedTxn").andAllChildRecords().logged(),
												cryptoTransfer(movingUnique(VANILLA_TOKEN, 1)
														.between(ACCOUNT, TOKEN_TREASURY)),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(GENESIS)
														.via("NFTDissociateWithDelegateContractKeyHappyTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("NFTDissociateWithDelegateContractKeyHappyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("NFTDissociateFromTreasuryFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(ACCOUNT_IS_TREASURY)),
						childRecordsCheck("NFTDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
						childRecordsCheck("nonZeroNFTBalanceDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(ACCOUNT_STILL_OWNS_NFTS)),
						childRecordsCheck("NFTDissociateWithDelegateContractKeyHappyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN).logged()
				);
	}

	/* -- HSCS-KEY-4 from HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForNonFungibleFrozen() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> frozenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForNonFungibleFrozen")
				.given(
						newKeyNamed(FREEZE_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(FROZEN_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.freezeDefault(true)
								.freezeKey(FREEZE_KEY)
								.exposingCreatedIdTo(id -> frozenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												tokenAssociate(ACCOUNT, FROZEN_TOKEN),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("frozenNFTAssociateWithDelegateContractKeyTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("frozenNFTAssociateWithDelegateContractKeyTxn").andAllChildRecords().logged(),
												tokenUnfreeze(FROZEN_TOKEN, ACCOUNT),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(frozenTokenID.get()))
														.payingWith(GENESIS)
														.via("UnfrozenNFTAssociateWithDelegateContractKeyTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("UnfrozenNFTAssociateWithDelegateContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("frozenNFTAssociateWithDelegateContractKeyTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(ACCOUNT_FROZEN_FOR_TOKEN)),
						childRecordsCheck("UnfrozenNFTAssociateWithDelegateContractKeyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(FROZEN_TOKEN).logged()
				);
	}

	/* -- HSCS-KEY-4 from HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<TokenID> kycTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileWithDelegateContractKeyForNonFungibleWithKYC")
				.given(
						newKeyNamed(KYC_KEY),
						cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY).exposingCreatedIdTo(treasuryID::set),
						tokenCreate(KYC_TOKEN)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.initialSupply(0)
								.kycKey(KYC_KEY)
								.exposingCreatedIdTo(id -> kycTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT).bytecode(THE_CONTRACT).gas(100_000),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, THE_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycNFTDissociateWithDelegateContractKeyFailedTxn")
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												getTxnRecord("kycNFTDissociateWithDelegateContractKeyFailedTxn").andAllChildRecords().logged(),
												tokenAssociate(ACCOUNT, KYC_TOKEN),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(kycTokenID.get()))
														.payingWith(GENESIS)
														.via("kycNFTDissociateWithDelegateContractKeyHappyTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("kycNFTDissociateWithDelegateContractKeyHappyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("kycNFTDissociateWithDelegateContractKeyFailedTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)),
						childRecordsCheck("kycNFTDissociateWithDelegateContractKeyHappyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KYC_TOKEN).logged()
				);
	}

	/* -- Not specifically required in the HTS Precompile Test Plan -- */
	public HapiApiSpec dissociatePrecompileHasExpectedSemanticsForDeletedTokens() {
		final String tbdUniqToken = "UniqToBeDeleted";
		final String zeroBalanceFrozen = "0bFrozen";
		final String zeroBalanceUnfrozen = "0bUnfrozen";
		final String nonZeroBalanceFrozen = "1bFrozen";
		final String nonZeroBalanceUnfrozen = "1bUnfrozen";
		final long initialSupply = 100L;
		final long nonZeroXfer = 10L;
		final var firstMeta = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
		final var secondMeta = ByteString.copyFrom("SECOND".getBytes(StandardCharsets.UTF_8));
		final var thirdMeta = ByteString.copyFrom("THIRD".getBytes(StandardCharsets.UTF_8));

		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();
		final AtomicReference<AccountID> zeroBalanceFrozenID = new AtomicReference<>();
		final AtomicReference<AccountID> zeroBalanceUnfrozenID = new AtomicReference<>();
		final AtomicReference<AccountID> nonZeroBalanceFrozenID = new AtomicReference<>();
		final AtomicReference<AccountID> nonZeroBalanceUnfrozenID = new AtomicReference<>();
		final AtomicReference<TokenID> tbdTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> tbdUniqueTokenID = new AtomicReference<>();

		return defaultHapiSpec("DissociatePrecompileHasExpectedSemanticsForDeletedTokens")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(TBD_TOKEN)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(initialSupply)
								.treasury(TOKEN_TREASURY)
								.freezeKey(MULTI_KEY)
								.freezeDefault(true)
								.exposingCreatedIdTo(id -> tbdTokenID.set(asToken(id))),
						tokenCreate(tbdUniqToken)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY)
								.initialSupply(0)
								.exposingCreatedIdTo(id -> tbdUniqueTokenID.set(asToken(id))),
						cryptoCreate(zeroBalanceFrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(zeroBalanceFrozenID::set),
						cryptoCreate(zeroBalanceUnfrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(zeroBalanceUnfrozenID::set),
						cryptoCreate(nonZeroBalanceFrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(nonZeroBalanceFrozenID::set),
						cryptoCreate(nonZeroBalanceUnfrozen)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(nonZeroBalanceUnfrozenID::set)
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												tokenAssociate(zeroBalanceFrozen, TBD_TOKEN),
												tokenAssociate(zeroBalanceUnfrozen, TBD_TOKEN),
												tokenAssociate(nonZeroBalanceFrozen, TBD_TOKEN),
												tokenAssociate(nonZeroBalanceUnfrozen, TBD_TOKEN),
												mintToken(tbdUniqToken, List.of(firstMeta, secondMeta, thirdMeta)),
												getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(3),
												tokenUnfreeze(TBD_TOKEN, zeroBalanceUnfrozen),
												tokenUnfreeze(TBD_TOKEN, nonZeroBalanceUnfrozen),
												tokenUnfreeze(TBD_TOKEN, nonZeroBalanceFrozen),

												cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceFrozen)),
												cryptoTransfer(moving(nonZeroXfer, TBD_TOKEN).between(TOKEN_TREASURY, nonZeroBalanceUnfrozen)),

												tokenFreeze(TBD_TOKEN, nonZeroBalanceFrozen),
												getAccountBalance(TOKEN_TREASURY)
														.hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer),
												tokenDelete(TBD_TOKEN),
												tokenDelete(tbdUniqToken),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(zeroBalanceFrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(zeroBalanceFrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateZeroBalanceFrozenTxn"),
												getTxnRecord("dissociateZeroBalanceFrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(zeroBalanceUnfrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(zeroBalanceUnfrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateZeroBalanceUnfrozenTxn"),
												getTxnRecord("dissociateZeroBalanceUnfrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(nonZeroBalanceFrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(nonZeroBalanceFrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateNonZeroBalanceFrozenTxn"),
												getTxnRecord("dissociateNonZeroBalanceFrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(nonZeroBalanceUnfrozenID.get()), asAddress(tbdTokenID.get()))
														.payingWith(nonZeroBalanceUnfrozen)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via("dissociateNonZeroBalanceUnfrozenTxn"),
												getTxnRecord("dissociateNonZeroBalanceUnfrozenTxn").andAllChildRecords().logged(),
												contractCall(THE_CONTRACT, SINGLE_TOKEN_DISSOCIATE,
														asAddress(treasuryID.get()), asAddress(tbdUniqueTokenID.get()))
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.payingWith(TOKEN_TREASURY)
										)
						)
				).then(
						childRecordsCheck("dissociateZeroBalanceFrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("dissociateZeroBalanceUnfrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("dissociateNonZeroBalanceFrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
						childRecordsCheck("dissociateNonZeroBalanceUnfrozenTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(zeroBalanceFrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(zeroBalanceUnfrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(nonZeroBalanceFrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(nonZeroBalanceUnfrozen).hasNoTokenRelationship(TBD_TOKEN),
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(relationshipWith(TBD_TOKEN))
								.hasNoTokenRelationship(tbdUniqToken)
								.hasOwnedNfts(0),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(TBD_TOKEN, initialSupply - 2 * nonZeroXfer)
				);
	}

	/* -- Not specifically required in the HTS Precompile Test Plan -- */
	private HapiApiSpec nestedDissociateWorksAsExpected() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

		return defaultHapiSpec("nestedDissociateWorksAsExpected")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(INNER_CONTRACT),
						updateLargeFile(ACCOUNT, INNER_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						fileCreate(OUTER_CONTRACT),
						updateLargeFile(ACCOUNT, OUTER_CONTRACT,
								extractByteCode(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT)),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
				)
				.when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(OUTER_CONTRACT, NESTED_TOKEN_DISSOCIATE,
														asAddress(accountID.get()), asAddress(vanillaTokenID.get()))
														.payingWith(ACCOUNT)
														.via("nestedDissociateTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS),
												getTxnRecord("nestedDissociateTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("nestedDissociateTxn", SUCCESS,
								recordWith().status(SUCCESS),
								recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}


	/* -- HSCS-PREC-007 from HTS Precompile Test Plan -- */
	public HapiApiSpec multiplePrecompileDissociationWithSigsForFungibleWorks() {
		final AtomicReference<TokenID> knowableTokenTokenID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<AccountID> treasuryID = new AtomicReference<>();

		return defaultHapiSpec("multiplePrecompileDissociationWithSigsForFungibleWorks")
				.given(
						cryptoCreate(ACCOUNT)
								.balance(10 * ONE_HUNDRED_HBARS)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(THE_CONTRACT),
						updateLargeFile(ACCOUNT, THE_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						cryptoCreate(TOKEN_TREASURY)
								.balance(0L)
								.exposingCreatedIdTo(treasuryID::set),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
						tokenCreate(KNOWABLE_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.initialSupply(TOTAL_SUPPLY)
								.exposingCreatedIdTo(id -> knowableTokenTokenID.set(asToken(id)))
				)
				.when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(THE_CONTRACT)
														.bytecode(THE_CONTRACT)
														.gas(100_000),
												tokenAssociate(ACCOUNT, List.of(VANILLA_TOKEN, KNOWABLE_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN)),
												getAccountInfo(ACCOUNT).hasToken(relationshipWith(KNOWABLE_TOKEN)),
												contractCall(THE_CONTRACT, MULTIPLE_TOKENS_DISSOCIATE,
														asAddress(accountID.get()), List.of(
																asAddress(vanillaTokenID.get()),
																asAddress(knowableTokenTokenID.get())))
														.payingWith(ACCOUNT)
														.via("multipleDissociationTxn")
														.hasKnownStatus(SUCCESS),
												getTxnRecord("multipleDissociationTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("multipleDissociationTxn", SUCCESS, recordWith().status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(KNOWABLE_TOKEN)
				);
	}

	/* -- HSCS-KEY-1 from HTS Precompile Test Plan -- */
	private HapiApiSpec delegateCallForDissociatePrecompileSignedWithContractKeyFails() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("DelegateCallForDissociatePrecompileSignedWithContractKeyFails")
				.given(
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(INNER_CONTRACT),
						updateLargeFile(ACCOUNT, INNER_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						fileCreate(OUTER_CONTRACT),
						updateLargeFile(ACCOUNT, OUTER_CONTRACT,
								extractByteCode(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT)),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												newKeyNamed(CONTRACT_KEY).shape(CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(CONTRACT_KEY),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCall(OUTER_CONTRACT, DELEGATE_DISSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("delegateDissociateCallWithContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(5_000_000),
												getTxnRecord("delegateDissociateCallWithContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("delegateDissociateCallWithContractKeyTxn", CONTRACT_REVERT_EXECUTED, recordWith()
								.status(INVALID_SIGNATURE)),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	/* -- HSCS-KEY-3 from HTS Precompile Test Plan -- */
	private HapiApiSpec delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("delegateCallForDissociatePrecompileSignedWithDelegateContractKeyWorks")
				.given(
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(INNER_CONTRACT),
						updateLargeFile(ACCOUNT, INNER_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						fileCreate(OUTER_CONTRACT),
						updateLargeFile(ACCOUNT, OUTER_CONTRACT,
								extractByteCode(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT)),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, OUTER_CONTRACT))),
												cryptoUpdate(ACCOUNT).key(DELEGATE_KEY),
												contractCall(OUTER_CONTRACT, DELEGATE_DISSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("delegateDissociateCallWithDelegateContractKeyTxn")
														.hasKnownStatus(ResponseCodeEnum.SUCCESS)
														.gas(5_000_000),
												getTxnRecord("delegateDissociateCallWithDelegateContractKeyTxn").andAllChildRecords().logged()
										)
						)
				).then(
						childRecordsCheck("delegateDissociateCallWithDelegateContractKeyTxn", SUCCESS, recordWith()
								.status(SUCCESS)),
						getAccountInfo(ACCOUNT).hasNoTokenRelationship(VANILLA_TOKEN)
				);
	}

	/* -- HSCS-KEY-2 from HTS Precompile Test Plan -- */
	private HapiApiSpec staticCallForDissociatePrecompileFails() {
		final AtomicReference<AccountID> accountID = new AtomicReference<>();
		final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

		return defaultHapiSpec("StaticCallForDissociatePrecompileFails")
				.given(
						cryptoCreate(ACCOUNT)
								.exposingCreatedIdTo(accountID::set),
						fileCreate(INNER_CONTRACT),
						updateLargeFile(ACCOUNT, INNER_CONTRACT,
								extractByteCode(ContractResources.ASSOCIATE_DISSOCIATE_CONTRACT)),
						fileCreate(OUTER_CONTRACT),
						updateLargeFile(ACCOUNT, OUTER_CONTRACT,
								extractByteCode(ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT)),
						contractCreate(INNER_CONTRACT)
								.bytecode(INNER_CONTRACT)
								.gas(100_000),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(VANILLA_TOKEN)
								.tokenType(FUNGIBLE_COMMON)
								.treasury(TOKEN_TREASURY)
								.exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id)))
				).when(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(ACCOUNT, VANILLA_TOKEN),
												contractCreate(OUTER_CONTRACT, ContractResources.NESTED_ASSOCIATE_DISSOCIATE_CONTRACT_CONSTRUCTOR,
														getNestedContractAddress(INNER_CONTRACT, spec))
														.bytecode(OUTER_CONTRACT)
														.gas(100_000),
												contractCall(OUTER_CONTRACT, STATIC_DISSOCIATE_CALL_ABI,
														asAddress(accountID.get()), asAddress(vanillaTokenTokenID.get()))
														.payingWith(GENESIS)
														.via("staticDissociateCallTxn")
														.hasKnownStatus(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED)
														.gas(5_000_000),
												getTxnRecord("staticDissociateCallTxn").andAllChildRecords().logged()
										)
						)
				).then(
						emptyChildRecordsCheck("staticDissociateCallTxn", CONTRACT_REVERT_EXECUTED),
						getAccountInfo(ACCOUNT).hasToken(relationshipWith(VANILLA_TOKEN))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	/* --- Helpers --- */

	private static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}

	@NotNull
	private String getNestedContractAddress(String outerContract, HapiApiSpec spec) {
		return CommonUtils.calculateSolidityAddress(
				(int) spec.registry().getContractId(outerContract).getShardNum(),
				spec.registry().getContractId(outerContract).getRealmNum(),
				spec.registry().getContractId(outerContract).getContractNum());
	}
}
