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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(CRYPTO)
public class TransferWithCustomFees extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransferWithCustomFees.class);
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
        new TransferWithCustomFees().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            transferFungibleTokenWithFixedHbarCustomFees(),
            transferFungibleTokenWithFixedHtsCustomFees(),
            transferNonFungibleTokenWithFixedHbarCustomFees(),
            transferNonFungibleTokenWithFixedHtsCustomFees(),
            transferApprovedFungibleTokenWithFixedHbarCustomFee(),
            transferApprovedFungibleTokenWithFixedHtsCustomFeeAsOwner(),
            transferApprovedFungibleTokenWithFixedHtsCustomFeeAsSpender(),
            transferApprovedNonFungibleTokenWithFixedHbarCustomFee(),
            transferApprovedNonFungibleTokenWithFixedHtsCustomFeesAsOwner(),
            transferApprovedNonFungibleTokenWithFixedHtsCustomFeeAsSpender(),
            transferFungibleTokenWithThreeFixedHtsCustomFeesWithoutAllCollectorsExempt(),
            transferFungibleTokenWithThreeFixedHtsCustomFeesWithAllCollectorsExempt(),
            transferFungibleTokenWithFixedHtsCustomFees2Layers(),
            transferNonFungibleTokenWithFixedHtsCustomFees2Layers(),
            transferMaxFungibleTokenWith10FixedHtsCustomFees2Layers(),
            multipleTransfersWithMultipleCustomFees(),
            transferWithFractionalCustomFee(),
            transferWithInsufficientCustomFees(),
        });
    }

    @HapiTest
    public HapiSpec transferFungibleTokenWithFixedHbarCustomFees() {
        return defaultHapiSpec("transferFungibleTokenWithFixedHbarCustomFees")
                .given(
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        cryptoTransfer(moving(1000, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(1, fungibleToken).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .via("hbarFixedFee")
                        .payingWith(tokenOwner))
                .then(withOpContext((spec, log) -> {
                    final var record = getTxnRecord("hbarFixedFee");
                    allRunFor(spec, record);
                    final var txFee = record.getResponseRecord().getTransactionFee();

                    getAccountBalance(tokenOwner)
                            .hasTinyBars(ONE_MILLION_HBARS - (txFee + hbarFee))
                            .hasTokenBalance(fungibleToken, 999);
                    getAccountBalance(tokenReceiver).hasTokenBalance(fungibleToken, 1);
                    getAccountBalance(hbarCollector).hasTinyBars(hbarFee);
                }));
    }

    @HapiTest
    public HapiSpec transferFungibleTokenWithFixedHtsCustomFees() {
        return defaultHapiSpec("transferFungibleTokenWithFixedHtsCustomFees")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        cryptoTransfer(moving(1000, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(1, fungibleToken).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(fungibleToken, 999)
                                .hasTokenBalance(feeDenom, tokenTotal - htsFee),
                        getAccountBalance(tokenReceiver).hasTokenBalance(fungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferNonFungibleTokenWithFixedHbarCustomFees() {
        return defaultHapiSpec("transferNonFungibleTokenWithFixedHbarCustomFees")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector)),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .via("hbarFixedFee")
                        .payingWith(tokenOwner))
                .then(withOpContext((spec, log) -> {
                    final var record = getTxnRecord("hbarFixedFee");
                    allRunFor(spec, record);
                    final var txFee = record.getResponseRecord().getTransactionFee();

                    getAccountBalance(tokenOwner)
                            .hasTinyBars(ONE_MILLION_HBARS - (txFee + hbarFee))
                            .hasTokenBalance(nonFungibleToken, 0);
                    getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1);
                    getAccountBalance(hbarCollector).hasTinyBars(hbarFee);
                }));
    }

    @HapiTest
    public HapiSpec transferNonFungibleTokenWithFixedHtsCustomFees() {
        return defaultHapiSpec("transferNonFungibleTokenWithFixedHtsCustomFees")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(nonFungibleToken, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferApprovedFungibleTokenWithFixedHbarCustomFee() {
        return defaultHapiSpec("transferApprovedFungibleTokenWithFixedHbarCustomFee")
                .given(
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        tokenAssociate(spender, fungibleToken),
                        cryptoTransfer(moving(tokenTotal, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addTokenAllowance(tokenOwner, fungibleToken, spender, 10L)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(fungibleToken, spender, 10L)),
                        cryptoTransfer(movingWithAllowance(1L, fungibleToken).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .via("hbarFixedFee")
                                .payingWith(spender)
                                .signedBy(spender))
                .then(withOpContext((spec, log) -> {
                    final var record = getTxnRecord("hbarFixedFee");
                    allRunFor(spec, record);
                    final var txFee = record.getResponseRecord().getTransactionFee();

                    getAccountBalance(tokenOwner)
                            .hasTinyBars(ONE_MILLION_HBARS - (txFee + hbarFee))
                            .hasTokenBalance(fungibleToken, 999);
                    getAccountBalance(spender).hasTokenBalance(fungibleToken, 0);
                    getAccountBalance(tokenReceiver).hasTokenBalance(fungibleToken, 1);
                    getAccountBalance(hbarCollector).hasTinyBars(hbarFee);
                    getAccountDetails(tokenOwner)
                            .has(accountDetailsWith().tokenAllowancesContaining(fungibleToken, spender, 9L));
                }));
    }

    @HapiTest
    public HapiSpec transferApprovedFungibleTokenWithFixedHtsCustomFeeAsOwner() {
        return defaultHapiSpec("transferApprovedFungibleTokenWithFixedHtsCustomFeeAsOwner")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(spender).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        tokenAssociate(spender, fungibleToken),
                        cryptoTransfer(moving(tokenTotal, fungibleToken).between(tokenTreasury, tokenOwner)),
                        cryptoTransfer(moving(200L, feeDenom).between(spender, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addTokenAllowance(tokenOwner, fungibleToken, spender, 10L)
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(fungibleToken, spender, 10L)),
                        cryptoTransfer(movingWithAllowance(1L, fungibleToken).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(fungibleToken, spender, 9L)),
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(fungibleToken, 999)
                                .hasTokenBalance(feeDenom, 200L - htsFee),
                        getAccountBalance(spender)
                                .hasTokenBalance(fungibleToken, 0)
                                .hasTokenBalance(feeDenom, 800L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(fungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferApprovedFungibleTokenWithFixedHtsCustomFeeAsSpender() {
        return defaultHapiSpec("transferApprovedFungibleTokenWithFixedHtsCustomFeeAsSpender")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenAssociate(spender, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        tokenAssociate(spender, fungibleToken),
                        cryptoTransfer(moving(tokenTotal, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addTokenAllowance(tokenOwner, fungibleToken, spender, 10L)
                                .fee(ONE_HUNDRED_HBARS)
                                .signedBy(tokenOwner)
                                .payingWith(tokenOwner),
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(fungibleToken, spender, 10L)),
                        cryptoTransfer(movingWithAllowance(1L, fungibleToken).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountDetails(tokenOwner)
                                .has(accountDetailsWith().tokenAllowancesContaining(fungibleToken, spender, 9L)),
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(fungibleToken, 999)
                                .hasTokenBalance(feeDenom, tokenTotal - htsFee),
                        getAccountBalance(spender)
                                .hasTokenBalance(fungibleToken, 0)
                                .hasTokenBalance(feeDenom, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(fungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferApprovedNonFungibleTokenWithFixedHbarCustomFee() {
        return defaultHapiSpec("transferApprovedNonFungibleTokenWithFixedHbarCustomFee")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector)),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        tokenAssociate(spender, nonFungibleToken),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addNftAllowance(tokenOwner, nonFungibleToken, spender, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 1L)
                                        .between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .via("hbarFixedFee")
                                .payingWith(spender)
                                .signedBy(spender))
                .then(withOpContext((spec, log) -> {
                    final var record = getTxnRecord("hbarFixedFee");
                    allRunFor(spec, record);
                    final var txFee = record.getResponseRecord().getTransactionFee();

                    getAccountBalance(tokenOwner)
                            .hasTinyBars(ONE_MILLION_HBARS - (txFee + hbarFee))
                            .hasTokenBalance(nonFungibleToken, 0);
                    getAccountBalance(spender).hasTokenBalance(nonFungibleToken, 0);
                    getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1);
                    getAccountBalance(hbarCollector).hasTinyBars(hbarFee);
                }));
    }

    @HapiTest
    public HapiSpec transferApprovedNonFungibleTokenWithFixedHtsCustomFeesAsOwner() {
        return defaultHapiSpec("transferApprovedNonFungibleTokenWithFixedHtsCustomFeesAsOwner")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(spender).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenAssociate(tokenOwner, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        tokenAssociate(spender, nonFungibleToken),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)),
                        cryptoTransfer(moving(200L, feeDenom).between(spender, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addNftAllowance(tokenOwner, nonFungibleToken, spender, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 1L)
                                        .between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, 200L - htsFee),
                        getAccountBalance(spender)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, 800L),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferApprovedNonFungibleTokenWithFixedHtsCustomFeeAsSpender() {
        return defaultHapiSpec("transferApprovedNonFungibleTokenWithFixedHtsCustomFeeAsSpender")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenAssociate(spender, feeDenom),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        tokenAssociate(spender, nonFungibleToken),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .addNftAllowance(tokenOwner, nonFungibleToken, spender, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(tokenOwner),
                        cryptoTransfer(movingUniqueWithAllowance(nonFungibleToken, 1L)
                                        .between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, tokenTotal - htsFee),
                        getAccountBalance(spender)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferFungibleTokenWithThreeFixedHtsCustomFeesWithoutAllCollectorsExempt() {
        final long amountToSend = 400L;
        return defaultHapiSpec("transferFungibleTokenWithThreeFixedHtsCustomFeesWithoutAllCollectorsExempt")
                .given(
                        cryptoCreate(alice).balance(0L),
                        cryptoCreate(bob).balance(0L),
                        cryptoCreate(carol).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal * 10L),
                        tokenAssociate(alice, feeDenom),
                        tokenAssociate(bob, feeDenom),
                        tokenAssociate(carol, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(aliceFee, feeDenom, alice))
                                .withCustom(fixedHtsFee(bobFee, feeDenom, bob))
                                .withCustom(fixedHtsFee(carolFee, feeDenom, carol)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        tokenAssociate(alice, fungibleToken),
                        tokenAssociate(bob, fungibleToken),
                        tokenAssociate(carol, fungibleToken),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, alice)),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, bob)),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, carol)))
                .when(
                        cryptoTransfer(moving(amountToSend, fungibleToken).between(tokenTreasury, alice)),
                        cryptoTransfer(moving(amountToSend / 2, fungibleToken).between(alice, bob)),
                        cryptoTransfer(moving(amountToSend / 4, fungibleToken).between(bob, carol)))
                .then(
                        getAccountBalance(alice)
                                .hasTokenBalance(fungibleToken, 200)
                                .hasTokenBalance(feeDenom, 600),
                        getAccountBalance(bob)
                                .hasTokenBalance(fungibleToken, 100)
                                .hasTokenBalance(feeDenom, 800),
                        getAccountBalance(carol)
                                .hasTokenBalance(fungibleToken, 100)
                                .hasTokenBalance(feeDenom, 1600));
    }

    @HapiTest
    public HapiSpec transferFungibleTokenWithThreeFixedHtsCustomFeesWithAllCollectorsExempt() {
        final long amountToSend = 400L;
        return defaultHapiSpec("transferFungibleTokenWithThreeFixedHtsCustomFeesWithAllCollectorsExempt")
                .given(
                        cryptoCreate(alice).balance(0L),
                        cryptoCreate(bob).balance(0L),
                        cryptoCreate(carol).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal * 10L),
                        tokenAssociate(alice, feeDenom),
                        tokenAssociate(bob, feeDenom),
                        tokenAssociate(carol, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(aliceFee, feeDenom, alice, true))
                                .withCustom(fixedHtsFee(bobFee, feeDenom, bob, true))
                                .withCustom(fixedHtsFee(carolFee, feeDenom, carol, true)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        tokenAssociate(alice, fungibleToken),
                        tokenAssociate(bob, fungibleToken),
                        tokenAssociate(carol, fungibleToken),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, alice)),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, bob)),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, carol)))
                .when(
                        cryptoTransfer(moving(amountToSend, fungibleToken).between(tokenTreasury, alice)),
                        cryptoTransfer(moving(amountToSend / 2, fungibleToken).between(alice, bob)),
                        cryptoTransfer(moving(amountToSend / 4, fungibleToken).between(bob, carol)))
                .then(
                        getAccountBalance(alice)
                                .hasTokenBalance(fungibleToken, 200)
                                .hasTokenBalance(feeDenom, 1000),
                        getAccountBalance(bob)
                                .hasTokenBalance(fungibleToken, 100)
                                .hasTokenBalance(feeDenom, 1000),
                        getAccountBalance(carol)
                                .hasTokenBalance(fungibleToken, 100)
                                .hasTokenBalance(feeDenom, 1000));
    }

    @HapiTest
    public HapiSpec transferFungibleTokenWithFixedHtsCustomFees2Layers() {
        return defaultHapiSpec("transferFungibleTokenWithFixedHtsCustomFees2Layers")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(htsCollector2),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(htsCollector2, fungibleToken),
                        tokenCreate(fungibleToken2)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, fungibleToken, htsCollector2)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        tokenAssociate(tokenReceiver, fungibleToken2),
                        tokenAssociate(tokenOwner, fungibleToken2),
                        cryptoTransfer(
                                moving(tokenTotal, fungibleToken2).between(tokenTreasury, tokenOwner),
                                moving(tokenTotal, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(1, fungibleToken2).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(fungibleToken2, 999)
                                .hasTokenBalance(fungibleToken, tokenTotal - htsFee)
                                .hasTokenBalance(feeDenom, tokenTotal - htsFee),
                        getAccountBalance(tokenReceiver).hasTokenBalance(fungibleToken2, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee),
                        getAccountBalance(htsCollector2).hasTokenBalance(fungibleToken, htsFee));
    }

    @HapiTest
    public HapiSpec transferNonFungibleTokenWithFixedHtsCustomFees2Layers() {
        return defaultHapiSpec("transferNonFungibleTokenWithFixedHtsCustomFees2Layers")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(htsCollector2),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(htsCollector2, fungibleToken),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHtsFee(htsFee, fungibleToken, htsCollector2)),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        tokenAssociate(tokenReceiver, nonFungibleToken),
                        tokenAssociate(tokenOwner, nonFungibleToken),
                        cryptoTransfer(
                                movingUnique(nonFungibleToken, 1L).between(tokenTreasury, tokenOwner),
                                moving(tokenTotal, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(fungibleToken, tokenTotal - htsFee)
                                .hasTokenBalance(feeDenom, tokenTotal - htsFee),
                        getAccountBalance(tokenReceiver).hasTokenBalance(nonFungibleToken, 1),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee),
                        getAccountBalance(htsCollector2).hasTokenBalance(fungibleToken, htsFee));
    }

    @HapiTest
    public HapiSpec transferMaxFungibleTokenWith10FixedHtsCustomFees2Layers() {
        final String fungibleToken3 = "fungibleWithCustomFees3";
        final String fungibleToken4 = "fungibleWithCustomFees4";
        final String fungibleToken5 = "fungibleWithCustomFees5";
        final String fungibleToken6 = "fungibleWithCustomFees6";
        final String fungibleToken7 = "fungibleWithCustomFees7";
        final String fungibleToken8 = "fungibleWithCustomFees8";
        final String fungibleToken9 = "fungibleWithCustomFees9";
        final String fungibleToken10 = "fungibleWithCustomFees10";
        final String fungibleToken11 = "fungibleWithCustomFees11";
        final String fungibleToken12 = "fungibleWithCustomFees12";
        final String fungibleToken13 = "fungibleWithCustomFees13";
        final String fungibleToken14 = "fungibleWithCustomFees14";
        final String fungibleToken15 = "fungibleWithCustomFees15";
        final String fungibleToken16 = "fungibleWithCustomFees16";
        final String fungibleToken17 = "fungibleWithCustomFees17";
        final String fungibleToken18 = "fungibleWithCustomFees18";
        final String fungibleToken19 = "fungibleWithCustomFees19";
        final String fungibleToken20 = "fungibleWithCustomFees20";
        final var specificTokenTotal = 2 * tokenTotal;

        List<String> firstLayerCustomFees = List.of(
                fungibleToken2,
                fungibleToken3,
                fungibleToken4,
                fungibleToken5,
                fungibleToken6,
                fungibleToken7,
                fungibleToken8,
                fungibleToken9,
                fungibleToken10);
        List<String> secondLayerCustomFees = List.of(
                fungibleToken11,
                fungibleToken12,
                fungibleToken13,
                fungibleToken14,
                fungibleToken15,
                fungibleToken16,
                fungibleToken17,
                fungibleToken18,
                fungibleToken19,
                fungibleToken20);

        return defaultHapiSpec("transferMaxFungibleTokenWith10FixedHtsCustomFees2Layers")
                .given(withOpContext((spec, log) -> {
                    ArrayList<HapiSpecOperation> ops = new ArrayList<>();
                    var collectorCreate = cryptoCreate(htsCollector);
                    var collector2Create = cryptoCreate(htsCollector2);
                    var tokenOwnerCreate = cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS);
                    var tokenReceiverCreate = cryptoCreate(tokenReceiver);
                    var tokenTreasuryCreate = cryptoCreate(tokenTreasury);
                    allRunFor(
                            spec,
                            collectorCreate,
                            collector2Create,
                            tokenOwnerCreate,
                            tokenReceiverCreate,
                            tokenTreasuryCreate);

                    // create all second layer custom fee hts tokens
                    for (String secondLayerCustomFee : secondLayerCustomFees) {
                        ops.add(tokenCreate(secondLayerCustomFee)
                                .treasury(tokenOwner)
                                .initialSupply(specificTokenTotal));
                        ops.add(tokenAssociate(htsCollector, secondLayerCustomFee));
                    }
                    // create all first layer custom fee hts tokens
                    for (String firstLayerCustomFee : firstLayerCustomFees) {
                        ops.add(tokenCreate(firstLayerCustomFee)
                                .treasury(tokenOwner)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(specificTokenTotal)
                                .withCustom(fixedHtsFee(htsFee, fungibleToken11, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken12, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken13, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken14, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken15, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken16, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken17, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken18, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken19, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, fungibleToken20, htsCollector)));

                        ops.add(tokenAssociate(htsCollector2, firstLayerCustomFee));
                    }
                    allRunFor(spec, ops);

                    var fungibleToTransfer = tokenCreate(fungibleToken)
                            .treasury(tokenTreasury)
                            .tokenType(TokenType.FUNGIBLE_COMMON)
                            .initialSupply(9223372036854775807L)
                            .withCustom(fixedHtsFee(htsFee, fungibleToken2, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken3, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken4, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken5, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken6, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken7, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken8, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken9, htsCollector2))
                            .withCustom(fixedHtsFee(htsFee, fungibleToken10, htsCollector2));
                    var ownerAssociate = tokenAssociate(tokenOwner, fungibleToken);
                    var receiverAssociate = tokenAssociate(tokenReceiver, fungibleToken);
                    var transferToOwner = cryptoTransfer(
                            moving(9223372036854775807L, fungibleToken).between(tokenTreasury, tokenOwner));
                    allRunFor(spec, fungibleToTransfer, ownerAssociate, receiverAssociate, transferToOwner);
                }))
                .when(cryptoTransfer(moving(1, fungibleToken).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(fungibleToken, 9223372036854775806L)
                                .hasTokenBalance(fungibleToken2, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken3, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken4, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken5, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken6, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken7, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken8, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken9, specificTokenTotal - htsFee)
                                .hasTokenBalance(fungibleToken10, specificTokenTotal - htsFee),
                        getAccountBalance(tokenReceiver).hasTokenBalance(fungibleToken, 1),
                        getAccountBalance(htsCollector2)
                                .hasTokenBalance(fungibleToken2, htsFee)
                                .hasTokenBalance(fungibleToken3, htsFee)
                                .hasTokenBalance(fungibleToken4, htsFee)
                                .hasTokenBalance(fungibleToken5, htsFee)
                                .hasTokenBalance(fungibleToken6, htsFee)
                                .hasTokenBalance(fungibleToken7, htsFee)
                                .hasTokenBalance(fungibleToken8, htsFee)
                                .hasTokenBalance(fungibleToken9, htsFee)
                                .hasTokenBalance(fungibleToken10, htsFee));
    }

    @HapiTest
    public HapiSpec multipleTransfersWithMultipleCustomFees() {
        return defaultHapiSpec("multipleTransfersWithMultipleCustomFees")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(htsCollector).balance(ONE_MILLION_HBARS),
                        cryptoCreate(htsCollector2),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(alice).balance(ONE_MILLION_HBARS),
                        cryptoCreate(bob).balance(ONE_MILLION_HBARS),
                        cryptoCreate(carol),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenCreate(feeDenom2).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenAssociate(htsCollector2, feeDenom2),
                        tokenAssociate(alice, feeDenom),
                        tokenAssociate(alice, feeDenom2),
                        tokenAssociate(bob, feeDenom),
                        tokenAssociate(bob, feeDenom2),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector))
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(alice, fungibleToken),
                        tokenAssociate(bob, fungibleToken),
                        tokenCreate(nonFungibleToken)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHtsFee(50L, feeDenom, htsCollector))
                                .withCustom(fixedHtsFee(htsFee, feeDenom2, htsCollector2)),
                        mintToken(nonFungibleToken, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(alice, nonFungibleToken),
                        tokenAssociate(bob, nonFungibleToken),
                        tokenAssociate(carol, nonFungibleToken),
                        cryptoTransfer(
                                moving(tokenTotal, feeDenom).between(tokenOwner, alice),
                                moving(tokenTotal, feeDenom2).between(tokenOwner, alice),
                                moving(tokenTotal, fungibleToken).between(tokenTreasury, alice),
                                movingUnique(nonFungibleToken, 1L).between(tokenTreasury, alice)),
                        // make 2 transfers - one with the same HTS token as custom fee and one with different HTS token
                        // as custom fee
                        cryptoTransfer(moving(10L, fungibleToken).between(alice, bob))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(alice),
                        cryptoTransfer(movingUnique(nonFungibleToken, 1L).between(alice, bob))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(alice),
                        // check balances
                        getAccountBalance(alice)
                                .hasTokenBalance(fungibleToken, tokenTotal - 10L)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, tokenTotal - htsFee - 50L)
                                .hasTokenBalance(feeDenom2, tokenTotal - htsFee),
                        getAccountBalance(bob)
                                .hasTokenBalance(fungibleToken, 8L)
                                .hasTokenBalance(nonFungibleToken, 1)
                                .hasTokenBalance(feeDenom, 0)
                                .hasTokenBalance(feeDenom2, 0),
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee + 50L),
                        getAccountBalance(htsCollector2).hasTokenBalance(feeDenom2, htsFee))
                .when(
                        // transfer some hts custom fee tokens to bob as he is a sender and needs to pay with them
                        cryptoTransfer(
                                moving(100L, feeDenom).between(alice, bob),
                                moving(200L, feeDenom2).between(alice, bob)),
                        // make 2 transfers in a single tx with different senders and receivers
                        cryptoTransfer(
                                        moving(10L, fungibleToken).between(alice, bob),
                                        movingUnique(nonFungibleToken, 1L).between(bob, carol))
                                .fee(ONE_HUNDRED_HBARS)
                                .signedBy(alice, bob)
                                .payingWith(alice))
                .then(
                        // check balances
                        getAccountBalance(alice)
                                .hasTokenBalance(fungibleToken, tokenTotal - 2 * 10L)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, tokenTotal - 2 * htsFee - 50L - 100L)
                                .hasTokenBalance(feeDenom2, tokenTotal - htsFee - 200L),
                        getAccountBalance(bob)
                                .hasTokenBalance(fungibleToken, 16L)
                                .hasTokenBalance(nonFungibleToken, 0)
                                .hasTokenBalance(feeDenom, 50L)
                                .hasTokenBalance(feeDenom2, 100L),
                        getAccountBalance(carol)
                                .hasTokenBalance(fungibleToken, 0L)
                                .hasTokenBalance(nonFungibleToken, 1)
                                .hasTokenBalance(feeDenom, 0)
                                .hasTokenBalance(feeDenom2, 0),
                        getAccountBalance(hbarCollector).hasTinyBars(2 * hbarFee),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 2 * htsFee + 2 * 50L),
                        getAccountBalance(htsCollector2).hasTokenBalance(feeDenom2, 2 * htsFee));
    }

    @HapiTest
    public HapiSpec transferWithFractionalCustomFee() {
        return defaultHapiSpec("transferWithFractionalCustomFee")
                .given(
                        cryptoCreate(htsCollector).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector))
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        cryptoTransfer(moving(tokenTotal, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(3, fungibleToken).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(fungibleToken, 997)
                                .hasTokenBalance(feeDenom, tokenTotal),
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee));
    }

    @HapiTest
    public HapiSpec transferWithInsufficientCustomFees() {
        return defaultHapiSpec("transferWithInsufficientCustomFees")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(10),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(fungibleToken)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, fungibleToken),
                        tokenAssociate(tokenOwner, fungibleToken),
                        cryptoTransfer(moving(tokenTotal, fungibleToken).between(tokenTreasury, tokenOwner)))
                .when()
                .then(cryptoTransfer(moving(1, fungibleToken).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner)
                        .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
