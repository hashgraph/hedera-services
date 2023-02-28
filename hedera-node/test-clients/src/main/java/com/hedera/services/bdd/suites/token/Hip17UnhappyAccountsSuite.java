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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Hip17UnhappyAccountsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Hip17UnhappyAccountsSuite.class);

    final String supplyKey = "supplyKey";
    final String freezeKey = "freezeKey";
    final String kycKey = "kycKey";
    final String wipeKey = "wipeKey";
    final String firstUser = "Client1";
    final String secondUser = "Client2";
    final String tokenTreasury = "treasury";
    final String uniqueTokenA = "TokenA";

    public static void main(String... args) {
        new Hip17UnhappyAccountsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            /* Dissociated Account */
            uniqueTokenOperationsFailForDissociatedAccount(),
            /* Frozen Account */
            uniqueTokenOperationsFailForFrozenAccount(),
            /* Account Without KYC */
            uniqueTokenOperationsFailForKycRevokedAccount(),
            /* Expired Account */
            uniqueTokenOperationsFailForExpiredAccount(),
            /* Deleted Account */
            uniqueTokenOperationsFailForDeletedAccount(),
            /* AutoRemoved Account */
            uniqueTokenOperationsFailForAutoRemovedAccount()
        });
    }

    private HapiSpec uniqueTokenOperationsFailForAutoRemovedAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForAutoRemovedAccount")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(AutoRenewConfigChoices.propsForAccountAutoRenewOnWith(1, 0, 100, 10))
                                .erasingProps(Set.of("minimumAutoRenewDuration")),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(wipeKey),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(uniqueTokenA)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .wipeKey(wipeKey)
                                .treasury(tokenTreasury),
                        mintToken(
                                uniqueTokenA,
                                List.of(ByteString.copyFromUtf8("memo1"), ByteString.copyFromUtf8("memo2"))))
                .when(
                        cryptoCreate(firstUser).autoRenewSecs(3L).balance(0L),
                        tokenAssociate(firstUser, uniqueTokenA),
                        grantTokenKyc(uniqueTokenA, firstUser),
                        cryptoTransfer(movingUnique(uniqueTokenA, 1L).between(tokenTreasury, firstUser)),
                        sleepFor(3_500L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        cryptoTransfer(movingUnique(uniqueTokenA, 2L).between(tokenTreasury, firstUser))
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        revokeTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(INVALID_ACCOUNT_ID),
                        grantTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenFreeze(uniqueTokenA, firstUser).hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenUnfreeze(uniqueTokenA, firstUser).hasKnownStatus(INVALID_ACCOUNT_ID),
                        wipeTokenAccount(uniqueTokenA, firstUser, List.of(1L)).hasKnownStatus(INVALID_ACCOUNT_ID));
    }

    private HapiSpec uniqueTokenOperationsFailForDeletedAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForDeletedAccount")
                .given(
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(wipeKey),
                        cryptoCreate(firstUser),
                        cryptoCreate(tokenTreasury))
                .when(
                        tokenCreate(uniqueTokenA)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .wipeKey(wipeKey)
                                .treasury(tokenTreasury),
                        mintToken(
                                uniqueTokenA,
                                List.of(ByteString.copyFromUtf8("memo1"), ByteString.copyFromUtf8("memo2"))),
                        tokenAssociate(firstUser, uniqueTokenA),
                        cryptoDelete(firstUser))
                .then(
                        cryptoTransfer(movingUnique(uniqueTokenA, 2L).between(tokenTreasury, firstUser))
                                .hasKnownStatus(ACCOUNT_DELETED),
                        revokeTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_DELETED),
                        grantTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_DELETED),
                        tokenFreeze(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_DELETED),
                        tokenUnfreeze(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_DELETED),
                        wipeTokenAccount(uniqueTokenA, firstUser, List.of(1L)).hasKnownStatus(ACCOUNT_DELETED));
    }

    private HapiSpec uniqueTokenOperationsFailForExpiredAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForExpiredAccount")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        AutoRenewConfigChoices.propsForAccountAutoRenewOnWith(1, 7776000, 100, 10))
                                .erasingProps(Set.of("minimumAutoRenewDuration")),
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(wipeKey),
                        cryptoCreate(tokenTreasury).autoRenewSecs(THREE_MONTHS_IN_SECONDS),
                        tokenCreate(uniqueTokenA)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .wipeKey(wipeKey)
                                .treasury(tokenTreasury),
                        mintToken(
                                uniqueTokenA,
                                List.of(ByteString.copyFromUtf8("memo1"), ByteString.copyFromUtf8("memo2"))),
                        cryptoCreate(firstUser).autoRenewSecs(10L).balance(0L),
                        getAccountInfo(firstUser).logged(),
                        tokenAssociate(firstUser, uniqueTokenA),
                        grantTokenKyc(uniqueTokenA, firstUser),
                        cryptoTransfer(movingUnique(uniqueTokenA, 2L).between(tokenTreasury, firstUser)))
                .when(sleepFor(10_500L), cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)))
                .then(
                        getAccountInfo(firstUser).logged(),
                        cryptoTransfer(movingUnique(uniqueTokenA, 2L).between(tokenTreasury, firstUser))
                                .hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        cryptoCreate(secondUser),
                        tokenAssociate(secondUser, uniqueTokenA),
                        cryptoTransfer(movingUnique(uniqueTokenA, 1L).between(firstUser, secondUser))
                                .hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        tokenFreeze(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        tokenUnfreeze(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        grantTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        revokeTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        wipeTokenAccount(uniqueTokenA, firstUser, List.of(1L))
                                .hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    private HapiSpec uniqueTokenOperationsFailForKycRevokedAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForKycRevokedAccount")
                .given(
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(wipeKey),
                        cryptoCreate(firstUser),
                        cryptoCreate(secondUser),
                        cryptoCreate(tokenTreasury))
                .when(
                        tokenCreate(uniqueTokenA)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .wipeKey(wipeKey)
                                .treasury(tokenTreasury),
                        mintToken(
                                uniqueTokenA,
                                List.of(ByteString.copyFromUtf8("memo1"), ByteString.copyFromUtf8("memo2"))),
                        tokenAssociate(firstUser, uniqueTokenA),
                        grantTokenKyc(uniqueTokenA, firstUser),
                        cryptoTransfer(movingUnique(uniqueTokenA, 1L).between(tokenTreasury, firstUser)),
                        tokenAssociate(secondUser, uniqueTokenA),
                        revokeTokenKyc(uniqueTokenA, firstUser))
                .then(
                        cryptoTransfer(movingUnique(uniqueTokenA, 2L).between(tokenTreasury, firstUser))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        cryptoTransfer(movingUnique(uniqueTokenA, 1L).between(firstUser, secondUser))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        wipeTokenAccount(uniqueTokenA, firstUser, List.of(1L))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN));
    }

    private HapiSpec uniqueTokenOperationsFailForFrozenAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForFrozenAccount")
                .given(
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(wipeKey),
                        cryptoCreate(firstUser),
                        cryptoCreate(secondUser),
                        cryptoCreate(tokenTreasury))
                .when(
                        tokenCreate(uniqueTokenA)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .wipeKey(wipeKey)
                                .treasury(tokenTreasury),
                        mintToken(
                                uniqueTokenA,
                                List.of(ByteString.copyFromUtf8("memo1"), ByteString.copyFromUtf8("memo2"))),
                        tokenAssociate(firstUser, uniqueTokenA),
                        grantTokenKyc(uniqueTokenA, firstUser),
                        cryptoTransfer(movingUnique(uniqueTokenA, 1L).between(tokenTreasury, firstUser)),
                        tokenAssociate(secondUser, uniqueTokenA),
                        tokenFreeze(uniqueTokenA, firstUser))
                .then(
                        cryptoTransfer(movingUnique(uniqueTokenA, 2L).between(tokenTreasury, firstUser))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        cryptoTransfer(movingUnique(uniqueTokenA, 1L).between(firstUser, secondUser))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        wipeTokenAccount(uniqueTokenA, firstUser, List.of(1L))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
    }

    private HapiSpec uniqueTokenOperationsFailForDissociatedAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForDissociatedAccount")
                .given(
                        newKeyNamed(supplyKey),
                        newKeyNamed(freezeKey),
                        newKeyNamed(kycKey),
                        newKeyNamed(wipeKey),
                        cryptoCreate(firstUser),
                        cryptoCreate(tokenTreasury))
                .when(
                        tokenCreate(uniqueTokenA)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(supplyKey)
                                .freezeKey(freezeKey)
                                .kycKey(kycKey)
                                .wipeKey(wipeKey)
                                .treasury(tokenTreasury),
                        mintToken(uniqueTokenA, List.of(ByteString.copyFromUtf8("memo"))))
                .then(
                        tokenFreeze(uniqueTokenA, firstUser).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        tokenUnfreeze(uniqueTokenA, firstUser).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        grantTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        revokeTokenKyc(uniqueTokenA, firstUser).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        wipeTokenAccount(uniqueTokenA, firstUser, List.of(1L))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        cryptoTransfer(movingUnique(uniqueTokenA, 1L).between(tokenTreasury, firstUser))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
