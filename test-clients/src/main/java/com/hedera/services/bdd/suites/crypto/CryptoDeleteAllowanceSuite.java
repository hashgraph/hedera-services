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
package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDeleteAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoApproveAllowance.MISSING_OWNER;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoDeleteAllowanceSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoDeleteAllowanceSuite.class);

    public static void main(String... args) {
        new CryptoDeleteAllowanceSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    happyPathWorks(),
                    approvedForAllNotAffectedOnDelete(),
                    noOwnerDefaultsToPayerInDeleteAllowance(),
                    invalidOwnerFails(),
                    canDeleteMultipleOwners(),
                    emptyAllowancesDeleteRejected(),
                    tokenNotAssociatedToAccountFailsOnDeleteAllowance(),
                    invalidTokenTypeFailsInDeleteAllowance(),
                    validatesSerialNums(),
                    exceedsTransactionLimit(),
                    succeedsWhenTokenPausedFrozenKycRevoked(),
                    feesAsExpected(),
                    duplicateEntriesDoesntThrow()
                });
    }

    private HapiSpec duplicateEntriesDoesntThrow() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("duplicateEntriesDoesntThrow")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L, 2L, 3L)),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 100L)
                                                .tokenAllowancesContaining(token, spender, 100L)),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender))
                .then(
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L, 2L, 2L, 2L, 2L))
                                .addNftDeleteAllowance(owner, nft, List.of(1L, 3L, 2L, 3L)),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(0)),
                        getTokenNftInfo(nft, 1L).hasNoSpender(),
                        getTokenNftInfo(nft, 2L).hasNoSpender(),
                        getTokenNftInfo(nft, 3L).hasNoSpender(),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .addNftDeleteAllowance(owner, nft, List.of(2L)),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(0)),
                        getTokenNftInfo(nft, 1L).hasNoSpender(),
                        getTokenNftInfo(nft, 2L).hasNoSpender(),
                        getTokenNftInfo(nft, 3L).hasNoSpender());
    }

    private HapiSpec invalidOwnerFails() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("invalidOwnerFails")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate("payer").balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"))
                .when(
                        cryptoApproveAllowance()
                                .payingWith("payer")
                                .addCryptoAllowance(owner, spender, 100L)
                                .signedBy("payer", owner)
                                .blankMemo(),
                        cryptoDelete(owner),
                        cryptoDeleteAllowance()
                                .payingWith("payer")
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .signedBy("payer", owner)
                                .via("baseDeleteTxn")
                                .blankMemo()
                                .hasPrecheck(INVALID_ALLOWANCE_OWNER_ID))
                .then(
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .hasCostAnswerPrecheck(ACCOUNT_DELETED)
                                .hasAnswerOnlyPrecheck(ACCOUNT_DELETED));
    }

    private HapiSpec feesAsExpected() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        final String payer = "payer";
        return defaultHapiSpec("feesAsExpected")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(payer)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate("spender1").balance(ONE_HUNDRED_HBARS),
                        cryptoCreate("spender2").balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, "spender2", 100L)
                                .addTokenAllowance(owner, token, "spender2", 100L)
                                .addNftAllowance(
                                        owner, nft, "spender2", false, List.of(1L, 2L, 3L)),
                        /* without specifying owner */
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .blankMemo()
                                .addNftDeleteAllowance(MISSING_OWNER, nft, List.of(1L))
                                .via("baseDeleteNft"),
                        validateChargedUsdWithin("baseDeleteNft", 0.05, 0.01))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
                        /* with specifying owner */
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .blankMemo()
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .via("baseDeleteNft"),
                        validateChargedUsdWithin("baseDeleteNft", 0.05, 0.01),

                        /* with 2 serials */
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .blankMemo()
                                .addNftDeleteAllowance(owner, nft, List.of(2L, 3L))
                                .via("twoDeleteNft"),
                        validateChargedUsdWithin("twoDeleteNft", 0.050101, 0.01),
                        /* with 2 sigs */
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, "spender2", false, List.of(1L)),
                        cryptoDeleteAllowance()
                                .payingWith(payer)
                                .blankMemo()
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .signedBy(payer, owner)
                                .via("twoDeleteNft"),
                        validateChargedUsdWithin("twoDeleteNft", 0.08124, 0.01));
    }

    private HapiSpec succeedsWhenTokenPausedFrozenKycRevoked() {
        final String owner = "owner";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("succeedsWhenTokenPausedFrozenKycRevoked")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                "hedera.allowances.maxTransactionLimit", "20",
                                                "hedera.allowances.maxAccountLimit", "100")),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("adminKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("pauseKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender1).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(token)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey("supplyKey")
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .kycKey("kycKey")
                                .adminKey("adminKey")
                                .freezeKey("freezeKey")
                                .pauseKey("pauseKey")
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(nft)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey("supplyKey")
                                .kycKey("kycKey")
                                .adminKey("adminKey")
                                .freezeKey("freezeKey")
                                .pauseKey("pauseKey")
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(owner, token),
                        tokenAssociate(owner, nft),
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        grantTokenKyc(token, owner),
                        grantTokenKyc(nft, owner),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L)),
                        revokeTokenKyc(nft, owner),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L)),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().noAllowances()))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(3L)),
                        tokenPause(nft),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(3L)),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().noAllowances()),
                        tokenUnpause(nft),
                        tokenFreeze(nft, owner),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(2L)),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(2L)),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().noAllowances()),
                        // reset
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                "hedera.allowances.maxTransactionLimit", "20",
                                                "hedera.allowances.maxAccountLimit", "100")));
    }

    private HapiSpec exceedsTransactionLimit() {
        final String owner = "owner";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String spender2 = "spender2";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("exceedsTransactionLimit")
                .given(
                        newKeyNamed("supplyKey"),
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of("hedera.allowances.maxTransactionLimit", "4")),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender1).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender2).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L, 2L)),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L, 2L, 3L, 3L, 3L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L, 1L, 1L, 1L, 1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .addNftDeleteAllowance(owner, nft, List.of(2L))
                                .addNftDeleteAllowance(owner, nft, List.of(3L))
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED))
                .then(
                        // reset
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of("hedera.allowances.maxTransactionLimit", "20")));
    }

    private HapiSpec validatesSerialNums() {
        final String owner = "owner";
        final String spender = "spender";
        final String nft = "nft";
        return defaultHapiSpec("validatesSerialNums")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(nft)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey("supplyKey")
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(owner, nft),
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        cryptoTransfer(movingUnique(nft, 1L, 2L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L, 2L)),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L)),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(-1L))
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1000L))
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(3L))
                                .hasKnownStatus(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L, 1L, 2L)),
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of())
                                .hasPrecheck(EMPTY_ALLOWANCES))
                .then();
    }

    private HapiSpec invalidTokenTypeFailsInDeleteAllowance() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        return defaultHapiSpec("invalidTokenTypeFailsInDeleteAllowance")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(token)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey("supplyKey")
                                .maxSupply(1000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(owner, token),
                        mintToken(token, 500L).via("tokenMint"))
                .when()
                .then(
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, token, List.of(1L))
                                .hasPrecheck(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES));
    }

    private HapiSpec emptyAllowancesDeleteRejected() {
        final String owner = "owner";
        return defaultHapiSpec("emptyAllowancesDeleteRejected")
                .given(
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10))
                .when(cryptoDeleteAllowance().hasPrecheck(EMPTY_ALLOWANCES))
                .then();
    }

    private HapiSpec tokenNotAssociatedToAccountFailsOnDeleteAllowance() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("tokenNotAssociatedToAccountFailsOnDeleteAllowance")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"))
                .when(
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
                .then(
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(0)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(0)));
    }

    private HapiSpec canDeleteMultipleOwners() {
        final String owner1 = "owner1";
        final String owner2 = "owner2";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("canDeleteMultipleOwners")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner1)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(owner2)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(token)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey("supplyKey")
                                .maxSupply(10_000L)
                                .initialSupply(10L)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(nft)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey("supplyKey")
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(owner1, token, nft),
                        tokenAssociate(owner2, token, nft),
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c"),
                                                ByteString.copyFromUtf8("d"),
                                                ByteString.copyFromUtf8("e"),
                                                ByteString.copyFromUtf8("f")))
                                .via("nftTokenMint"),
                        mintToken(token, 1000L).via("tokenMint"),
                        cryptoTransfer(
                                moving(500, token).between(TOKEN_TREASURY, owner1),
                                moving(500, token).between(TOKEN_TREASURY, owner2),
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner1),
                                movingUnique(nft, 4L, 5L, 6L).between(TOKEN_TREASURY, owner2)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(owner1, spender, ONE_HBAR)
                                .addTokenAllowance(owner1, token, spender, 100L)
                                .addNftAllowance(owner1, nft, spender, false, List.of(1L))
                                .addCryptoAllowance(owner2, spender, 2 * ONE_HBAR)
                                .addTokenAllowance(owner2, token, spender, 300L)
                                .addNftAllowance(owner2, nft, spender, false, List.of(4L, 5L))
                                .signedBy(DEFAULT_PAYER, owner1, owner2)
                                .via("multiOwnerTxn"),
                        getAccountDetails(owner1)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .tokenAllowancesContaining(token, spender, 100L)
                                                .cryptoAllowancesContaining(spender, ONE_HBAR)
                                                .nftApprovedForAllAllowancesCount(0)),
                        getAccountDetails(owner2)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .tokenAllowancesContaining(token, spender, 300L)
                                                .cryptoAllowancesContaining(spender, 2 * ONE_HBAR)
                                                .nftApprovedForAllAllowancesCount(0)),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 4L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 5L).hasSpenderID(spender))
                .then(
                        cryptoDeleteAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftDeleteAllowance(owner1, nft, List.of(1L))
                                .addNftDeleteAllowance(owner2, nft, List.of(4L, 5L))
                                .signedBy(DEFAULT_PAYER, owner1, owner2)
                                .via("multiOwnerDeleteTxn"),
                        getAccountDetails(owner1)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(0)),
                        getAccountDetails(owner2)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(0)),
                        getTokenNftInfo(nft, 1L).hasNoSpender(),
                        getTokenNftInfo(nft, 4L).hasNoSpender(),
                        getTokenNftInfo(nft, 5L).hasNoSpender());
    }

    private HapiSpec noOwnerDefaultsToPayerInDeleteAllowance() {
        final String payer = "payer";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("noOwnerDefaultsToPayerInDeleteAllowance")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(payer)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender1).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        tokenAssociate(payer, token),
                        tokenAssociate(payer, nft),
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, payer)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(payer)
                                .addCryptoAllowance(payer, spender, 100L)
                                .addTokenAllowance(payer, token, spender, 100L)
                                .addNftAllowance(payer, nft, spender, true, List.of(1L))
                                .blankMemo()
                                .logged(),
                        cryptoDeleteAllowance()
                                .payingWith(payer)
                                .addNftDeleteAllowance(MISSING_OWNER, nft, List.of(1L))
                                .via("deleteTxn")
                                .blankMemo()
                                .logged(),
                        getTxnRecord("deleteTxn").logged())
                .then(
                        getAccountDetails(payer)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(1)),
                        getTokenNftInfo(nft, 1L).hasNoSpender());
    }

    private HapiSpec approvedForAllNotAffectedOnDelete() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("approvedForAllNotAffectedOnDelete")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addNftAllowance(owner, nft, spender, true, List.of(1L))
                                .via("otherAdjustTxn"),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(1)
                                                .tokenAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 100L)
                                                .tokenAllowancesContaining(token, spender, 100L)
                                                .nftApprovedAllowancesContaining(nft, spender)),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender))
                .then(
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(1L))
                                .blankMemo()
                                .via("cryptoDeleteAllowanceTxn")
                                .logged(),
                        getTxnRecord("cryptoDeleteAllowanceTxn").logged(),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(1)
                                                .tokenAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(1)
                                                .nftApprovedAllowancesContaining(nft, spender))
                                .logged(),
                        getTokenNftInfo(nft, 1L).hasNoSpender());
    }

    private HapiSpec happyPathWorks() {
        final String owner = "owner";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("happyPathWorks")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender1).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
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
                        mintToken(
                                        nft,
                                        List.of(
                                                ByteString.copyFromUtf8("a"),
                                                ByteString.copyFromUtf8("b"),
                                                ByteString.copyFromUtf8("c")))
                                .via("nftTokenMint"),
                        mintToken(token, 500L).via("tokenMint"),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addNftAllowance(owner, nft, spender1, true, List.of(3L))
                                .via("otherAdjustTxn"),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountDetailsWith()
                                                .cryptoAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(1)
                                                .tokenAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 100L)
                                                .tokenAllowancesContaining(token, spender, 100L)),
                        getTokenNftInfo(nft, 3L).hasSpenderID(spender1))
                .then(
                        cryptoDeleteAllowance()
                                .payingWith(owner)
                                .addNftDeleteAllowance(owner, nft, List.of(3L))
                                .blankMemo()
                                .via("cryptoDeleteAllowanceTxn")
                                .logged(),
                        getTxnRecord("cryptoDeleteAllowanceTxn").logged(),
                        validateChargedUsdWithin("cryptoDeleteAllowanceTxn", 0.05, 0.01),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(accountDetailsWith().nftApprovedForAllAllowancesCount(1)),
                        getTokenNftInfo(nft, 3L).hasNoSpender());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
