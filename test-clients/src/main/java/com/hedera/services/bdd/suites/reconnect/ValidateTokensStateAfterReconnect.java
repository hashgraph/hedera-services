package com.hedera.services.bdd.suites.reconnect;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;

/**
 * A reconnect test in which  a few tokens are created while the node 0.0.8 is disconnected from the network. Once the
 * node is reconnected the state of tokens is verified on reconnected node and other node
 */
public class ValidateTokensStateAfterReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ValidateTokensStateAfterReconnect.class);
	public static final String reconnectingNode = "0.0.8";
	public static final String nonReconnectingNode = "0.0.3";

	public static void main(String... args) {
		new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				validateTokensAfterReconnect()
		);
	}

	private HapiApiSpec validateTokensAfterReconnect() {
		String tokenToBeQueried = "token-1";
		String anotherToken = "token-2";
		String anotherAccount = "account";
		String supplyKey = "supplyKey";
		String freezeKey = "freezeKey";
		String adminKey = "adminKey";


		return customHapiSpec("ValidateTokensAfterReconnect")
				.withProperties(Map.of(
						"txn.start.offset.secs", "-5")
				)
				.given(
						sleepFor(Duration.ofSeconds(25).toMillis()),
						tokenOpsEnablement(),
						newKeyNamed(supplyKey),
						newKeyNamed(freezeKey),
						newKeyNamed(adminKey),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS).logging(),
						cryptoCreate(anotherAccount).balance(ONE_HUNDRED_HBARS).logging()
				)
				.when(
						getAccountBalance(GENESIS)
								.setNode(reconnectingNode)
								.unavailableNode(),

						tokenCreate(tokenToBeQueried)
								.freezeKey(freezeKey)
								.supplyKey(supplyKey)
								.initialSupply(ONE_HUNDRED_HBARS)
								.treasury(TOKEN_TREASURY).logging(),
						tokenCreate(anotherToken)
								.freezeKey(freezeKey)
								.supplyKey(supplyKey)
								.initialSupply(1)
								.treasury(TOKEN_TREASURY).logging(),

						/* Some token operations*/
						tokenAssociate(anotherAccount, tokenToBeQueried).logging(),
						tokenAssociate(anotherAccount, anotherToken).logging(),
						cryptoTransfer(moving(1, tokenToBeQueried).between(TOKEN_TREASURY, anotherAccount)).logging(),
						cryptoTransfer(moving(1, tokenToBeQueried).between(TOKEN_TREASURY, anotherAccount)).logging(),
						cryptoTransfer(moving(1, tokenToBeQueried).between(TOKEN_TREASURY, anotherAccount)).logging(),
						tokenUpdate(tokenToBeQueried)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(TOKEN_TREASURY)
								.adminKey(adminKey).logging(),
						mintToken(tokenToBeQueried, 100).logging()
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(DEFAULT_PAYER),
						mintToken(anotherToken, 100).logging()
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(DEFAULT_PAYER),
						burnToken(anotherToken, 1)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(DEFAULT_PAYER).logging(),
						burnToken(tokenToBeQueried, 1)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(DEFAULT_PAYER).logging(),
						cryptoDelete(TOKEN_TREASURY)
								.hasKnownStatus(ACCOUNT_IS_TREASURY).logging(),
						/* end token operations */

						getAccountBalance(GENESIS)
								.setNode(reconnectingNode)
								.unavailableNode()
				)
				.then(
						withLiveNode(reconnectingNode)
								.within(5 * 60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),

						getTokenInfo(tokenToBeQueried)
								.setNode(reconnectingNode)
								.hasAdminKey(adminKey)
								.hasFreezeKey(freezeKey)
								.hasSupplyKey(supplyKey)
								.hasTotalSupply(70).logging(),
						getTokenInfo(tokenToBeQueried)
								.setNode(nonReconnectingNode)
								.hasAdminKey(adminKey)
								.hasFreezeKey(freezeKey)
								.hasSupplyKey(supplyKey)
								.hasTotalSupply(70).logging(),
						getTokenInfo(anotherToken)
								.setNode(reconnectingNode)
								.hasFreezeKey(freezeKey)
								.hasSupplyKey(supplyKey)
								.hasTotalSupply(31).logging(),
						getTokenInfo(anotherToken)
								.setNode(nonReconnectingNode)
								.hasFreezeKey(freezeKey)
								.hasSupplyKey(supplyKey)
								.hasTotalSupply(31).logging(),
						cryptoDelete(TOKEN_TREASURY)
								.hasKnownStatus(ACCOUNT_IS_TREASURY)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
