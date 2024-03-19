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
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.*;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.*;
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
public class TransferWithCustomRoyaltyFees2 extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransferWithCustomRoyaltyFees2.class);
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
    private static final String feeDenom3 = "denom3";
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
        new TransferWithCustomRoyaltyFees2().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                transferNonFungibleWithRoyaltyHtsFee2Transactions(),
                transferNonFungibleWithRoyaltyHtsFee2TokenFees(),
                transferNonFungibleWithRoyaltyHtsFeeMinFee(),
                transferNonFungibleWithRoyaltyAllowancePassFromSpenderToOwner());
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyHtsFee2Transactions() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyHtsFee2Transactions")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(0L),
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
                        moving(0, feeDenom).between(tokenReceiver, tokenOwner),
                        moving(4, feeDenom).between(tokenReceiver, tokenOwner))
                )
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 2));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyHtsFee2TokenFees() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyHtsFee2TokenFees")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(0L),
                        tokenCreate(feeDenom).treasury(tokenReceiver).initialSupply(4),
                        tokenCreate(feeDenom2).treasury(tokenReceiver).initialSupply(4),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(tokenOwner, feeDenom2),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenAssociate(htsCollector, feeDenom2),
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
                        moving(4, feeDenom).between(tokenReceiver, tokenOwner),
                        moving(4, feeDenom2).between(tokenReceiver, tokenOwner))
                )
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 2),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom2, 2));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyHtsFeeMinFee() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyHtsFeeMinFee")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(0L),
                        tokenCreate(feeDenom).treasury(tokenReceiver).initialSupply(4),
                        tokenCreate(feeDenom2).treasury(tokenReceiver).initialSupply(4),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenAssociate(tokenOwner, feeDenom2),
                        tokenAssociate(htsCollector, feeDenom2),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(1, Long.MAX_VALUE, fixedHtsFeeInheritingRoyaltyCollector(4, feeDenom2), htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(
                        movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver),
                        moving(1, feeDenom).between(tokenReceiver, tokenOwner))
                )
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(feeDenom, 3),
                        getAccountBalance(tokenReceiver).hasTokenBalance(feeDenom2, 4),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom2, 0));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyFallbackAllowance() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyFallbackAllowance")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(feeDenom).treasury(tokenTreasury).initialSupply(10),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(htsCollector, feeDenom),
                        cryptoTransfer(moving(10, feeDenom).between(tokenTreasury, tokenOwner)),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(1L, 2L, fixedHtsFeeInheritingRoyaltyCollector(8, feeDenom), htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addTokenAllowance(tokenOwner, feeDenom, tokenReceiver, 10L)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(feeDenom, tokenReceiver, 10L)),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                )
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(feeDenom, 3),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 10));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyAllowancePassFromSpenderToOwner() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyAllowancePassFromSpenderToOwner")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector).balance(0L),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(feeDenom).treasury(tokenTreasury).initialSupply(20),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenAssociate(tokenReceiver, feeDenom),
                        tokenAssociate(htsCollector, feeDenom),
                        cryptoTransfer(moving(20, feeDenom).between(tokenTreasury, tokenOwner)),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeNoFallback(1L, 5L, htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addTokenAllowance(tokenOwner, feeDenom, tokenReceiver, 10L)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(feeDenom, tokenReceiver, 10L)),
                        cryptoTransfer(
                                movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver),
                                movingWithAllowance(10L, feeDenom).between(tokenOwner, tokenOwner))
                                .fee(ONE_HUNDRED_HBARS)
                                .via("hbarFixedFee")
                                .payingWith(tokenReceiver)
                                .signedBy(tokenReceiver, tokenOwner)
                )
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(feeDenom, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0));
    }

    @HapiTest
    public HapiSpec transferNonFungibleWithRoyaltyAllCollectorsExempt() {
        return defaultHapiSpec("transferNonFungibleWithRoyaltyAllCollectorsExempt")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(alice).balance(ONE_MILLION_HBARS),
                        cryptoCreate(bob).balance(ONE_MILLION_HBARS),
                        cryptoCreate(carol).balance(ONE_MILLION_HBARS),
                        cryptoCreate(htsCollector).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury).balance(ONE_MILLION_HBARS),
                        tokenCreate(feeDenom).treasury(alice).initialSupply(10),
                        tokenCreate(feeDenom2).treasury(bob).initialSupply(20),
                        tokenCreate(feeDenom3).treasury(tokenTreasury)
                                .initialSupply(10)
                                .withCustom(fixedHbarFee(10L, carol, true)),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .withCustom(royaltyFeeWithFallback(1L, 2L, fixedHtsFeeInheritingRoyaltyCollector(10, feeDenom2), htsCollector)),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(carol, nonFungibleToken),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, carol)),
                        cryptoTransfer(moving(10, feeDenom2).between(bob, tokenReceiver)))
                .when(
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(carol, tokenReceiver))
                )
                .then(
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(feeDenom2, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 10));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
