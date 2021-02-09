package com.hedera.services.bdd.suites.token;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.assertions.BaseErroringAssertsProvider;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Granted;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;
import static org.junit.Assert.assertEquals;

public class TokenAssociationSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenAssociationSpecs.class);

	private static final String FREEZABLE_TOKEN_ON_BY_DEFAULT = "TokenA";
	private static final String FREEZABLE_TOKEN_OFF_BY_DEFAULT = "TokenB";
	private static final String KNOWABLE_TOKEN = "TokenC";
	private static final String VANILLA_TOKEN = "TokenD";

	public static void main(String... args) {
		new TokenAssociationSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						treasuryAssociationIsAutomatic(),
						dissociateHasExpectedSemantics(),
						accountInfoQueriesAsExpected(),
						contractInfoQueriesAsExpected(),
						associateHasExpectedSemantics(),
						associatedContractsMustHaveAdminKeys(),
						dissociateHasExpectedSemanticsForDeletedTokens(),
						expiredAndDeletedTokensStillAppearInContractInfo(),
						dissociationFromExpiredTokensAsExpected(),
				}
		);
	}

	public HapiApiSpec associatedContractsMustHaveAdminKeys() {
		String misc = "someToken";
		String contract = "defaultContract";

		return defaultHapiSpec("AssociatedContractsMustHaveAdminKeys")
				.given(
						tokenCreate(misc)
				).when(
						contractCreate(contract).omitAdminKey()
				).then(
						tokenAssociate(contract, misc).hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	public HapiApiSpec contractInfoQueriesAsExpected() {
		return defaultHapiSpec("ContractInfoQueriesAsExpected")
				.given(
						newKeyNamed("simple"),
						tokenCreate("a"),
						tokenCreate("b"),
						tokenCreate("c"),
						tokenCreate("tbd").adminKey("simple"),
						contractCreate("contract")
				).when(
						tokenAssociate("contract", "a", "b", "c", "tbd"),
						getContractInfo("contract")
								.hasToken(relationshipWith("a"))
								.hasToken(relationshipWith("b"))
								.hasToken(relationshipWith("c"))
								.hasToken(relationshipWith("tbd")),
						tokenDissociate("contract", "b"),
						tokenDelete("tbd")
				).then(
						getContractInfo("contract")
								.hasToken(relationshipWith("a"))
								.hasNoTokenRelationship("b")
								.hasToken(relationshipWith("c"))
								.hasToken(relationshipWith("tbd"))
								.logged()
				);
	}

	public HapiApiSpec accountInfoQueriesAsExpected() {
		return defaultHapiSpec("InfoQueriesAsExpected")
				.given(
						newKeyNamed("simple"),
						tokenCreate("a"),
						tokenCreate("b"),
						tokenCreate("c"),
						tokenCreate("tbd").adminKey("simple"),
						cryptoCreate("account").balance(0L)
				).when(
						tokenAssociate("account", "a", "b", "c", "tbd"),
						getAccountInfo("account")
								.hasToken(relationshipWith("a"))
								.hasToken(relationshipWith("b"))
								.hasToken(relationshipWith("c"))
								.hasToken(relationshipWith("tbd")),
						tokenDissociate("account", "b"),
						tokenDelete("tbd")
				).then(
						getAccountInfo("account")
								.hasToken(relationshipWith("a"))
								.hasNoTokenRelationship("b")
								.hasToken(relationshipWith("c"))
								.hasToken(relationshipWith("tbd"))
								.logged()
				);
	}

	public HapiApiSpec expiredAndDeletedTokensStillAppearInContractInfo() {
		String contract = "nothingMattersAnymore";
		String treasury = "something";
		String expiringToken = "expiringToken";
		long lifetimeSecs = 10;
		long xfer = 123L;
		AtomicLong now = new AtomicLong();
		return defaultHapiSpec("ExpiredAndDeletedTokensStillAppearInContractInfo")
				.given(
						newKeyNamed("admin"),
						cryptoCreate(treasury),
						fileCreate("bytecode").path(ContractResources.FUSE_BYTECODE_PATH),
						contractCreate(contract).bytecode("bytecode").via("creation"),
						withOpContext((spec, opLog) -> {
							var subOp = getTxnRecord("creation");
							allRunFor(spec, subOp);
							var record = subOp.getResponseRecord();
							now.set(record.getConsensusTimestamp().getSeconds());
						}),
						sourcing(() ->
								tokenCreate(expiringToken)
										.adminKey("admin")
										.treasury(treasury)
										.expiry(now.get() + lifetimeSecs))
				).when(
						tokenAssociate(contract, expiringToken),
						cryptoTransfer(
								moving(xfer, expiringToken)
										.between(treasury, contract))
				).then(
						getAccountBalance(contract)
								.hasTokenBalance(expiringToken, xfer),
						getContractInfo(contract)
								.hasToken(relationshipWith(expiringToken)
										.freeze(FreezeNotApplicable)),
						sleepFor(lifetimeSecs * 1_000L),
						getAccountBalance(contract)
								.hasTokenBalance(expiringToken, xfer),
						getContractInfo(contract)
								.hasToken(relationshipWith(expiringToken)
										.freeze(FreezeNotApplicable)),
						tokenDelete(expiringToken),
						getAccountBalance(contract)
								.hasTokenBalance(expiringToken, xfer),
						getContractInfo(contract)
								.hasToken(relationshipWith(expiringToken)
										.freeze(FreezeNotApplicable))
				);
	}

	public HapiApiSpec dissociationFromExpiredTokensAsExpected() {
		String treasury = "accountA";
		String frozenAccount = "frozen";
		String unfrozenAccount = "unfrozen";
		String expiringToken = "expiringToken";
		long lifetimeSecs = 10;

		AtomicLong now = new AtomicLong();
		return defaultHapiSpec("DissociationFromExpiredTokensAsExpected")
				.given(
						newKeyNamed("freezeKey"),
						cryptoCreate(treasury),
						cryptoCreate(frozenAccount).via("creation"),
						cryptoCreate(unfrozenAccount).via("creation"),
						withOpContext((spec, opLog) -> {
							var subOp = getTxnRecord("creation");
							allRunFor(spec, subOp);
							var record = subOp.getResponseRecord();
							now.set(record.getConsensusTimestamp().getSeconds());
						}),
						sourcing(() ->
								tokenCreate(expiringToken)
										.freezeKey("freezeKey")
										.freezeDefault(true)
										.treasury(treasury)
										.initialSupply(1000L)
										.expiry(now.get() + lifetimeSecs))
				).when(
						tokenAssociate(unfrozenAccount, expiringToken),
						tokenAssociate(frozenAccount, expiringToken),
						tokenUnfreeze(expiringToken, unfrozenAccount),
						cryptoTransfer(
								moving(100L, expiringToken)
										.between(treasury, unfrozenAccount))
				).then(
						getAccountBalance(treasury)
								.hasTokenBalance(expiringToken, 900L),
						sleepFor(lifetimeSecs * 1_000L),
						tokenDissociate(treasury, expiringToken)
								.hasKnownStatus(ACCOUNT_IS_TREASURY),
						tokenDissociate(unfrozenAccount, expiringToken)
								.via("dissociateTxn"),
						getTxnRecord("dissociateTxn")
								.hasPriority(TransactionRecordAsserts
										.recordWith().tokenTransfers(new BaseErroringAssertsProvider<>() {
											@Override
											public ErroringAsserts<List<TokenTransferList>> assertsFor(
													HapiApiSpec spec) {
												return tokenXfers -> {
													try {
														assertEquals(
																"Wrong number of tokens transferred!",
																1,
																tokenXfers.size());
														TokenTransferList xfers = tokenXfers.get(0);
														assertEquals("Wrong token transferred!",
																spec.registry().getTokenID(expiringToken),
																xfers.getToken());
														AccountAmount toTreasury = xfers.getTransfers(0);
														assertEquals("Treasury should come first!",
																spec.registry().getAccountID(treasury),
																toTreasury.getAccountID());
														assertEquals("Treasury should get 100 tokens back!",
																100L,
																toTreasury.getAmount());
														AccountAmount fromAccount = xfers.getTransfers(1);
														assertEquals("Account should come second!",
																spec.registry().getAccountID(unfrozenAccount),
																fromAccount.getAccountID());
														assertEquals("Account should send 100 tokens back!",
																-100L,
																fromAccount.getAmount());
													} catch (Throwable error) {
														return List.of(error);
													}
													return Collections.emptyList();
												};
											}
										})),
						getAccountBalance(treasury)
								.hasTokenBalance(expiringToken, 1000L),
						getAccountInfo(frozenAccount)
								.hasToken(relationshipWith(expiringToken)
										.freeze(Frozen)),
						tokenDissociate(frozenAccount, expiringToken)
								.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN)
				);
	}

	public HapiApiSpec dissociateHasExpectedSemanticsForDeletedTokens() {
		String tbdToken = "ToBeDeleted";
		String zeroBalanceFrozen = "0bFrozen";
		String zeroBalanceUnfrozen = "0bUnfrozen";
		String nonZeroBalanceFrozen = "1bFrozen";
		String nonZeroBalanceUnfrozen = "1bUnfrozen";
		long initialSupply = 100L;
		long nonZeroXfer = 10L;

		return defaultHapiSpec("DissociateHasExpectedSemanticsForDeletedTokens")
				.given(
						newKeyNamed("adminKey"),
						newKeyNamed("freezeKey"),
						cryptoCreate(TOKEN_TREASURY).balance(0L),
						tokenCreate(tbdToken)
								.adminKey("adminKey")
								.initialSupply(initialSupply)
								.treasury(TOKEN_TREASURY)
								.freezeKey("freezeKey")
								.freezeDefault(true),
						cryptoCreate(zeroBalanceFrozen).balance(0L),
						cryptoCreate(zeroBalanceUnfrozen).balance(0L),
						cryptoCreate(nonZeroBalanceFrozen).balance(0L),
						cryptoCreate(nonZeroBalanceUnfrozen).balance(0L)
				).when(
						tokenAssociate(zeroBalanceFrozen, tbdToken),
						tokenAssociate(zeroBalanceUnfrozen, tbdToken),
						tokenAssociate(nonZeroBalanceFrozen, tbdToken),
						tokenAssociate(nonZeroBalanceUnfrozen, tbdToken),

						tokenUnfreeze(tbdToken, zeroBalanceUnfrozen),
						tokenUnfreeze(tbdToken, nonZeroBalanceUnfrozen),
						tokenUnfreeze(tbdToken, nonZeroBalanceFrozen),

						cryptoTransfer(moving(nonZeroXfer, tbdToken).between(TOKEN_TREASURY, nonZeroBalanceFrozen)),
						cryptoTransfer(moving(nonZeroXfer, tbdToken).between(TOKEN_TREASURY, nonZeroBalanceUnfrozen)),

						tokenFreeze(tbdToken, nonZeroBalanceFrozen),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(tbdToken, initialSupply - 2 * nonZeroXfer),
						tokenDelete(tbdToken)
				).then(
						tokenDissociate(zeroBalanceFrozen, tbdToken),
						tokenDissociate(zeroBalanceUnfrozen, tbdToken),
						tokenDissociate(nonZeroBalanceFrozen, tbdToken),
						tokenDissociate(nonZeroBalanceUnfrozen, tbdToken),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(tbdToken, initialSupply - 2 * nonZeroXfer)
				);
	}

	public HapiApiSpec dissociateHasExpectedSemantics() {
		return defaultHapiSpec("DissociateHasExpectedSemantics")
				.given(flattened(
						basicKeysAndTokens()
				)).when(
						tokenCreate("tkn1")
								.treasury(TOKEN_TREASURY),
						tokenDissociate(TOKEN_TREASURY, "tkn1")
								.hasKnownStatus(ACCOUNT_IS_TREASURY),
						cryptoCreate("misc"),
						tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN),
						tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
						tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, "misc"),
						cryptoTransfer(
								moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
										.between(TOKEN_TREASURY, "misc")),
						tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
						cryptoTransfer(
								moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
										.between("misc", TOKEN_TREASURY)),
						tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
				).then(
						getAccountInfo("misc")
								.hasToken(relationshipWith(KNOWABLE_TOKEN))
								.hasNoTokenRelationship(FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.logged()
				);
	}

	public HapiApiSpec associateHasExpectedSemantics() {
		return defaultHapiSpec("AssociateHasExpectedSemantics")
				.given(flattened(
						basicKeysAndTokens()
				)).when(
						cryptoCreate("misc").balance(0L),
						tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT),
						tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.hasKnownStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT),
						tokenAssociate("misc", "1.2.3")
								.hasKnownStatus(INVALID_TOKEN_ID),
						tokenAssociate("misc", "1.2.3", "1.2.3")
								.hasPrecheck(TOKEN_ID_REPEATED_IN_TOKEN_LIST),
						tokenDissociate("misc", "1.2.3", "1.2.3")
								.hasPrecheck(TOKEN_ID_REPEATED_IN_TOKEN_LIST),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("tokens.maxPerAccount", "" + 1)),
						tokenAssociate("misc", FREEZABLE_TOKEN_OFF_BY_DEFAULT)
								.hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxPerAccount", "" + 1000
						)).payingWith(ADDRESS_BOOK_CONTROL),
						tokenAssociate("misc", FREEZABLE_TOKEN_OFF_BY_DEFAULT),
						tokenAssociate("misc", KNOWABLE_TOKEN, VANILLA_TOKEN)
				).then(
						getAccountInfo("misc")
								.hasToken(
										relationshipWith(FREEZABLE_TOKEN_ON_BY_DEFAULT)
												.kyc(KycNotApplicable)
												.freeze(Frozen))
								.hasToken(
										relationshipWith(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
												.kyc(KycNotApplicable)
												.freeze(Unfrozen))
								.hasToken(
										relationshipWith(KNOWABLE_TOKEN)
												.kyc(Revoked)
												.freeze(FreezeNotApplicable))
								.hasToken(
										relationshipWith(VANILLA_TOKEN)
												.kyc(KycNotApplicable)
												.freeze(FreezeNotApplicable))
								.logged()
				);
	}

	public HapiApiSpec treasuryAssociationIsAutomatic() {
		return defaultHapiSpec("TreasuryAssociationIsAutomatic")
				.given(
						basicKeysAndTokens()
				).when().then(
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(
										relationshipWith(FREEZABLE_TOKEN_ON_BY_DEFAULT)
												.kyc(KycNotApplicable)
												.freeze(Unfrozen))
								.hasToken(
										relationshipWith(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
												.kyc(KycNotApplicable)
												.freeze(Unfrozen))
								.hasToken(
										relationshipWith(KNOWABLE_TOKEN)
												.kyc(Granted)
												.freeze(FreezeNotApplicable))
								.hasToken(
										relationshipWith(VANILLA_TOKEN)
												.kyc(KycNotApplicable)
												.freeze(FreezeNotApplicable))
								.logged()
				);
	}

	private HapiSpecOperation[] basicKeysAndTokens() {
		return new HapiSpecOperation[] {
				newKeyNamed("kycKey"),
				newKeyNamed("freezeKey"),
				cryptoCreate(TOKEN_TREASURY).balance(0L),
				tokenCreate(FREEZABLE_TOKEN_ON_BY_DEFAULT)
						.treasury(TOKEN_TREASURY)
						.freezeKey("freezeKey")
						.freezeDefault(true),
				tokenCreate(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
						.treasury(TOKEN_TREASURY)
						.freezeKey("freezeKey")
						.freezeDefault(false),
				tokenCreate(KNOWABLE_TOKEN)
						.treasury(TOKEN_TREASURY)
						.kycKey("kycKey"),
				tokenCreate(VANILLA_TOKEN)
						.treasury(TOKEN_TREASURY)
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
