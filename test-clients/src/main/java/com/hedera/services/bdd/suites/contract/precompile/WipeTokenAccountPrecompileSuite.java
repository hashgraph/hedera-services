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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WipeTokenAccountPrecompileSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(WipeTokenAccountPrecompileSuite.class);
    private static final String WIPE_CONTRACT = "WipeTokenAccount";
    private static final String ACCOUNT = "anybody";
    private static final String WIPE_KEY = "wipeKey";
    private static final String MULTI_KEY = "purpose";
    private static final long GAS_TO_OFFER = 4_000_000L;

    public static void main(String... args) {
        new WipeTokenAccountPrecompileSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(List.of(wipeFungibleTokenHappyPath(), wipeNonFungibleTokenHappyPath()));
    }

    private HapiApiSpec wipeFungibleTokenHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec("WipeFungibleTokenHappyPath")
                .given(
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey(WIPE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(WIPE_CONTRACT),
                        contractCreate(WIPE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(WIPE_CONTRACT)
                                                        .bytecode(WIPE_CONTRACT),
                                                contractCall(
                                                                WIPE_CONTRACT,
                                                                "wipeFungibleToken",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()),
                                                                10L)
                                                        .payingWith(ACCOUNT)
                                                        .via("wipeFungibleTxn")
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        getTokenInfo(VANILLA_TOKEN).hasTotalSupply(990),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 490));
    }

    private HapiApiSpec wipeNonFungibleTokenHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec("WipeNonFungibleTokenHappyPath")
                .given(
                        newKeyNamed(WIPE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .wipeKey(WIPE_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!"))),
                        uploadInitCode(WIPE_CONTRACT),
                        contractCreate(WIPE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(
                                movingUnique(VANILLA_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var serialNumbers = new ArrayList<>();
                                    serialNumbers.add(1L);
                                    allRunFor(
                                            spec,
                                            contractCreate(WIPE_CONTRACT).bytecode(WIPE_CONTRACT),
                                            contractCall(
                                                            WIPE_CONTRACT,
                                                            "wipeNonFungibleToken",
                                                            asAddress(vanillaTokenID.get()),
                                                            asAddress(accountID.get()),
                                                            serialNumbers)
                                                    .payingWith(ACCOUNT)
                                                    .via("wipeFungibleTxn")
                                                    .gas(GAS_TO_OFFER));
                                }))
                .then(
                        getTokenInfo(VANILLA_TOKEN).hasTotalSupply(1),
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1));
    }
}
