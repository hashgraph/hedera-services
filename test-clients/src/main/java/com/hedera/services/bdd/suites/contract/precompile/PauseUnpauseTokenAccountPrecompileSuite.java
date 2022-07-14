/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.contract.precompile;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Paused;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Unpaused;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PauseUnpauseTokenAccountPrecompileSuite extends HapiApiSuite {
    private static final Logger log =
            LogManager.getLogger(PauseUnpauseTokenAccountPrecompileSuite.class);
    private static final String PAUSE_UNPAUSE_CONTRACT = "PauseUnpauseTokenAccount";

    private static final String PAUSE_KEY = "PAUSE_KEY";

    private static final String UNPAUSE_KEY = "UNPAUSE_KEY";
    private static final long GAS_TO_OFFER = 4_000_000L;

    public static void main(String... args) {
        new PauseUnpauseTokenAccountPrecompileSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
                List.of(
                        pauseFungibleTokenHappyPath(),
                        unpauseFungibleTokenHappyPath(),
                        pauseNonFungibleTokenHappyPath(),
                        unpauseNonFungibleTokenHappyPath()));
    }

    private HapiApiSpec pauseFungibleTokenHappyPath() {
        final AtomicReference<TokenID> tokenID = new AtomicReference<>();

        return defaultHapiSpec("PauseFungibleTokenHappyPath")
                .given(
                        newKeyNamed(PAUSE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .pauseKey(PAUSE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> tokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(PAUSE_UNPAUSE_CONTRACT)
                                                        .bytecode(PAUSE_UNPAUSE_CONTRACT),
                                                contractCall(
                                                                PAUSE_UNPAUSE_CONTRACT,
                                                                "pauseTokenAccount",
                                                                asAddress(tokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("pauseTokenAccountTxn")
                                                        .gas(GAS_TO_OFFER))))
                .then(getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Paused));
    }

    private HapiApiSpec unpauseFungibleTokenHappyPath() {
        final AtomicReference<TokenID> tokenID = new AtomicReference<>();

        return defaultHapiSpec("UnpauseFungibleTokenHappyPath")
                .given(
                        newKeyNamed(UNPAUSE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .pauseKey(UNPAUSE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> tokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(PAUSE_UNPAUSE_CONTRACT)
                                                        .bytecode(PAUSE_UNPAUSE_CONTRACT),
                                                contractCall(
                                                                PAUSE_UNPAUSE_CONTRACT,
                                                                "unpauseTokenAccount",
                                                                asAddress(tokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("unpauseTokenAccountTxn")
                                                        .gas(GAS_TO_OFFER))))
                .then(getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Unpaused));
    }

    private HapiApiSpec pauseNonFungibleTokenHappyPath() {
        final AtomicReference<TokenID> tokenID = new AtomicReference<>();

        return defaultHapiSpec("PauseNonFungibleTokenHappyPath")
                .given(
                        newKeyNamed(PAUSE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .pauseKey(PAUSE_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> tokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(PAUSE_UNPAUSE_CONTRACT)
                                                        .bytecode(PAUSE_UNPAUSE_CONTRACT),
                                                contractCall(
                                                                PAUSE_UNPAUSE_CONTRACT,
                                                                "pauseTokenAccount",
                                                                asAddress(tokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("pauseTokenAccountTxn")
                                                        .gas(GAS_TO_OFFER))))
                .then(getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Paused));
    }

    private HapiApiSpec unpauseNonFungibleTokenHappyPath() {
        final AtomicReference<TokenID> tokenID = new AtomicReference<>();

        return defaultHapiSpec("UnpauseNonFungibleTokenHappyPath")
                .given(
                        newKeyNamed(UNPAUSE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .pauseKey(UNPAUSE_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> tokenID.set(asToken(id))),
                        uploadInitCode(PAUSE_UNPAUSE_CONTRACT),
                        contractCreate(PAUSE_UNPAUSE_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(PAUSE_UNPAUSE_CONTRACT)
                                                        .bytecode(PAUSE_UNPAUSE_CONTRACT),
                                                contractCall(
                                                                PAUSE_UNPAUSE_CONTRACT,
                                                                "unpauseTokenAccount",
                                                                asAddress(tokenID.get()))
                                                        .payingWith(GENESIS)
                                                        .via("unpauseTokenAccountTxn")
                                                        .gas(GAS_TO_OFFER))))
                .then(getTokenInfo(VANILLA_TOKEN).hasPauseStatus(Unpaused));
    }
}
