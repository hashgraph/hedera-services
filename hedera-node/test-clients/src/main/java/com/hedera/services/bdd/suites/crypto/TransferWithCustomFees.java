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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class TransferWithCustomFees extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransferWithCustomFees.class);
    private final long hbarFee = 1_000L;
    private final long htsFee = 100L;
    private final long tokenTotal = 1_000L;
    private final long numerator = 1L;
    private final long denominator = 10L;
    private final long minHtsFee = 2L;
    private final long maxHtsFee = 10L;

    private final String token = "withCustomSchedules";
    private final String feeDenom = "denom";
    private final String hbarCollector = "hbarFee";
    private final String htsCollector = "denomFee";
    private final String tokenReceiver = "receiver";
    private final String tokenTreasury = "tokenTreasury";

    private final String tokenOwner = "tokenOwner";

    public static void main(String... args) {
        new TransferWithCustomFees().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            transferWithFixedCustomFeeSchedule(),
            transferWithFractinalCustomFeeSchedule(),
            transferWithInsufficientCustomFees()
        });
    }

    @HapiTest
    public HapiSpec transferWithFixedCustomFeeSchedule() {
        return defaultHapiSpec("transferWithFixedCustomFeeSchedule")
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
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee));
    }

    @HapiTest
    public HapiSpec transferWithFractinalCustomFeeSchedule() {
        return defaultHapiSpec("transferWithCustomFeeScheduleHappyPath")
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
        return defaultHapiSpec("transferWithFixedCustomFeeSchedule")
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
