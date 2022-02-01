package com.hedera.services.bdd.suites.crypto;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class CryptoApproveAllowanceSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoApproveAllowanceSuite.class);

	public static void main(String... args) {
		new CryptoApproveAllowanceSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				happyPathWorks(),

		});
	}

	private HapiApiSpec happyPathWorks() {
		final String owner = "owner";
		final String spender = "spender";
		final String token = "token";
		final String nft = "nft";
		return defaultHapiSpec("happyPathWorks")
				.given(
						newKeyNamed("supplyKey"),
						newKeyNamed("adminKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("wipeKey"),
						newKeyNamed("feeScheduleKey"),
						newKeyNamed("pauseKey"),
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(token)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.supplyKey("supplyKey")
								.maxSupply(1000L)
								.initialSupply(10L)
								.treasury(TOKEN_TREASURY),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, token),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						mintToken(token, 500L).via("tokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addCryptoAllowance(spender, 100L)
								.addTokenAllowance(token, spender, 100L)
								.addNftAllowance(nft, spender, false, List.of(1L))
								.fee(ONE_HUNDRED_HBARS)
				)
				.then(
						getAccountInfo(owner)
								.has(accountWith()
										.cryptoAllowancesCount(1)
										.nftAllowancesCount(1)
										.tokenAllowancesCount(1)
										.cryptoAllowancesContaining(spender, 100L)
										.tokenAllowancesContaining(token, spender, 100L)
										.nftAllowancesContaining(nft, spender, false, List.of(1L))
								));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
