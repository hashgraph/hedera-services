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
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenTransact;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenTransact.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TokenTransactSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenTransactSpecs.class);

	private static final long TOTAL_SUPPLY = 1_000;
	private static final String A_TOKEN = "TokenA";
	private static final String B_TOKEN = "TokenB";
	private static final String FIRST_USER = "Client1";
	private static final String SECOND_USER = "Client2";
	private static final String TOKEN_TREASURY = "treasury";

	public static void main(String... args) {
		new TokenTransactSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						balancesChangeOnTokenTransfer(),
						txnsAreAtomic(),
						accountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue(),
						senderSigsAreChecked(),
						senderSigsAreValid(),
						balancesAreChecked(),
						nonZeroNetTransfersRejected(),
				}
		);
	}
	public HapiApiSpec balancesAreChecked() {
		return defaultHapiSpec("BalancesAreChecked")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(100)
								.treasury("firstTreasury")
				).then(
						tokenTransact(
								moving(100_000_000_000_000L, A_TOKEN)
										.between("firstTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury")
								.hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE)
				);
	}

	public HapiApiSpec accountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue() {
		return defaultHapiSpec("AccountsMustBeExplicitlyUnfrozenOnlyIfDefaultFreezeIsTrue")
				.given(
						cryptoCreate("randomBeneficiary"),
						cryptoCreate("treasury"),
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						newKeyNamed("freezeKey")
				).when(
						tokenCreate(A_TOKEN)
								.treasury("treasury")
								.freezeKey("freezeKey")
								.freezeDefault(true),
						tokenTransact(
								moving(100, A_TOKEN)
										.between("treasury", "randomBeneficiary")
						).hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
						/* and */
						tokenCreate(B_TOKEN)
								.treasury("treasury")
								.freezeDefault(false),
						tokenTransact(
								moving(100, B_TOKEN)
										.between("treasury", "randomBeneficiary")
						).payingWith("payer").via("successfulTransfer")
				).then(
						getAccountBalance("randomBeneficiary")
								.logged()
								.hasTokenBalance(B_TOKEN, 100),
						getTxnRecord("successfulTransfer").logged()
				);
	}

	public HapiApiSpec senderSigsAreChecked() {
		return defaultHapiSpec("SenderSigsAreChecked")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialSupply(234)
								.treasury("secondTreasury")
				).then(
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury")
								.hasKnownStatus(INVALID_SIGNATURE)
				);
	}

	public HapiApiSpec senderSigsAreValid() {
		return defaultHapiSpec("SenderSigsAreValid")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialSupply(234)
								.treasury("secondTreasury")
				).then(
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary")
						).payingWith("payer")
								.signedBy("firstTreasury","payer")
								.hasKnownStatus(SUCCESS)
				);
	}

	public HapiApiSpec txnsAreAtomic() {
		final int MONOGAMOUS_NETWORK = 1;
		final int ADVENTUROUS_NETWORK = 1_000;
		return defaultHapiSpec("TxnsAreAtomic")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("secondTreasury"),
						cryptoCreate("beneficiary")
				).when(
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxPerAccount", "" + MONOGAMOUS_NETWORK
						)),
						tokenCreate(A_TOKEN)
								.initialSupply(123)
								.treasury("firstTreasury"),
						tokenCreate(B_TOKEN)
								.initialSupply(234)
								.treasury("secondTreasury"),
						tokenTransact(
								moving(100, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(100, B_TOKEN).between("secondTreasury", "beneficiary")
						).hasKnownStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
				).then(
						getAccountBalance("firstTreasury")
								.logged()
								.hasTokenBalance(A_TOKEN, 123),
						getAccountBalance("secondTreasury")
								.logged()
								.hasTokenBalance(B_TOKEN, 234),
						getAccountBalance("beneficiary").logged(),
						/* Cleanup */
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK
						))
				);
	}

	public HapiApiSpec nonZeroNetTransfersRejected() {
		return defaultHapiSpec("NonZeroNetTransfersRejected")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						cryptoCreate("firstTreasury"),
						cryptoCreate("beneficiary")
				).when(
						tokenCreate(A_TOKEN)
								.initialSupply(100)
								.treasury("firstTreasury")
				).then(
						tokenTransact(
								moving(1, A_TOKEN).between("firstTreasury", "beneficiary"),
								moving(1, A_TOKEN).from("firstTreasury")
						).payingWith("payer")
								.signedBy("payer", "firstTreasury")
								.hasKnownStatus(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN)
				);
	}


	public HapiApiSpec balancesChangeOnTokenTransfer() {
		return defaultHapiSpec("BalancesChangeOnTokenTransfer")
				.given(
						cryptoCreate(FIRST_USER),
						cryptoCreate(SECOND_USER),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(A_TOKEN)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY),
						tokenCreate(B_TOKEN)
								.initialSupply(TOTAL_SUPPLY)
								.treasury(TOKEN_TREASURY)
				).when(
						tokenTransact(
								moving(100, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER),
								moving(100, B_TOKEN).between(TOKEN_TREASURY, SECOND_USER)
						)
				).then(
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(A_TOKEN, TOTAL_SUPPLY - 100)
								.hasTokenBalance(B_TOKEN, TOTAL_SUPPLY - 100),
						getAccountBalance(FIRST_USER)
								.hasTokenBalance(A_TOKEN, 100),
						getAccountBalance(SECOND_USER)
								.hasTokenBalance(B_TOKEN, 100)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
