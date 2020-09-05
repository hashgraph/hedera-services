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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenTransact;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

public class TokenMgmtSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenMgmtSpecs.class);

	public static void main(String... args) {
		new TokenMgmtSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						freezeMgmtFailureCasesWork(),
						freezeMgmtSuccessCasesWork(),
				}
		);
	}

	public HapiApiSpec freezeMgmtFailureCasesWork() {
		var unfreezableToken = "without";
		var freezableToken = "withPlusDefaultTrue";

		return defaultHapiSpec("FreezeMgmtFailureCasesWork")
				.given(
						newKeyNamed("oneFreeze"),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(unfreezableToken)
								.symbol(salted("nope"))
								.treasury(TOKEN_TREASURY),
						tokenCreate(freezableToken)
								.symbol(salted("yesTrue"))
								.freezeDefault(true)
								.freezeKey("oneFreeze")
								.treasury(TOKEN_TREASURY)
				).when(
						tokenFreeze(unfreezableToken, TOKEN_TREASURY)
								.signedBy(GENESIS)
								.hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY),
						tokenFreeze(freezableToken, "1.2.3")
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						tokenFreeze(freezableToken, TOKEN_TREASURY)
								.signedBy(GENESIS)
								.hasKnownStatus(INVALID_SIGNATURE),
						tokenUnfreeze(unfreezableToken, TOKEN_TREASURY)
								.signedBy(GENESIS)
								.hasKnownStatus(TOKEN_HAS_NO_FREEZE_KEY),
						tokenUnfreeze(freezableToken, "1.2.3")
								.hasKnownStatus(INVALID_ACCOUNT_ID),
						tokenUnfreeze(freezableToken, TOKEN_TREASURY)
								.signedBy(GENESIS)
								.hasKnownStatus(INVALID_SIGNATURE)
				).then(
						getTokenInfo(unfreezableToken)
								.hasRegisteredId(unfreezableToken)
								.hasRegisteredSymbol(unfreezableToken)
								.logged()
				);
	}

	public HapiApiSpec freezeMgmtSuccessCasesWork() {
		var withPlusDefaultTrue = "withPlusDefaultTrue";
		var withPlusDefaultFalse = "withPlusDefaultFalse";

		return defaultHapiSpec("FreezeMgmtSuccessCasesWork")
				.given(
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate("misc"),
						newKeyNamed("oneFreeze"),
						newKeyNamed("twoFreeze"),
						tokenCreate(withPlusDefaultTrue)
								.symbol(salted("yesTrue"))
								.freezeDefault(true)
								.freezeKey("oneFreeze")
								.treasury(TOKEN_TREASURY),
						tokenCreate(withPlusDefaultFalse)
								.symbol(salted("yesFalse"))
								.freezeDefault(false)
								.freezeKey("twoFreeze")
								.treasury(TOKEN_TREASURY)
				).when(
						tokenTransact(
								moving(1, withPlusDefaultFalse)
										.between(TOKEN_TREASURY, "misc")),
						tokenFreeze(withPlusDefaultFalse, "misc"),
						tokenTransact(
								moving(1, withPlusDefaultFalse)
										.between(TOKEN_TREASURY, "misc"))
								.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
						getAccountInfo("misc").logged(),
						tokenUnfreeze(withPlusDefaultFalse, "misc"),
						tokenTransact(
								moving(1, withPlusDefaultFalse)
										.between(TOKEN_TREASURY, "misc"))
				).then(
						getAccountInfo("misc").logged()
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
