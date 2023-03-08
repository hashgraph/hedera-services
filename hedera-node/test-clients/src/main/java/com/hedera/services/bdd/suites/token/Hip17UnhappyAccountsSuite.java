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
    private static final String MEMO_1 = "memo1";
    private static final String MEMO_2 = "memo2";

    private static final String SUPPLY_KEY = "supplyKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String KYC_KEY = "kycKey";
    private static final String WIPE_KEY = "wipeKey";
    private static final String CLIENT_1 = "Client1";
    private static final String CLIENT_2 = "Client2";
    private static final String TREASURY = "treasury";
    private static final String UNIQUE_TOKEN_A = "TokenA";

    public static void main(String... args) {
        new Hip17UnhappyAccountsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[]{
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
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(TREASURY),
                        tokenCreate(UNIQUE_TOKEN_A)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TREASURY),
                        mintToken(
                                UNIQUE_TOKEN_A,
                                List.of(ByteString.copyFromUtf8(MEMO_1), ByteString.copyFromUtf8(MEMO_2))))
                .when(
                        cryptoCreate(CLIENT_1).autoRenewSecs(3L).balance(0L),
                        tokenAssociate(CLIENT_1, UNIQUE_TOKEN_A),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 1L).between(TREASURY, CLIENT_1)),
                        sleepFor(3_500L),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)))
                .then(
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 2L).between(TREASURY, CLIENT_1))
                                .hasKnownStatus(INVALID_ACCOUNT_ID),
                        revokeTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(INVALID_ACCOUNT_ID),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenFreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(INVALID_ACCOUNT_ID),
                        tokenUnfreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(INVALID_ACCOUNT_ID),
                        wipeTokenAccount(UNIQUE_TOKEN_A, CLIENT_1, List.of(1L)).hasKnownStatus(INVALID_ACCOUNT_ID));
    }

    private HapiSpec uniqueTokenOperationsFailForDeletedAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForDeletedAccount")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(CLIENT_1),
                        cryptoCreate(TREASURY))
                .when(
                        tokenCreate(UNIQUE_TOKEN_A)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TREASURY),
                        mintToken(
                                UNIQUE_TOKEN_A,
                                List.of(ByteString.copyFromUtf8(MEMO_1), ByteString.copyFromUtf8(MEMO_2))),
                        tokenAssociate(CLIENT_1, UNIQUE_TOKEN_A),
                        cryptoDelete(CLIENT_1))
                .then(
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 2L).between(TREASURY, CLIENT_1))
                                .hasKnownStatus(ACCOUNT_DELETED),
                        revokeTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_DELETED),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_DELETED),
                        tokenFreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_DELETED),
                        tokenUnfreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_DELETED),
                        wipeTokenAccount(UNIQUE_TOKEN_A, CLIENT_1, List.of(1L)).hasKnownStatus(ACCOUNT_DELETED));
    }

    private HapiSpec uniqueTokenOperationsFailForExpiredAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForExpiredAccount")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        AutoRenewConfigChoices.propsForAccountAutoRenewOnWith(1, 7776000, 100, 10))
                                .erasingProps(Set.of("minimumAutoRenewDuration")),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(TREASURY).autoRenewSecs(THREE_MONTHS_IN_SECONDS),
                        tokenCreate(UNIQUE_TOKEN_A)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TREASURY),
                        mintToken(
                                UNIQUE_TOKEN_A,
                                List.of(ByteString.copyFromUtf8(MEMO_1), ByteString.copyFromUtf8(MEMO_2))),
                        cryptoCreate(CLIENT_1).autoRenewSecs(10L).balance(0L),
                        getAccountInfo(CLIENT_1).logged(),
                        tokenAssociate(CLIENT_1, UNIQUE_TOKEN_A),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 2L).between(TREASURY, CLIENT_1)))
                .when(sleepFor(10_500L), cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)))
                .then(
                        getAccountInfo(CLIENT_1).logged(),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 2L).between(TREASURY, CLIENT_1))
                                .hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        cryptoCreate(CLIENT_2),
                        tokenAssociate(CLIENT_2, UNIQUE_TOKEN_A),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 1L).between(CLIENT_1, CLIENT_2))
                                .hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        tokenFreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        tokenUnfreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        revokeTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL),
                        wipeTokenAccount(UNIQUE_TOKEN_A, CLIENT_1, List.of(1L))
                                .hasKnownStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    private HapiSpec uniqueTokenOperationsFailForKycRevokedAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForKycRevokedAccount")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(CLIENT_1),
                        cryptoCreate(CLIENT_2),
                        cryptoCreate(TREASURY))
                .when(
                        tokenCreate(UNIQUE_TOKEN_A)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TREASURY),
                        mintToken(
                                UNIQUE_TOKEN_A,
                                List.of(ByteString.copyFromUtf8(MEMO_1), ByteString.copyFromUtf8(MEMO_2))),
                        tokenAssociate(CLIENT_1, UNIQUE_TOKEN_A),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 1L).between(TREASURY, CLIENT_1)),
                        tokenAssociate(CLIENT_2, UNIQUE_TOKEN_A),
                        revokeTokenKyc(UNIQUE_TOKEN_A, CLIENT_1))
                .then(
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 2L).between(TREASURY, CLIENT_1))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 1L).between(CLIENT_1, CLIENT_2))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        wipeTokenAccount(UNIQUE_TOKEN_A, CLIENT_1, List.of(1L))
                                .hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN));
    }

    private HapiSpec uniqueTokenOperationsFailForFrozenAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForFrozenAccount")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(CLIENT_1),
                        cryptoCreate(CLIENT_2),
                        cryptoCreate(TREASURY))
                .when(
                        tokenCreate(UNIQUE_TOKEN_A)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TREASURY),
                        mintToken(
                                UNIQUE_TOKEN_A,
                                List.of(ByteString.copyFromUtf8(MEMO_1), ByteString.copyFromUtf8(MEMO_2))),
                        tokenAssociate(CLIENT_1, UNIQUE_TOKEN_A),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 1L).between(TREASURY, CLIENT_1)),
                        tokenAssociate(CLIENT_2, UNIQUE_TOKEN_A),
                        tokenFreeze(UNIQUE_TOKEN_A, CLIENT_1))
                .then(
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 2L).between(TREASURY, CLIENT_1))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 1L).between(CLIENT_1, CLIENT_2))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        wipeTokenAccount(UNIQUE_TOKEN_A, CLIENT_1, List.of(1L))
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN));
    }

    private HapiSpec uniqueTokenOperationsFailForDissociatedAccount() {
        return defaultHapiSpec("UniqueTokenOperationsFailForDissociatedAccount")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(CLIENT_1),
                        cryptoCreate(TREASURY))
                .when(
                        tokenCreate(UNIQUE_TOKEN_A)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .freezeKey(FREEZE_KEY)
                                .kycKey(KYC_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TREASURY),
                        mintToken(UNIQUE_TOKEN_A, List.of(ByteString.copyFromUtf8("memo"))))
                .then(
                        tokenFreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        tokenUnfreeze(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        grantTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        revokeTokenKyc(UNIQUE_TOKEN_A, CLIENT_1).hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        wipeTokenAccount(UNIQUE_TOKEN_A, CLIENT_1, List.of(1L))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN_A, 1L).between(TREASURY, CLIENT_1))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
