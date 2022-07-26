/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountWith;
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
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoApproveAllowanceSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(CryptoApproveAllowanceSuite.class);

    public static void main(String... args) {
        new CryptoApproveAllowanceSuite().runSuiteSync();
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    canHaveMultipleOwners(),
                    noOwnerDefaultsToPayer(),
                    invalidSpenderFails(),
                    invalidOwnerFails(),
                    happyPathWorks(),
                    emptyAllowancesRejected(),
                    spenderSameAsOwnerFails(),
                    negativeAmountFailsForFungible(),
                    tokenNotAssociatedToAccountFails(),
                    invalidTokenTypeFails(),
                    validatesSerialNums(),
                    tokenExceedsMaxSupplyFails(),
                    exceedsTransactionLimit(),
                    exceedsAccountLimit(),
                    succeedsWhenTokenPausedFrozenKycRevoked(),
                    serialsInAscendingOrder(),
                    feesAsExpected(),
                    cannotHaveMultipleAllowedSpendersForTheSameNFTSerial(),
                    canGrantNftAllowancesWithTreasuryOwner(),
                    canGrantFungibleAllowancesWithTreasuryOwner(),
                    approveForAllSpenderCanDelegateOnNFT(),
                    duplicateEntriesGetsReplacedWithDifferentTxn(),
                    duplicateKeysAndSerialsInSameTxnDoesntThrow()
                });
    }

    private HapiApiSpec duplicateKeysAndSerialsInSameTxnDoesntThrow() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("duplicateKeysAndSerialsInSameTxnDoesntThrow")
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
                                .addCryptoAllowance(owner, spender, 200L)
                                .blankMemo()
                                .logged(),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 200L)),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 300L)
                                .addTokenAllowance(owner, token, spender, 300L)
                                .addTokenAllowance(owner, token, spender, 500L)
                                .blankMemo()
                                .logged(),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 300L)
                                                .tokenAllowancesCount(1)
                                                .tokenAllowancesContaining(token, spender, 500L)),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 500L)
                                .addTokenAllowance(owner, token, spender, 600L)
                                .addNftAllowance(
                                        owner, nft, spender, false, List.of(1L, 2L, 2L, 2L, 2L))
                                .addNftAllowance(owner, nft, spender, true, List.of(1L))
                                .addNftAllowance(owner, nft, spender, false, List.of(2L))
                                .addNftAllowance(owner, nft, spender, true, List.of(3L))
                                .blankMemo()
                                .logged(),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 500L)
                                                .tokenAllowancesCount(1)
                                                .tokenAllowancesContaining(token, spender, 600L)
                                                .nftApprovedForAllAllowancesCount(1)))
                .then(
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 2L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 3L).hasSpenderID(spender));
    }

    private HapiApiSpec approveForAllSpenderCanDelegateOnNFT() {
        final String owner = "owner";
        final String delegatingSpender = "delegatingSpender";
        final String newSpender = "newSpender";
        final String nft = "nft";
        return defaultHapiSpec("ApproveForAllSpenderCanDelegateOnNFTs")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(delegatingSpender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(newSpender).balance(ONE_HUNDRED_HBARS),
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
                                                ByteString.copyFromUtf8("b")))
                                .via("nftTokenMint"),
                        cryptoTransfer(movingUnique(nft, 1L, 2L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(owner, nft, delegatingSpender, true, List.of(1L))
                                .addNftAllowance(owner, nft, newSpender, false, List.of(2L))
                                .signedBy(DEFAULT_PAYER, owner))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        owner,
                                        nft,
                                        newSpender,
                                        delegatingSpender,
                                        false,
                                        List.of(1L))
                                .signedBy(DEFAULT_PAYER, owner)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        owner,
                                        nft,
                                        delegatingSpender,
                                        newSpender,
                                        false,
                                        List.of(2L))
                                .signedBy(DEFAULT_PAYER, newSpender)
                                .hasPrecheck(DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        owner, nft, newSpender, delegatingSpender, true, List.of())
                                .signedBy(DEFAULT_PAYER, delegatingSpender)
                                .hasPrecheck(DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL),
                        getTokenNftInfo(nft, 2L).hasSpenderID(newSpender),
                        getTokenNftInfo(nft, 1L).hasSpenderID(delegatingSpender),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addDelegatedNftAllowance(
                                        owner,
                                        nft,
                                        newSpender,
                                        delegatingSpender,
                                        false,
                                        List.of(1L))
                                .signedBy(DEFAULT_PAYER, delegatingSpender),
                        getTokenNftInfo(nft, 1L).hasSpenderID(newSpender));
    }

    private HapiApiSpec canGrantFungibleAllowancesWithTreasuryOwner() {
        final String spender = "spender";
        final String otherReceiver = "otherReceiver";
        final String fungibleToken = "fungible";
        final String supplyKey = "supplyKey";
        return defaultHapiSpec("canGrantFungibleAllowancesWithTreasuryOwner")
                .given(
                        newKeyNamed(supplyKey),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(spender),
                        tokenCreate(fungibleToken)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(10000)
                                .initialSupply(5000),
                        cryptoCreate(otherReceiver)
                                .balance(ONE_HBAR)
                                .maxAutomaticTokenAssociations(1))
                .when(
                        cryptoApproveAllowance()
                                .addTokenAllowance(TOKEN_TREASURY, fungibleToken, spender, 10)
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER),
                        cryptoApproveAllowance()
                                .addTokenAllowance(TOKEN_TREASURY, fungibleToken, spender, 110)
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER))
                .then(
                        cryptoTransfer(
                                        movingWithAllowance(30, fungibleToken)
                                                .between(TOKEN_TREASURY, otherReceiver))
                                .payingWith(spender)
                                .signedBy(spender),
                        getAccountDetails(TOKEN_TREASURY)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .tokenAllowancesContaining(
                                                        fungibleToken, spender, 80))
                                .logged(),
                        getAccountDetails(TOKEN_TREASURY)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .tokenAllowancesContaining(
                                                        fungibleToken, spender, 80))
                                .logged());
    }

    private HapiApiSpec canGrantNftAllowancesWithTreasuryOwner() {
        final String spender = "spender";
        final String otherReceiver = "otherReceiver";
        final String nonFungibleToken = "nonFungible";
        final String supplyKey = "supplyKey";
        return defaultHapiSpec("canGrantNftAllowancesWithTreasuryOwner")
                .given(
                        newKeyNamed(supplyKey),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(spender),
                        tokenCreate(nonFungibleToken)
                                .supplyType(TokenSupplyType.INFINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(supplyKey),
                        mintToken(
                                nonFungibleToken,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"))),
                        cryptoCreate(otherReceiver)
                                .balance(ONE_HBAR)
                                .maxAutomaticTokenAssociations(1))
                .when(
                        cryptoApproveAllowance()
                                .addNftAllowance(
                                        TOKEN_TREASURY,
                                        nonFungibleToken,
                                        spender,
                                        false,
                                        List.of(4L))
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoApproveAllowance()
                                .addNftAllowance(
                                        TOKEN_TREASURY,
                                        nonFungibleToken,
                                        spender,
                                        false,
                                        List.of(1L, 3L))
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER),
                        cryptoDeleteAllowance()
                                .addNftDeleteAllowance(
                                        TOKEN_TREASURY, nonFungibleToken, List.of(4L))
                                .signedBy(TOKEN_TREASURY, DEFAULT_PAYER)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER))
                .then(
                        getAccountDetails(TOKEN_TREASURY).payingWith(GENESIS).logged(),
                        cryptoTransfer(
                                        movingUniqueWithAllowance(nonFungibleToken, 1L)
                                                .between(TOKEN_TREASURY, otherReceiver))
                                .payingWith(spender)
                                .signedBy(spender),
                        getAccountDetails(TOKEN_TREASURY)
                                .payingWith(GENESIS)
                                .has(accountWith().nftApprovedForAllAllowancesCount(0))
                                .logged());
    }

    private HapiApiSpec invalidOwnerFails() {
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
                        cryptoApproveAllowance()
                                .payingWith("payer")
                                .addCryptoAllowance(owner, spender, 100L)
                                .signedBy("payer", owner)
                                .blankMemo()
                                .hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
                        cryptoApproveAllowance()
                                .payingWith("payer")
                                .addTokenAllowance(owner, token, spender, 100L)
                                .signedBy("payer", owner)
                                .blankMemo()
                                .hasPrecheck(INVALID_ALLOWANCE_OWNER_ID),
                        cryptoApproveAllowance()
                                .payingWith("payer")
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .signedBy("payer", owner)
                                .via("baseApproveTxn")
                                .blankMemo()
                                .hasPrecheck(INVALID_ALLOWANCE_OWNER_ID))
                .then(
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .hasCostAnswerPrecheck(ACCOUNT_DELETED)
                                .hasAnswerOnlyPrecheck(ACCOUNT_DELETED));
    }

    private HapiApiSpec invalidSpenderFails() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("invalidSpenderFails")
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
                        cryptoDelete(spender),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 100L)
                                .blankMemo()
                                .hasKnownStatus(INVALID_ALLOWANCE_SPENDER_ID),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .blankMemo()
                                .hasKnownStatus(INVALID_ALLOWANCE_SPENDER_ID),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .via("baseApproveTxn")
                                .blankMemo()
                                .hasKnownStatus(INVALID_ALLOWANCE_SPENDER_ID))
                .then();
    }

    private HapiApiSpec noOwnerDefaultsToPayer() {
        final String payer = "payer";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("noOwnerDefaultsToPayer")
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
                                .addCryptoAllowance(MISSING_OWNER, spender1, 100L)
                                .addTokenAllowance(MISSING_OWNER, token, spender, 100L)
                                .addNftAllowance(MISSING_OWNER, nft, spender, false, List.of(1L))
                                .via("approveTxn")
                                .blankMemo()
                                .logged(),
                        getTxnRecord("approveTxn").logged())
                .then(
                        validateChargedUsdWithin("approveTxn", 0.05238, 0.01),
                        getAccountDetails(payer)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender1, 100L)
                                                .tokenAllowancesContaining(token, spender, 100L)));
    }

    private HapiApiSpec canHaveMultipleOwners() {
        final String owner1 = "owner1";
        final String owner2 = "owner2";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("canHaveMultipleOwners")
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
                                .addCryptoAllowance(owner2, spender, ONE_HBAR)
                                .addTokenAllowance(owner2, token, spender, 100L)
                                .addNftAllowance(owner2, nft, spender, false, List.of(4L))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(owner1, spender, ONE_HBAR)
                                .addTokenAllowance(owner1, token, spender, 100L)
                                .addNftAllowance(owner1, nft, spender, false, List.of(1L))
                                .addCryptoAllowance(owner2, spender, ONE_HBAR)
                                .addTokenAllowance(owner2, token, spender, 100L)
                                .addNftAllowance(owner2, nft, spender, false, List.of(4L))
                                .signedBy(DEFAULT_PAYER, owner1)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(owner1, spender, ONE_HBAR)
                                .addTokenAllowance(owner1, token, spender, 100L)
                                .addNftAllowance(owner1, nft, spender, false, List.of(1L))
                                .addCryptoAllowance(owner2, spender, ONE_HBAR)
                                .addTokenAllowance(owner2, token, spender, 100L)
                                .addNftAllowance(owner2, nft, spender, false, List.of(4L))
                                .signedBy(DEFAULT_PAYER, owner2)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addCryptoAllowance(owner1, spender, ONE_HBAR)
                                .addTokenAllowance(owner1, token, spender, 100L)
                                .addNftAllowance(owner1, nft, spender, false, List.of(1L))
                                .addCryptoAllowance(owner2, spender, 2 * ONE_HBAR)
                                .addTokenAllowance(owner2, token, spender, 300L)
                                .addNftAllowance(owner2, nft, spender, false, List.of(4L, 5L))
                                .signedBy(DEFAULT_PAYER, owner1, owner2))
                .then(
                        getAccountDetails(owner1)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .tokenAllowancesContaining(token, spender, 100L)
                                                .cryptoAllowancesContaining(spender, ONE_HBAR)),
                        getAccountDetails(owner2)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .tokenAllowancesContaining(token, spender, 300L)
                                                .cryptoAllowancesContaining(
                                                        spender, 2 * ONE_HBAR)));
    }

    private HapiApiSpec feesAsExpected() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("feesAsExpected")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
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
                                .addCryptoAllowance(owner, spender, 100L)
                                .via("approve")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approve", 0.05, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .via("approveTokenTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveTokenTxn", 0.05012, 0.01))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .via("approveNftTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveNftTxn", 0.050101, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, "spender1", true, List.of())
                                .via("approveForAllNftTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveForAllNftTxn", 0.05, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, "spender2", 100L)
                                .addTokenAllowance(owner, token, "spender2", 100L)
                                .addNftAllowance(owner, nft, "spender2", false, List.of(1L))
                                .via("approveTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveTxn", 0.05238, 0.01),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(2)
                                                .nftApprovedForAllAllowancesCount(1)
                                                .tokenAllowancesCount(2)
                                                .cryptoAllowancesContaining("spender2", 100L)
                                                .tokenAllowancesContaining(
                                                        token, "spender2", 100L)),
                        /* edit existing allowances */
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, "spender2", 200L)
                                .via("approveModifyCryptoTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveModifyCryptoTxn", 0.049375, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, "spender2", 200L)
                                .via("approveModifyTokenTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveModifyTokenTxn", 0.04943, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, "spender1", false, List.of())
                                .via("approveModifyNftTxn")
                                .fee(ONE_HBAR)
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("approveModifyNftTxn", 0.049375, 0.01),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(2)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(2)
                                                .cryptoAllowancesContaining("spender2", 200L)
                                                .tokenAllowancesContaining(
                                                        token, "spender2", 200L)));
    }

    private HapiApiSpec serialsInAscendingOrder() {
        final String owner = "owner";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String nft = "nft";
        return defaultHapiSpec("serialsInAscendingOrder")
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
                                                ByteString.copyFromUtf8("c"),
                                                ByteString.copyFromUtf8("d")))
                                .via("nftTokenMint"),
                        cryptoTransfer(
                                movingUnique(nft, 1L, 2L, 3L, 4L).between(TOKEN_TREASURY, owner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, true, List.of(1L))
                                .fee(ONE_HBAR),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender1, false, List.of(4L, 2L, 3L))
                                .fee(ONE_HBAR))
                .then(
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .logged()
                                .has(
                                        accountWith()
                                                .nftApprovedForAllAllowancesCount(1)
                                                .nftApprovedAllowancesContaining(nft, spender)));
    }

    private HapiApiSpec succeedsWhenTokenPausedFrozenKycRevoked() {
        final String owner = "owner";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String spender2 = "spender2";
        final String spender3 = "spender3";
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
                        cryptoCreate(spender2).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender3).balance(ONE_HUNDRED_HBARS),
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
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .fee(ONE_HBAR),
                        revokeTokenKyc(token, owner),
                        revokeTokenKyc(nft, owner),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender1, 100L)
                                .addNftAllowance(owner, nft, spender1, false, List.of(1L))
                                .fee(ONE_HBAR))
                .then(
                        tokenPause(token),
                        tokenPause(nft),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender2, 100L)
                                .addNftAllowance(owner, nft, spender2, false, List.of(2L))
                                .fee(ONE_HBAR),
                        tokenUnpause(token),
                        tokenUnpause(nft),
                        tokenFreeze(token, owner),
                        tokenFreeze(nft, owner),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender3, 100L)
                                .addNftAllowance(owner, nft, spender3, false, List.of(3L))
                                .fee(ONE_HBAR),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(0)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(4)));
    }

    private HapiApiSpec exceedsTransactionLimit() {
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
                                        Map.of(
                                                "hedera.allowances.maxTransactionLimit", "4",
                                                "hedera.allowances.maxAccountLimit", "5")),
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
                                .addCryptoAllowance(owner, spender, 100L)
                                .addCryptoAllowance(owner, spender1, 100L)
                                .addCryptoAllowance(owner, spender2, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(
                                        owner, nft, spender, false, List.of(1L, 1L, 1L, 1L, 1L))
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 100L)
                                .addCryptoAllowance(owner, spender, 200L)
                                .addCryptoAllowance(owner, spender, 100L)
                                .addCryptoAllowance(owner, spender, 200L)
                                .addCryptoAllowance(owner, spender, 200L)
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .hasPrecheck(MAX_ALLOWANCES_EXCEEDED))
                .then(
                        // reset
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                "hedera.allowances.maxTransactionLimit", "20",
                                                "hedera.allowances.maxAccountLimit", "100")));
    }

    private HapiApiSpec exceedsAccountLimit() {
        final String owner = "owner";
        final String spender = "spender";
        final String spender1 = "spender1";
        final String spender2 = "spender2";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("exceedsAccountLimit")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                "hedera.allowances.maxAccountLimit", "3",
                                                "hedera.allowances.maxTransactionLimit", "5")),
                        newKeyNamed("supplyKey"),
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
                                .addCryptoAllowance(owner, spender, 100L)
                                .addCryptoAllowance(owner, spender2, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .fee(ONE_HBAR),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(2)
                                                .tokenAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(0)))
                .then(
                        cryptoCreate("spender3").balance(ONE_HUNDRED_HBARS),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender1, 100L)
                                .fee(ONE_HBAR)
                                .hasKnownStatus(MAX_ALLOWANCES_EXCEEDED),
                        // reset
                        fileUpdate(APP_PROPERTIES)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .overridingProps(
                                        Map.of(
                                                "hedera.allowances.maxTransactionLimit", "20",
                                                "hedera.allowances.maxAccountLimit", "100")));
    }

    private HapiApiSpec tokenExceedsMaxSupplyFails() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        return defaultHapiSpec("tokenExceedsMaxSupplyFails")
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
                .when(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender, 5000L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY))
                .then();
    }

    private HapiApiSpec validatesSerialNums() {
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
                                .addNftAllowance(owner, nft, spender, false, List.of(1000L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(-1000L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(3L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasKnownStatus(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(2L, 2L, 2L))
                                .fee(ONE_HUNDRED_HBARS))
                .then();
    }

    private HapiApiSpec invalidTokenTypeFails() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("invalidTokenTypeFails")
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
                                .addTokenAllowance(owner, nft, spender, 100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, token, spender, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES))
                .then();
    }

    private HapiApiSpec emptyAllowancesRejected() {
        final String owner = "owner";
        return defaultHapiSpec("emptyAllowancesRejected")
                .given(
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10))
                .when(cryptoApproveAllowance().hasPrecheck(EMPTY_ALLOWANCES).fee(ONE_HUNDRED_HBARS))
                .then();
    }

    private HapiApiSpec tokenNotAssociatedToAccountFails() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("tokenNotAssociatedToAccountFails")
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
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
                .then(
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(0)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(0)));
    }

    private HapiApiSpec spenderSameAsOwnerFails() {
        final String owner = "owner";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("spenderSameAsOwnerFails")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
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
                                .addCryptoAllowance(owner, owner, 100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, owner, 100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addNftAllowance(owner, nft, owner, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER))
                .then(
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(0)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(0)));
    }

    private HapiApiSpec negativeAmountFailsForFungible() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("negativeAmountFailsForFungible")
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
                                .addCryptoAllowance(owner, spender, -100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(NEGATIVE_ALLOWANCE_AMOUNT),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addTokenAllowance(owner, token, spender, -100L)
                                .fee(ONE_HUNDRED_HBARS)
                                .hasPrecheck(NEGATIVE_ALLOWANCE_AMOUNT))
                .then(
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(0)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(0)));
    }

    private HapiApiSpec happyPathWorks() {
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
                                .via("baseApproveTxn")
                                .blankMemo()
                                .logged(),
                        validateChargedUsdWithin("baseApproveTxn", 0.05, 0.01),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender1, 100L)
                                .addTokenAllowance(owner, token, spender, 100L)
                                .addNftAllowance(owner, nft, spender, false, List.of(1L))
                                .via("approveTxn")
                                .blankMemo()
                                .logged())
                .then(
                        validateChargedUsdWithin("approveTxn", 0.05238, 0.01),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(2)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 100L)
                                                .tokenAllowancesContaining(token, spender, 100L)),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender));
    }

    private HapiApiSpec duplicateEntriesGetsReplacedWithDifferentTxn() {
        final String owner = "owner";
        final String spender = "spender";
        final String token = "token";
        final String nft = "nft";
        return defaultHapiSpec("duplicateEntriesGetsReplacedWithDifferentTxn")
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
                                .addNftAllowance(owner, nft, spender, true, List.of(1L, 2L))
                                .via("baseApproveTxn")
                                .blankMemo()
                                .logged(),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(1)
                                                .tokenAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 100L)
                                                .tokenAllowancesContaining(token, spender, 100L)
                                                .nftApprovedAllowancesContaining(nft, spender)),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 2L).hasSpenderID(spender),
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 200L)
                                .addTokenAllowance(owner, token, spender, 300L)
                                .addNftAllowance(owner, nft, spender, false, List.of(3L))
                                .via("duplicateAllowances"),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 2L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 3L).hasSpenderID(spender),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(1)
                                                .nftApprovedForAllAllowancesCount(0)
                                                .tokenAllowancesCount(1)
                                                .cryptoAllowancesContaining(spender, 200L)
                                                .tokenAllowancesContaining(token, spender, 300L)))
                .then(
                        cryptoApproveAllowance()
                                .payingWith(owner)
                                .addCryptoAllowance(owner, spender, 0L)
                                .addTokenAllowance(owner, token, spender, 0L)
                                .addNftAllowance(owner, nft, spender, true, List.of())
                                .via("removeAllowances"),
                        getAccountDetails(owner)
                                .payingWith(GENESIS)
                                .has(
                                        accountWith()
                                                .cryptoAllowancesCount(0)
                                                .nftApprovedForAllAllowancesCount(1)
                                                .tokenAllowancesCount(0)
                                                .nftApprovedAllowancesContaining(nft, spender)),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 2L).hasSpenderID(spender),
                        getTokenNftInfo(nft, 3L).hasSpenderID(spender));
    }

    private HapiApiSpec cannotHaveMultipleAllowedSpendersForTheSameNFTSerial() {
        final String owner1 = "owner1";
        final String spender = "spender";
        final String spender2 = "spender2";
        final String receiver = "receiver";
        final String nft = "nft";
        return defaultHapiSpec("CannotHaveMultipleAllowedSpendersForTheSameNFTSerial")
                .given(
                        newKeyNamed("supplyKey"),
                        cryptoCreate(owner1)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(spender2).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(receiver).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(receiver).balance(ONE_HUNDRED_HBARS),
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
                        tokenAssociate(owner1, nft),
                        tokenAssociate(receiver, nft),
                        mintToken(nft, List.of(ByteString.copyFromUtf8("a"))).via("nftTokenMint"),
                        cryptoTransfer(movingUnique(nft, 1L).between(TOKEN_TREASURY, owner1)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(owner1, nft, spender, true, List.of(1L))
                                .signedBy(DEFAULT_PAYER, owner1),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(owner1, nft, spender2, true, List.of(1L))
                                .signedBy(DEFAULT_PAYER, owner1),
                        getTokenNftInfo(nft, 1L).hasSpenderID(spender2).logged(),
                        getAccountDetails(owner1)
                                .payingWith(GENESIS)
                                .has(accountWith().nftApprovedForAllAllowancesCount(2)))
                .then(
                        cryptoTransfer(movingUniqueWithAllowance(nft, 1).between(owner1, receiver))
                                .payingWith(spender2)
                                .signedBy(spender2),
                        getTokenNftInfo(nft, 1L).hasNoSpender().logged(),
                        cryptoTransfer(movingUnique(nft, 1).between(receiver, owner1)),
                        cryptoTransfer(movingUniqueWithAllowance(nft, 1).between(owner1, receiver))
                                .payingWith(spender2)
                                .signedBy(spender2),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(owner1, nft, spender2, false, List.of())
                                .signedBy(DEFAULT_PAYER, owner1),
                        getAccountDetails(owner1)
                                .payingWith(GENESIS)
                                .has(accountWith().nftApprovedForAllAllowancesCount(1)),
                        cryptoTransfer(movingUnique(nft, 1).between(receiver, owner1)),
                        cryptoTransfer(movingUniqueWithAllowance(nft, 1).between(owner1, receiver))
                                .payingWith(spender2)
                                .signedBy(spender2)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE),
                        cryptoApproveAllowance()
                                .payingWith(DEFAULT_PAYER)
                                .addNftAllowance(owner1, nft, spender2, false, List.of(1L))
                                .signedBy(DEFAULT_PAYER, owner1),
                        cryptoTransfer(movingUniqueWithAllowance(nft, 1).between(owner1, receiver))
                                .payingWith(spender2)
                                .signedBy(spender2),
                        cryptoTransfer(movingUnique(nft, 1).between(receiver, owner1)),
                        cryptoTransfer(movingUniqueWithAllowance(nft, 1).between(owner1, receiver))
                                .payingWith(spender2)
                                .signedBy(spender2)
                                .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
