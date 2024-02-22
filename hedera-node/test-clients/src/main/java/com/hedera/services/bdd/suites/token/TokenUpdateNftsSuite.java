/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(TOKEN)
public class TokenUpdateNftsSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenUpdateNftsSuite.class);

    private static String TOKEN_TREASURY = "treasury";
    private static final String SENDER = "sender";
    private static final String RECEIVER = "receiver";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    private static final String SUPPLY_KEY = "supplyKey";

    public static void main(String... args) {
        new TokenUpdateNftsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(cryptoNFTTransferFeeChargedAsExpected(), updateHappyPath());
    }

    @HapiTest
    final HapiSpec cryptoNFTTransferFeeChargedAsExpected() {
        final var expectedNftXferPriceUsd = 0.001;
        final var expectedNftXferWithCustomFeePriceUsd = 0.002;
        final var customFeeCollector = "customFeeCollector";
        final var nonTreasurySender = "nonTreasurySender";
        final var nonFungibleToken = "nonFungibleToken";
        final var nonFungibleTokenWithCustomFee = "nonFungibleTokenWithCustomFee";
        final var nftXferTxn = "nftXferTxn";
        final var nftXferTxnWithCustomFee = "nftXferTxnWithCustomFee";

        return defaultHapiSpec("cryptoNFTTransferFeeChargedAsExpected", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        cryptoCreate(nonTreasurySender).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(customFeeCollector),
                        newKeyNamed(SUPPLY_KEY),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(SENDER),
                        mintToken(nonFungibleToken, List.of(copyFromUtf8("memo1"))),
                        tokenAssociate(RECEIVER, nonFungibleToken))
                .when(cryptoTransfer(movingUnique(nonFungibleToken, 1).between(SENDER, RECEIVER))
                        .blankMemo()
                        .payingWith(SENDER)
                        .via(nftXferTxn))
                .then(validateChargedUsdWithin(nftXferTxn, expectedNftXferPriceUsd, 0.01));
        //                        validateChargedUsdWithin(nftXferTxnWithCustomFee,
        // expectedNftXferWithCustomFeePriceUsd, 0.3));
    }

    @HapiTest
    public HapiSpec updateHappyPath() {
        String originalMemo = "First things first";
        String updatedMemo = "Nothing left to do";
        String saltedName = salted("primary");
        String newSaltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("UpdateHappyPath")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate("newTokenTreasury").balance(0L),
                        cryptoCreate("autoRenewAccount").balance(0L),
                        cryptoCreate("newAutoRenewAccount").balance(0L),
                        newKeyNamed("adminKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("newFreezeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("newKycKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("newSupplyKey"),
                        newKeyNamed("wipeKey"),
                        newKeyNamed("newWipeKey"),
                        newKeyNamed("pauseKey"),
                        newKeyNamed("newPauseKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .entityMemo(originalMemo)
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount("autoRenewAccount")
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .initialSupply(500)
                                .decimals(1)
                                .adminKey("adminKey")
                                .freezeKey("freezeKey")
                                .kycKey("kycKey")
                                .supplyKey("supplyKey")
                                .wipeKey("wipeKey")
                                .pauseKey("pauseKey")
                                .payingWith(civilian))
                .when(
                        tokenAssociate("newTokenTreasury", "primary"),
                        tokenUpdate("primary").entityMemo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        tokenUpdate("primary")
                                .name(newSaltedName)
                                .entityMemo(updatedMemo)
                                .treasury("newTokenTreasury")
                                .autoRenewAccount("newAutoRenewAccount")
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS + 1)
                                .freezeKey("newFreezeKey")
                                .kycKey("newKycKey")
                                .supplyKey("newSupplyKey")
                                .wipeKey("newWipeKey")
                                .pauseKey("newPauseKey")
                                .payingWith(civilian))
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance("primary", 0),
                        getAccountBalance("newTokenTreasury").hasTokenBalance("primary", 500),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(ExpectedTokenRel.relationshipWith("primary")
                                        .balance(0)),
                        getAccountInfo("newTokenTreasury")
                                .hasToken(ExpectedTokenRel.relationshipWith("primary")
                                        .freeze(TokenFreezeStatus.Unfrozen)
                                        .kyc(TokenKycStatus.Granted)
                                        .balance(500)),
                        getTokenInfo("primary")
                                .logged()
                                .hasEntityMemo(updatedMemo)
                                .hasRegisteredId("primary")
                                .hasName(newSaltedName)
                                .hasTreasury("newTokenTreasury")
                                .hasFreezeKey("primary")
                                .hasKycKey("primary")
                                .hasSupplyKey("primary")
                                .hasWipeKey("primary")
                                .hasPauseKey("primary")
                                .hasTotalSupply(500)
                                .hasAutoRenewAccount("newAutoRenewAccount")
                                .hasPauseStatus(TokenPauseStatus.Unpaused)
                                .hasAutoRenewPeriod(THREE_MONTHS_IN_SECONDS + 1));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
