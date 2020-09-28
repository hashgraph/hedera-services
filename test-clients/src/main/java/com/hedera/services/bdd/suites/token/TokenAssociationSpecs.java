package com.hedera.services.bdd.suites.token;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenTransact;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Granted;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;

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
						associateHasExpectedSemantics(),
						dissociateHasExpectedSemantics(),
				}
		);
	}

	public HapiApiSpec dissociateHasExpectedSemantics() {
		return defaultHapiSpec("DissociateHasExpectedSemantics")
				.given(flattened(
						basicKeysAndTokens(),
						cryptoCreate("payer")
				)).when(
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate("tkn1")
								.treasury(TOKEN_TREASURY),
						tokenDissociate(TOKEN_TREASURY, "tkn1")
								.hasKnownStatus(ACCOUNT_IS_TREASURY),
						cryptoCreate("misc"),
						tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.payingWith("misc")
								.hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
						tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT, KNOWABLE_TOKEN)
								.payingWith("payer"),
						tokenUnfreeze(FREEZABLE_TOKEN_ON_BY_DEFAULT, "misc"),
						tokenTransact(
								moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
										.between(TOKEN_TREASURY, "misc")),
						tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.payingWith("misc")
								.hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
						tokenTransact(
								moving(1, FREEZABLE_TOKEN_ON_BY_DEFAULT)
										.between("misc", TOKEN_TREASURY)),
						tokenDissociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.payingWith("misc")
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
						basicKeysAndTokens(),
						cryptoCreate("payer")
				)).when(
						cryptoCreate("misc"),
						tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.payingWith("payer"),
						tokenAssociate("misc", FREEZABLE_TOKEN_ON_BY_DEFAULT)
								.payingWith("payer")
								.hasKnownStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT),
						tokenAssociate("misc", "1.2.3")
								.hasKnownStatus(INVALID_TOKEN_ID),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxPerAccount", "" + 1
						)).payingWith(ADDRESS_BOOK_CONTROL),
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
				cryptoCreate(TOKEN_TREASURY),
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
