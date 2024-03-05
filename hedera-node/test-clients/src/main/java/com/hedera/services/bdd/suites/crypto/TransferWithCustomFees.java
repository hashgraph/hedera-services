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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(CRYPTO)
public class TransferWithCustomFees extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransferWithCustomFees.class);
    private final long hbarFee = 1_000L;
    private final long htsFee = 100L;
    private final long tokenTotal = 1_000L;
    private final long numerator = 1L;
    private final long denominator = 10L;
    private final long minHtsFee = 2L;
    private final long maxHtsFee = 10L;

    private final String token = "withCustomFees";
    private final String feeDenom = "denom";
    private final String hbarCollector = "hbarFee";
    private final String htsCollector = "denomFee";
    private final String tokenReceiver = "receiver";
    private final String tokenTreasury = "tokenTreasury";
    private final String spender = "spender";
    private static final String NFT_KEY = "nftKey";
    private final String tokenOwner = "tokenOwner";
    private final String alice = "alice";
    private final long aliceFee = 100L;
    private final String bob = "bob";
    private final long bobFee = 200L;
    private final String carol = "carol";
    private final long carolFee = 300L;

    public static void main(String... args) {
        new TransferWithCustomFees().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            transferErc20WithFixedHbarAndHtsCustomFees(),
            transferErc20WithFixedHtsCustomFeesNegativeNotEnoughBalance(),
            transferErc20WithFixedHbarCustomFeesNegativeNotEnoughBalance(),
            transferErc721WithFixedHbarAndHtsCustomFees(),
            transferErc721WithFixedHtsCustomFeesNegativeNotEnoughBalance(),
            transferErc721WithFixedHbarCustomFeesNegativeNotEnoughBalance(),
            transferApprovedErc20WithFixedHbarAndHtsCustomFee(),
            transferApprovedErc721WithFixedHbarAndHtsCustomFees(),
            transferErc20WithThreeFixedHbarCustomFees(),
            transferWithFractionalCustomFee(),
            transferWithInsufficientCustomFees()
        });
    }

    @HapiTest
    public HapiSpec transferErc20WithFixedHbarAndHtsCustomFees() {
        return defaultHapiSpec("transferErc20WithFixedHbarAndHtsCustomFees")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector))
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(1000, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(1, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(token, 999)
                                .hasTokenBalance(feeDenom, 900),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 1),
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferErc20WithFixedHtsCustomFeesNegativeNotEnoughBalance() {
        return defaultHapiSpec("transferErc20WithFixedHtsCustomFeesNegativeNotEnoughBalance")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(1),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(1, token).between(tokenOwner, tokenReceiver))
                        .payingWith(tokenOwner)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(token, tokenTotal)
                                .hasTokenBalance(feeDenom, 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0));
    }

    @HapiTest
    public HapiSpec transferErc20WithFixedHbarCustomFeesNegativeNotEnoughBalance() {
        return defaultHapiSpec("transferErc20WithFixedHbarCustomFeesNegativeNotEnoughBalance")
                .given(
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHbarFee(ONE_HUNDRED_HBARS, hbarCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(1, token).between(tokenOwner, tokenReceiver))
                        .payingWith(tokenOwner)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, tokenTotal),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 0),
                        getAccountBalance(hbarCollector).hasTinyBars(0));
    }

    @HapiTest
    public HapiSpec transferErc721WithFixedHbarAndHtsCustomFees() {
        return defaultHapiSpec("transferErc721WithFixedHbarAndHtsCustomFees")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector))
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        mintToken(token, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(movingUnique(token, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(token, 1L).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 0).hasTokenBalance(feeDenom, 900),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 1),
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferErc721WithFixedHtsCustomFeesNegativeNotEnoughBalance() {
        return defaultHapiSpec("transferErc721WithFixedHtsCustomFeesNegativeNotEnoughBalance")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(1),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHtsFee(2, feeDenom, htsCollector)),
                        mintToken(token, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(movingUnique(token, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(token, 1L).between(tokenOwner, tokenReceiver))
                        .payingWith(tokenOwner)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 1).hasTokenBalance(feeDenom, 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, 0));
    }

    @HapiTest
    public HapiSpec transferErc721WithFixedHbarCustomFeesNegativeNotEnoughBalance() {
        return defaultHapiSpec("transferErc721WithFixedHbarCustomFeesNegativeNotEnoughBalance")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHbarFee(THOUSAND_HBAR, hbarCollector)),
                        mintToken(token, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(movingUnique(token, 1L).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(movingUnique(token, 1L).between(tokenOwner, tokenReceiver))
                        .payingWith(tokenOwner)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 1),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 0),
                        getAccountBalance(hbarCollector).hasTinyBars(0));
    }

    @HapiTest
    public HapiSpec transferApprovedErc20WithFixedHbarAndHtsCustomFee() {
        return defaultHapiSpec("transferApprovedErc20WithFixedHbarAndHtsCustomFee")
                .given(
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector))
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        tokenAssociate(spender, token),
                        tokenAssociate(spender, feeDenom),
                        cryptoTransfer(moving(1000, token).between(tokenTreasury, tokenOwner)),
                        cryptoTransfer(moving(500, feeDenom).between(tokenOwner, spender)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(tokenOwner)
                                // .addCryptoAllowance(tokenOwner, spender, hbarFee)
                                .addTokenAllowance(tokenOwner, token, spender, 1)
                                // .addNftAllowance(OTHER_OWNER, NON_FUNGIBLE_TOKEN, spender, true, List.of(3L))
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoTransfer(movingWithAllowance(1, token).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(token, 999)
                                .hasTokenBalance(feeDenom, 400),
                        getAccountBalance(spender).hasTokenBalance(token, 0).hasTokenBalance(feeDenom, 500),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 1),
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferApprovedErc721WithFixedHbarAndHtsCustomFees() {
        return defaultHapiSpec("transferApprovedErc721WithFixedHbarAndHtsCustomFees")
                .given(
                        newKeyNamed(NFT_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(tokenOwner).balance(ONE_MILLION_HBARS),
                        cryptoCreate(spender).balance(ONE_MILLION_HBARS),
                        cryptoCreate(tokenReceiver),
                        cryptoCreate(tokenTreasury),
                        tokenCreate(feeDenom).treasury(tokenOwner).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector))
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        mintToken(token, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        tokenAssociate(spender, token),
                        tokenAssociate(spender, feeDenom),
                        cryptoTransfer(movingUnique(token, 1L).between(tokenTreasury, tokenOwner)))
                .when(
                        cryptoApproveAllowance()
                                .payingWith(tokenOwner)
                                .addNftAllowance(tokenOwner, token, spender, false, List.of(1L))
                                .fee(ONE_HUNDRED_HBARS),
                        cryptoTransfer(movingUniqueWithAllowance(token, 1L).between(tokenOwner, tokenReceiver))
                                .fee(ONE_HUNDRED_HBARS)
                                .payingWith(spender)
                                .signedBy(spender))
                .then(
                        getAccountBalance(tokenOwner).hasTokenBalance(token, 0).hasTokenBalance(feeDenom, 900),
                        getAccountBalance(spender).hasTokenBalance(token, 0).hasTokenBalance(feeDenom, 0),
                        getAccountBalance(tokenReceiver).hasTokenBalance(token, 1),
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee));
    }

    @HapiTest
    public HapiSpec transferErc20WithThreeFixedHbarCustomFees() {

        return defaultHapiSpec("transferErc20WithThreeFixedHbarCustomFees")
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
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(aliceFee, feeDenom, alice))
                                .withCustom(fixedHtsFee(bobFee, feeDenom, bob))
                                .withCustom(fixedHtsFee(carolFee, feeDenom, carol)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        tokenAssociate(alice, token),
                        tokenAssociate(bob, token),
                        tokenAssociate(carol, token),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, alice)),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, bob)),
                        cryptoTransfer(moving(1000, feeDenom).between(tokenOwner, carol)))
                .when(
                        cryptoTransfer(moving(400, token).between(tokenTreasury, alice)),
                        cryptoTransfer(moving(200, token).between(alice, bob)),
                        cryptoTransfer(moving(100, token).between(bob, carol)))
                .then(
                        // getAccountBalance(alice).hasTokenBalance(token, 200).hasTokenBalance(feeDenom, 1000),
                        // getAccountBalance(bob).hasTokenBalance(token, 100).hasTokenBalance(feeDenom, 1000)
                        getAccountBalance(carol).hasTokenBalance(token, 100).hasTokenBalance(feeDenom, 1000));
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
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .payingWith(htsCollector)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector))
                                .withCustom(fractionalFee(
                                        numerator, denominator, minHtsFee, OptionalLong.of(maxHtsFee), htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when(cryptoTransfer(moving(3, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner))
                .then(
                        getAccountBalance(tokenOwner)
                                .hasTokenBalance(token, 997)
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
                        tokenCreate(token)
                                .treasury(tokenTreasury)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(tokenReceiver, token),
                        tokenAssociate(tokenOwner, token),
                        cryptoTransfer(moving(tokenTotal, token).between(tokenTreasury, tokenOwner)))
                .when()
                .then(cryptoTransfer(moving(1, token).between(tokenOwner, tokenReceiver))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(tokenOwner)
                        .hasKnownStatus(ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
