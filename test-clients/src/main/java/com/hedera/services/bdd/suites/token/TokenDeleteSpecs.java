/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenDeleteSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenDeleteSpecs.class);

    private static final String FIRST_TBD = "firstTbd";
    private static final String SECOND_TBD = "secondTbd";
    private static final String TOKEN_ADMIN = "tokenAdmin";
    private static final String PAYER = "payer";
    private static final String MULTI_KEY = "multiKey";

    public static void main(String... args) {
        new TokenDeleteSpecs().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                deletionValidatesMissingAdminKey(),
                deletionWorksAsExpected(),
                deletionValidatesAlreadyDeletedToken(),
                treasuryBecomesDeletableAfterTokenDelete(),
                deletionValidatesRef());
    }

    private HapiSpec treasuryBecomesDeletableAfterTokenDelete() {
        return defaultHapiSpec("TreasuryBecomesDeletableAfterTokenDelete")
                .given(
                        newKeyNamed(TOKEN_ADMIN),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate(FIRST_TBD).adminKey(TOKEN_ADMIN).treasury(TOKEN_TREASURY),
                        tokenCreate(SECOND_TBD).adminKey(TOKEN_ADMIN).treasury(TOKEN_TREASURY),
                        cryptoDelete(TOKEN_TREASURY).hasKnownStatus(ACCOUNT_IS_TREASURY),
                        tokenDissociate(TOKEN_TREASURY, FIRST_TBD)
                                .hasKnownStatus(ACCOUNT_IS_TREASURY))
                .when(
                        tokenDelete(FIRST_TBD),
                        tokenDissociate(TOKEN_TREASURY, FIRST_TBD),
                        cryptoDelete(TOKEN_TREASURY).hasKnownStatus(ACCOUNT_IS_TREASURY),
                        tokenDelete(SECOND_TBD))
                .then(tokenDissociate(TOKEN_TREASURY, SECOND_TBD), cryptoDelete(TOKEN_TREASURY));
    }

    private HapiSpec deletionValidatesAlreadyDeletedToken() {
        return defaultHapiSpec("DeletionValidatesAlreadyDeletedToken")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate("tbd").adminKey(MULTI_KEY).treasury(TOKEN_TREASURY),
                        tokenDelete("tbd"))
                .when()
                .then(tokenDelete("tbd").hasKnownStatus(TOKEN_WAS_DELETED));
    }

    private HapiSpec deletionValidatesMissingAdminKey() {
        return defaultHapiSpec("DeletionValidatesMissingAdminKey")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(PAYER),
                        tokenCreate("tbd")
                                .freezeDefault(false)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(PAYER))
                .when()
                .then(
                        tokenDelete("tbd")
                                .payingWith(PAYER)
                                .signedBy(PAYER)
                                .hasKnownStatus(TOKEN_IS_IMMUTABLE));
    }

    public HapiSpec deletionWorksAsExpected() {
        return defaultHapiSpec("DeletionWorksAsExpected")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(PAYER),
                        tokenCreate("tbd")
                                .adminKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .freezeDefault(false)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(PAYER),
                        tokenAssociate(GENESIS, "tbd"))
                .when(
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        mintToken("tbd", 1),
                        burnToken("tbd", 1),
                        revokeTokenKyc("tbd", GENESIS),
                        grantTokenKyc("tbd", GENESIS),
                        tokenFreeze("tbd", GENESIS),
                        tokenUnfreeze("tbd", GENESIS),
                        cryptoTransfer(moving(1, "tbd").between(TOKEN_TREASURY, GENESIS)),
                        tokenDelete("tbd").payingWith(PAYER))
                .then(
                        getTokenInfo("tbd").logged(),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        cryptoTransfer(moving(1, "tbd").between(TOKEN_TREASURY, GENESIS))
                                .hasKnownStatus(TOKEN_WAS_DELETED),
                        mintToken("tbd", 1).hasKnownStatus(TOKEN_WAS_DELETED),
                        burnToken("tbd", 1).hasKnownStatus(TOKEN_WAS_DELETED),
                        revokeTokenKyc("tbd", GENESIS).hasKnownStatus(TOKEN_WAS_DELETED),
                        grantTokenKyc("tbd", GENESIS).hasKnownStatus(TOKEN_WAS_DELETED),
                        tokenFreeze("tbd", GENESIS).hasKnownStatus(TOKEN_WAS_DELETED),
                        tokenUnfreeze("tbd", GENESIS).hasKnownStatus(TOKEN_WAS_DELETED));
    }

    public HapiSpec deletionValidatesRef() {
        return defaultHapiSpec("DeletionValidatesRef")
                .given(cryptoCreate(PAYER))
                .when()
                .then(
                        tokenDelete("0.0.0")
                                .payingWith(PAYER)
                                .signedBy(PAYER)
                                .hasKnownStatus(INVALID_TOKEN_ID),
                        tokenDelete("1.2.3")
                                .payingWith(PAYER)
                                .signedBy(PAYER)
                                .hasKnownStatus(INVALID_TOKEN_ID));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
