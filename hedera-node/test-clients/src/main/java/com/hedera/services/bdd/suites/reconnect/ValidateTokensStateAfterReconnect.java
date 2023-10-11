/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
 */

package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hedera.services.bdd.suites.reconnect.AutoRenewEntitiesForReconnect.runTransfersBeforeReconnect;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A reconnect test in which a few tokens are created while the node 0.0.8 is disconnected from the
 * network. Once the node is reconnected the state of tokens is verified on reconnected node and
 * other node
 */
public class ValidateTokensStateAfterReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ValidateTokensStateAfterReconnect.class);
    public static final String reconnectingNode = "0.0.8";
    public static final String nonReconnectingNode = "0.0.3";
    private static final long TOKEN_INITIAL_SUPPLY = 500;

    public static void main(String... args) {
        new ValidateAppPropertiesStateAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runTransfersBeforeReconnect(), validateTokensAfterReconnect());
    }

    private HapiSpec validateTokensAfterReconnect() {
        String tokenToBeQueried = "token-1";
        String anotherToken = "token-2";
        String anotherAccount = "account";
        String supplyKey = "supplyKey";
        String freezeKey = "freezeKey";
        String adminKey = "adminKey";
        String newAdminKey = "newAdminKey";

        return customHapiSpec("ValidateTokensAfterReconnect")
                .withProperties(Map.of("txn.start.offset.secs", "-5"))
                .given(
                        sleepFor(Duration.ofSeconds(25).toMillis()),
                        tokenOpsEnablement(),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(adminKey),
                        newKeyNamed(newAdminKey),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS).logging(),
                        cryptoCreate(anotherAccount).balance(ONE_HUNDRED_HBARS).logging())
                .when(
                        sleepFor(Duration.ofSeconds(26).toMillis()),
                        getAccountBalance(GENESIS).setNode(reconnectingNode).unavailableNode(),
                        tokenCreate(tokenToBeQueried)
                                .freezeKey(freezeKey)
                                .supplyKey(supplyKey)
                                .initialSupply(TOKEN_INITIAL_SUPPLY)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(adminKey)
                                .logging(),
                        tokenCreate(anotherToken)
                                .freezeKey(freezeKey)
                                .supplyKey(supplyKey)
                                .initialSupply(TOKEN_INITIAL_SUPPLY)
                                .adminKey(adminKey)
                                .treasury(TOKEN_TREASURY)
                                .logging(),

                        /* Some token operations*/
                        getTokenInfo(tokenToBeQueried),
                        getTokenInfo(anotherToken),
                        tokenUpdate(tokenToBeQueried)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(TOKEN_TREASURY)
                                .adminKey(newAdminKey),
                        tokenAssociate(anotherAccount, tokenToBeQueried, anotherToken)
                                .logging(),
                        blockingOrder(IntStream.range(0, 10)
                                .mapToObj(i -> cryptoTransfer(
                                        moving(1, tokenToBeQueried).between(TOKEN_TREASURY, anotherAccount)))
                                .toArray(HapiSpecOperation[]::new)),
                        blockingOrder(IntStream.range(0, 5)
                                .mapToObj(i -> mintToken(tokenToBeQueried, 100))
                                .toArray(HapiSpecOperation[]::new)),
                        blockingOrder(IntStream.range(0, 5)
                                .mapToObj(i -> mintToken(anotherToken, 100))
                                .toArray(HapiSpecOperation[]::new)),
                        burnToken(anotherToken, 1),
                        burnToken(tokenToBeQueried, 1),
                        cryptoDelete(TOKEN_TREASURY).hasKnownStatus(ACCOUNT_IS_TREASURY),
                        getTokenInfo(tokenToBeQueried),
                        getTokenInfo(anotherToken),
                        /* end token operations */

                        getAccountBalance(GENESIS).setNode(reconnectingNode).unavailableNode())
                .then(
                        withLiveNode(reconnectingNode)
                                .within(5 * 60, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),

                        // wait reconnect node to finish receiving new state, otherwise it may
                        // response
                        // with incorrect answer from old state
                        sleepFor(3000),
                        /* validate tokenInfo between reconnecting node and other nodes match*/
                        getTokenInfo(tokenToBeQueried)
                                .setNode(reconnectingNode)
                                .hasAdminKey(tokenToBeQueried)
                                .hasFreezeKey(tokenToBeQueried)
                                .hasSupplyKey(tokenToBeQueried)
                                .hasTotalSupply(999)
                                .logging(),
                        getTokenInfo(tokenToBeQueried)
                                .setNode(nonReconnectingNode)
                                .hasAdminKey(tokenToBeQueried)
                                .hasFreezeKey(tokenToBeQueried)
                                .hasSupplyKey(tokenToBeQueried)
                                .hasTotalSupply(999)
                                .logging(),
                        getTokenInfo(anotherToken)
                                .setNode(reconnectingNode)
                                .hasFreezeKey(anotherToken)
                                .hasAdminKey(anotherToken)
                                .hasSupplyKey(anotherToken)
                                .hasTotalSupply(999)
                                .logging(),
                        getTokenInfo(anotherToken)
                                .setNode(nonReconnectingNode)
                                .hasFreezeKey(anotherToken)
                                .hasAdminKey(anotherToken)
                                .hasSupplyKey(anotherToken)
                                .hasTotalSupply(999)
                                .logging(),
                        cryptoDelete(TOKEN_TREASURY)
                                .hasKnownStatus(ACCOUNT_IS_TREASURY)
                                .setNode(reconnectingNode),

                        /* Should be able to delete treasury only after dissociating the tokens */
                        tokenDelete(tokenToBeQueried).setNode(reconnectingNode),
                        tokenDissociate(TOKEN_TREASURY, tokenToBeQueried).setNode(reconnectingNode),
                        cryptoDelete(TOKEN_TREASURY)
                                .hasKnownStatus(ACCOUNT_IS_TREASURY)
                                .setNode(reconnectingNode),
                        tokenDelete(anotherToken).setNode(reconnectingNode),
                        /* Should dissociate with any tokens[even deleted ones] to be able to delete the treasury */
                        cryptoDelete(TOKEN_TREASURY)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
                                .setNode(reconnectingNode),
                        tokenDissociate(TOKEN_TREASURY, anotherToken),
                        cryptoDelete(TOKEN_TREASURY));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
