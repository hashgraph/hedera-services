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
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenTransact;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TokenDeleteSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenDeleteSpecs.class);

	public static void main(String... args) {
		new TokenDeleteSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						deletionValidatesRef(),
						deletionWorksAsExpected(),
						deletionValidatesMissingAdminKey(),
				}
		);
	}

	private HapiApiSpec deletionValidatesMissingAdminKey() {
		return defaultHapiSpec("DeletionValidatesMissingAdminKey")
				.given(
						newKeyNamed("multiKey"),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("payer")
								.balance(A_HUNDRED_HBARS),
						tokenCreate("tbd")
								.freezeDefault(false)
								.kycDefault(true)
								.treasury(TOKEN_TREASURY)
								.payingWith("payer")
				).when( ).then(
						tokenDelete("tbd")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(UNAUTHORIZED)
				);
	}

	public HapiApiSpec deletionWorksAsExpected() {
		return defaultHapiSpec("DeletionWorksAsExpected")
				.given(
						newKeyNamed("multiKey"),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("payer")
								.balance(A_HUNDRED_HBARS),
						tokenCreate("tbd")
								.adminKey("multiKey")
								.freezeKey("multiKey")
								.kycKey("multiKey")
								.wipeKey("multiKey")
								.supplyKey("multiKey")
								.freezeDefault(false)
								.kycDefault(true)
								.treasury(TOKEN_TREASURY)
								.payingWith("payer")
				).when(
						getAccountInfo(TOKEN_TREASURY).logged(),
						mintToken("tbd", 1),
						burnToken("tbd", 1),
						revokeTokenKyc("tbd", GENESIS),
						grantTokenKyc("tbd", GENESIS),
						tokenFreeze("tbd", GENESIS),
						tokenUnfreeze("tbd", GENESIS),
						tokenTransact(moving(1, "tbd")
								.between(TOKEN_TREASURY, GENESIS)),
						tokenDelete("tbd")
								.payingWith("payer")
				).then(
						getTokenInfo("tbd").logged(),
						getAccountInfo(TOKEN_TREASURY).logged(),
						tokenTransact(moving(1, "tbd")
								.between(TOKEN_TREASURY, GENESIS))
								.hasKnownStatus(TOKEN_WAS_DELETED),
						mintToken("tbd", 1)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						burnToken("tbd", 1)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						revokeTokenKyc("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						grantTokenKyc("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						tokenFreeze("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						tokenUnfreeze("tbd", GENESIS)
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	public HapiApiSpec deletionValidatesRef() {
		return defaultHapiSpec("DeletionValidatesRef")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS)
				).when( ).then(
						tokenDelete("1.2.3")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_REF)
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
