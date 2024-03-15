/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeNoFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(CRYPTO)
public class TransferWithCustomRoyaltyFees extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransferWithCustomRoyaltyFees.class);
    private static final long hbarFee = 1_000L;
    private static final long htsFee = 100L;
    private static final long tokenTotal = 1_000L;
    private static final long numerator = 1L;
    private static final long denominator = 10L;
    private static final long minHtsFee = 2L;
    private static final long maxHtsFee = 10L;

    private static final String fungibleToken = "fungibleWithCustomFees";
    private static final String fungibleToken2 = "fungibleWithCustomFees2";
    private static final String nonFungibleToken = "nonFungibleWithCustomFees";
    private static final String feeDenom = "denom";
    private static final String feeDenom2 = "denom2";
    private static final String hbarCollector = "hbarFee";
    private static final String htsCollector = "denomFee";
    private static final String htsCollector2 = "denomFee2";
    private static final String tokenReceiver = "receiver";
    private static final String tokenTreasury = "tokenTreasury";
    private static final String spender = "spender";
    private static final String NFT_KEY = "nftKey";
    private static final String tokenOwner = "tokenOwner";
    private static final String alice = "alice";
    private static final long aliceFee = 100L;
    private static final String bob = "bob";
    private static final long bobFee = 200L;
    private static final String carol = "carol";
    private static final long carolFee = 300L;

    public static void main(String... args) {
        new TransferWithCustomRoyaltyFees().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                transferNonFungibleWithRoyaltyHbarFee(),
                transferNonFungibleWithRoyaltyFungibleFee(),
                transferNonFungibleWithRoyaltyFallbackHbarFee(),
                transferNonFungibleWithRoyaltyFallbackFungibleFee(),
                transferNonFungibleWithRoyaltyHbarFeeInsufficientBalance(),
                transferNonFungibleWithRoyaltyFungibleFeeInsufficientBalance(),
                transferNonFungibleWithRoyaltyFallbackHbarFeeInsufficientBalance(),
                transferNonFungibleWithRoyaltyFallbackFungibleFeeInsufficientBalance(),
                transferNonFungibleWithRoyaltyFallbackFungibleFeeNoAssociation(),
                transferNonFungibleWithRoyaltyFungibleFeeNoAssociation());
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyHbarFee() {
        final var fourHundredHbars = 4 * ONE_HUNDRED_HBARS;
        final var twoHundredHbars = 2 * ONE_HUNDRED_HBARS;
        return defaultHapiSpec("transferNonFungibleWithRoyaltyHbarFee")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(0L),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeNoFallback(1, 2, hbarCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(
                        movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver),
                        movingHbar(fourHundredHbars).between(tokenReceiver, tokenOwner)))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTinyBars(twoHundredHbars)
                                .hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(hbarCollector).hasTinyBars(twoHundredHbars));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFungibleFee() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFungibleFee")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(feeDenom).treasury(tokenReceiver).initialSupply(4),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeNoFallback(1, 2, htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(
                        movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver),
                        moving(4, feeDenom).between(tokenReceiver, tokenOwner)))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, 2),
                        getAccountBalance(tokenReceiver)
                                .hasTokenBalance(nonFungibleToken, 1)
                                .hasTokenBalance(feeDenom, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 2));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFallbackHbarFee() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFallbackHbarFee")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHbarFeeInheritingRoyaltyCollector(100), hbarCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                        .signedByPayerAnd(tokenReceiver, tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver)
                                .hasTokenBalance(nonFungibleToken, 1)
                                .hasTinyBars(ONE_MILLION_HBARS - 100),
                        getAccountBalance(hbarCollector).hasTinyBars(100));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFallbackFungibleFee() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFallbackFungibleFee")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(feeDenom).treasury(tokenReceiver).initialSupply(5),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(5, feeDenom), htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                        .signedByPayerAnd(tokenReceiver, tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver)
                                .hasTokenBalance(nonFungibleToken, 1)
                                .hasTokenBalance(feeDenom, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 5));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyHbarFeeInsufficientBalance() {
        final var fourHundredHbars = 4 * ONE_HUNDRED_HBARS;
        return defaultHapiSpec("transferNonFungibleWithRoyaltyHbarFeeInsufficientBalance")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(0L),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(0L),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeNoFallback(1, 2, hbarCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when()
                .then(
                        cryptoTransfer(
                                        movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver),
                                        movingHbar(fourHundredHbars).between(tokenReceiver, tokenOwner))
                                .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
                        getAccountBalance(hbarCollector).hasTinyBars(0));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFungibleFeeInsufficientBalance() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFungibleFeeInsufficientBalance")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(feeDenom).treasury(tokenReceiver).initialSupply(0),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeNoFallback(1, 2, htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when()
                .then(
                        cryptoTransfer(
                                        movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver),
                                        moving(4, feeDenom).between(tokenReceiver, tokenOwner))
                                .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFallbackHbarFeeInsufficientBalance() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFallbackHbarFeeInsufficientBalance")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(0L),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHbarFeeInheritingRoyaltyCollector(100), hbarCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when()
                .then(
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                                .signedByPayerAnd(tokenReceiver, tokenOwner)
                                .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
                        getAccountBalance(hbarCollector).hasTinyBars(0));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFallbackFungibleFeeInsufficientBalance() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFallbackFungibleFeeInsufficientBalance")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(feeDenom).treasury(tokenReceiver).initialSupply(0),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(5, feeDenom), htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when()
                .then(
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                                .signedByPayerAnd(tokenReceiver, tokenOwner)
                                .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFallbackFungibleFeeNoAssociation() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFallbackFungibleFeeNoAssociation")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(feeDenom).treasury(tokenTreasury).initialSupply(0),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(
                                        1, 2, fixedHtsFeeInheritingRoyaltyCollector(5, feeDenom), htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when()
                .then(
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                                .signedByPayerAnd(tokenReceiver, tokenOwner)
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFungibleFeeNoAssociation() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFungibleFeeNoAssociation")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner),
                        tokenCreate(feeDenom).treasury(tokenTreasury).initialSupply(0),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeNoFallback(1, 2, htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when()
                .then(
                        cryptoTransfer(
                                        movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver),
                                        moving(4, feeDenom).between(tokenReceiver, tokenOwner))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
