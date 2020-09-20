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
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
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
				}
		);
	}

	public HapiApiSpec treasuryAssociationIsAutomatic() {
		return defaultHapiSpec("TreasuryAssociationIsAutomatic")
				.given(
						newKeyNamed("kycKey"),
						newKeyNamed("freezeKey")
				).when(
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
				).then(
						getAccountInfo(TOKEN_TREASURY)
								.hasToken(
										relationshipWith(FREEZABLE_TOKEN_ON_BY_DEFAULT)
												.balance(0)
												.kyc(KycNotApplicable)
												.freeze(Frozen))
								.hasToken(
										relationshipWith(FREEZABLE_TOKEN_OFF_BY_DEFAULT)
												.balance(0)
												.kyc(KycNotApplicable)
												.freeze(Unfrozen))
								.hasToken(
										relationshipWith(KNOWABLE_TOKEN)
												.balance(0)
												.kyc(Revoked)
												.freeze(FreezeNotApplicable))
								.hasToken(
										relationshipWith(VANILLA_TOKEN)
												.balance(0)
												.kyc(KycNotApplicable)
												.freeze(FreezeNotApplicable))
								.logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
