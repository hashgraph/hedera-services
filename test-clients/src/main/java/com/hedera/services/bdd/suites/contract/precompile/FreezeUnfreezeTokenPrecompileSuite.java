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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
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
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class FreezeUnfreezeTokenPrecompileSuite extends HapiApiSuite {
    private static final Logger log =
            LogManager.getLogger(FreezeUnfreezeTokenPrecompileSuite.class);
    private static final String FREEZE_CONTRACT = "FreezeUnfreezeContract";
    private static final String ACCOUNT = "anybody";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String MULTI_KEY = "purpose";
    private static final long GAS_TO_OFFER = 4_000_000L;

    public static void main(String... args) {
        new FreezeUnfreezeTokenPrecompileSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(List.of( freezeFungibleAndNftTokensHappyPath(),isFrozenTokenHappyPath()));
    }

    private HapiApiSpec freezeFungibleAndNftTokensHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final AtomicReference<TokenID> nftTokenID = new AtomicReference<>();

        return defaultHapiSpec("freezeFungibleTokenHappyPath")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set).key(FREEZE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> nftTokenID.set(asToken(id))),
                        mintToken(KNOWABLE_TOKEN, List.of(copyFromUtf8("First!"))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, KNOWABLE_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                        cryptoTransfer(
                                movingUnique(KNOWABLE_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                "tokenFreeze",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via("freezeTxn")
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                "tokenFreeze",
                                                                asAddress(nftTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .via("freezeNFTTxn")
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        getAccountDetails(ACCOUNT)
                                .hasToken(
                                        ExpectedTokenRel.relationshipWith(KNOWABLE_TOKEN)
                                                .freeze(TokenFreezeStatus.Frozen)),
                        getAccountDetails(ACCOUNT)
                                .hasToken(
                                        ExpectedTokenRel.relationshipWith(VANILLA_TOKEN)
                                                .freeze(TokenFreezeStatus.Frozen)));
    }

    private HapiApiSpec isFrozenTokenHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec("freezeFungibleTokenHappyPath")
                .given(
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                "isTokenFrozen",
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via("isFrozenTxn")
                                                        .gas(GAS_TO_OFFER))))
                .then(getTxnRecord("isFrozenTxn").andAllChildRecords().logged());
    }
}
