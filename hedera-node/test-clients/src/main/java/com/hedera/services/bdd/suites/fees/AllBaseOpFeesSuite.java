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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyLabels.complex;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigControl.threshSigs;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenReject;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenReject.rejectingNFT;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenReject.rejectingToken;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.keys.KeyLabels;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(TOKEN)
public class AllBaseOpFeesSuite {
    private static final String PAYER = "payer";
    private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 0.01;
    private static final double ALLOWED_DIFFERENCE = 1;

    private static final String TREASURE_KEY = "treasureKey";
    private static final String FUNGIBLE_COMMON_TOKEN = "fungibleCommonToken";

    private static final String ADMIN_KEY = "adminKey";
    private static final String MULTI_KEY = "multiKey";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String WIPE_KEY = "wipeKey";
    private static final String KYC_KEY = "kycKey";

    private static final String CIVILIAN_ACCT = "civilian";
    private static final String ALICE = "alice";

    private static final String UNIQUE_TOKEN = "nftType";

    private static final String BASE_TXN = "baseTxn";

    private static final String UNFREEZE = "unfreeze";

    private static final double EXPECTED_FUNGIBLE_REJECT_PRICE_USD = 0.001;
    private static final double EXPECTED_NFT_REJECT_PRICE_USD = 0.00100245;
    private static final double EXPECTED_MIX_REJECT_PRICE_USD = 0.00375498;
    private static final double EXPECTED_UNFREEZE_PRICE_USD = 0.001;
    private static final double EXPECTED_FREEZE_PRICE_USD = 0.001;
    private static final double EXPECTED_NFT_MINT_PRICE_USD = 0.02;
    private static final double EXPECTED_NFT_BURN_PRICE_USD = 0.001;
    private static final double EXPECTED_NFT_WIPE_PRICE_USD = 0.001;

    @HapiTest
    final Stream<DynamicTest> baseNftMintOperationIsChargedExpectedFee() {
        final var standard100ByteMetadata = ByteString.copyFromUtf8(
                "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");

        return defaultHapiSpec("BaseUniqueMintOperationIsChargedExpectedFee")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(CIVILIAN_ACCT).balance(ONE_MILLION_HBARS).key(SUPPLY_KEY),
                        tokenCreate(UNIQUE_TOKEN)
                                .initialSupply(0L)
                                .expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                                .supplyKey(SUPPLY_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE))
                .when(mintToken(UNIQUE_TOKEN, List.of(standard100ByteMetadata))
                        .payingWith(CIVILIAN_ACCT)
                        .signedBy(SUPPLY_KEY)
                        .blankMemo()
                        .fee(ONE_HUNDRED_HBARS)
                        .via(BASE_TXN))
                .then(validateChargedUsdWithin(BASE_TXN, EXPECTED_NFT_MINT_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE));
    }

    @HapiTest
    final Stream<DynamicTest> NftMintsScaleLinearlyBasedOnNumberOfSerialNumbers() {
        final var expectedFee = 10 * EXPECTED_NFT_MINT_PRICE_USD;
        final var standard100ByteMetadata = ByteString.copyFromUtf8(
                "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");

        return defaultHapiSpec("NftMintsScaleLinearlyBasedOnNumberOfSerialNumbers")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(CIVILIAN_ACCT).balance(ONE_MILLION_HBARS).key(SUPPLY_KEY),
                        tokenCreate(UNIQUE_TOKEN)
                                .initialSupply(0L)
                                .expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                                .supplyKey(SUPPLY_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE))
                .when(mintToken(
                                UNIQUE_TOKEN,
                                List.of(
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata,
                                        standard100ByteMetadata))
                        .payingWith(CIVILIAN_ACCT)
                        .signedBy(SUPPLY_KEY)
                        .blankMemo()
                        .fee(ONE_HUNDRED_HBARS)
                        .via(BASE_TXN))
                .then(validateChargedUsdWithin(BASE_TXN, expectedFee, ALLOWED_DIFFERENCE_PERCENTAGE));
    }

    @HapiTest
    final Stream<DynamicTest> NftMintsScaleLinearlyBasedOnNumberOfSignatures() {
        final var numOfSigs = 10;
        final var extraSigPrice = 0.0006016996;
        final var expectedFee = EXPECTED_NFT_MINT_PRICE_USD + ((numOfSigs - 1) * extraSigPrice);
        final var standard100ByteMetadata = ByteString.copyFromUtf8(
                "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789");

        return defaultHapiSpec("NftMintsScaleLinearlyBasedOnNumberOfSignatures")
                .given(
                        newKeyNamed(SUPPLY_KEY).shape(listOf(numOfSigs)),
                        cryptoCreate(CIVILIAN_ACCT).balance(ONE_MILLION_HBARS).key(SUPPLY_KEY),
                        tokenCreate(UNIQUE_TOKEN)
                                .initialSupply(0L)
                                .expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                                .supplyKey(SUPPLY_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE))
                .when(mintToken(UNIQUE_TOKEN, List.of(standard100ByteMetadata))
                        .payingWith(CIVILIAN_ACCT)
                        .signedBy(SUPPLY_KEY, SUPPLY_KEY, SUPPLY_KEY)
                        .blankMemo()
                        .fee(ONE_HUNDRED_HBARS)
                        .via("moreSigsTxn"))
                .then(validateChargedUsdWithin("moreSigsTxn", expectedFee, ALLOWED_DIFFERENCE_PERCENTAGE));
    }

    @HapiTest
    final Stream<DynamicTest> baseNftWipeOperationIsChargedExpectedFee() {
        return defaultHapiSpec("BaseUniqueWipeOperationIsChargedExpectedFee")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(CIVILIAN_ACCT).key(WIPE_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(WIPE_KEY),
                        tokenCreate(UNIQUE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0L)
                                .supplyKey(SUPPLY_KEY)
                                .wipeKey(WIPE_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(CIVILIAN_ACCT, UNIQUE_TOKEN),
                        mintToken(UNIQUE_TOKEN, List.of(ByteString.copyFromUtf8("token_to_wipe"))),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN, 1L).between(TOKEN_TREASURY, CIVILIAN_ACCT)))
                .when(wipeTokenAccount(UNIQUE_TOKEN, CIVILIAN_ACCT, List.of(1L))
                        .payingWith(TOKEN_TREASURY)
                        .fee(ONE_HBAR)
                        .blankMemo()
                        .via(BASE_TXN))
                .then(validateChargedUsdWithin(BASE_TXN, EXPECTED_NFT_WIPE_PRICE_USD, 0.01));
    }

    @HapiTest
    final Stream<DynamicTest> baseNftBurnOperationIsChargedExpectedFee() {
        return defaultHapiSpec("BaseUniqueBurnOperationIsChargedExpectedFee")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(CIVILIAN_ACCT).key(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(UNIQUE_TOKEN)
                                .initialSupply(0)
                                .supplyKey(SUPPLY_KEY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY),
                        mintToken(UNIQUE_TOKEN, List.of(metadata("memo"))))
                .when(burnToken(UNIQUE_TOKEN, List.of(1L))
                        .fee(ONE_HBAR)
                        .payingWith(CIVILIAN_ACCT)
                        .blankMemo()
                        .via(BASE_TXN))
                .then(validateChargedUsdWithin(BASE_TXN, EXPECTED_NFT_BURN_PRICE_USD, 0.01));
    }

    @HapiTest
    final Stream<DynamicTest> baseCommonTokenRejectChargedAsExpected() {
        return defaultHapiSpec("baseCommonTokenRejectChargedAsExpected")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_COMMON_TOKEN)
                                .initialSupply(1000L)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(UNIQUE_TOKEN)
                                .initialSupply(0)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE),
                        mintToken(
                                UNIQUE_TOKEN,
                                List.of(
                                        metadata("nemo the fish"),
                                        metadata("garfield the cat"),
                                        metadata("snoopy the dog"))),
                        tokenAssociate(ALICE, FUNGIBLE_COMMON_TOKEN, UNIQUE_TOKEN),
                        cryptoTransfer(movingUnique(UNIQUE_TOKEN, 1L).between(TOKEN_TREASURY, ALICE))
                                .payingWith(TOKEN_TREASURY)
                                .via("nftTransfer"),
                        cryptoTransfer(moving(100, FUNGIBLE_COMMON_TOKEN).between(TOKEN_TREASURY, ALICE))
                                .payingWith(TOKEN_TREASURY)
                                .via("fungibleTransfer"))
                .when(
                        tokenReject(rejectingToken(FUNGIBLE_COMMON_TOKEN))
                                .payingWith(ALICE)
                                .via("rejectFungible"),
                        tokenReject(rejectingNFT(UNIQUE_TOKEN, 1))
                                .payingWith(ALICE)
                                .via("rejectNft"),
                        cryptoTransfer(
                                        movingUnique(UNIQUE_TOKEN, 1L).between(TOKEN_TREASURY, ALICE),
                                        moving(100, FUNGIBLE_COMMON_TOKEN).between(TOKEN_TREASURY, ALICE))
                                .payingWith(ALICE)
                                .via("transferMix"),
                        tokenReject(ALICE, rejectingNFT(UNIQUE_TOKEN, 1), rejectingToken(FUNGIBLE_COMMON_TOKEN))
                                .payingWith(TOKEN_TREASURY)
                                .via("rejectMix"))
                .then(
                        validateChargedUsdWithin(
                                "fungibleTransfer", EXPECTED_FUNGIBLE_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin("nftTransfer", EXPECTED_NFT_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin("transferMix", EXPECTED_MIX_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin(
                                "rejectFungible", EXPECTED_FUNGIBLE_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin("rejectNft", EXPECTED_NFT_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin("rejectMix", EXPECTED_MIX_REJECT_PRICE_USD, ALLOWED_DIFFERENCE));
    }

    @HapiTest
    final Stream<DynamicTest> baseNftFreezeUnfreezeChargedAsExpected() {
        return defaultHapiSpec("baseNftFreezeUnfreezeChargedAsExpected")
                .given(
                        newKeyNamed(TREASURE_KEY),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(KYC_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
                        cryptoCreate(CIVILIAN_ACCT),
                        tokenCreate(UNIQUE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0L)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(TOKEN_TREASURY)
                                .kycKey(KYC_KEY)
                                .freezeDefault(false)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                .supplyKey(ADMIN_KEY)
                                .via(BASE_TXN),
                        tokenAssociate(CIVILIAN_ACCT, UNIQUE_TOKEN))
                .when(
                        tokenFreeze(UNIQUE_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .signedBy(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                .via("freeze"),
                        tokenUnfreeze(UNIQUE_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY)
                                .via(UNFREEZE))
                .then(
                        validateChargedUsdWithin("freeze", EXPECTED_FREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
                        validateChargedUsdWithin(UNFREEZE, EXPECTED_UNFREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE));
    }

    @HapiTest
    final Stream<DynamicTest> baseCommonFreezeUnfreezeChargedAsExpected() {
        return defaultHapiSpec("baseCommonFreezeUnfreezeChargedAsExpected")
                .given(
                        newKeyNamed(TREASURE_KEY),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(WIPE_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS).key(TREASURE_KEY),
                        cryptoCreate(CIVILIAN_ACCT),
                        tokenCreate(FUNGIBLE_COMMON_TOKEN)
                                .adminKey(ADMIN_KEY)
                                .freezeKey(TOKEN_TREASURY)
                                .wipeKey(WIPE_KEY)
                                .supplyKey(SUPPLY_KEY)
                                .freezeDefault(false)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY),
                        tokenAssociate(CIVILIAN_ACCT, FUNGIBLE_COMMON_TOKEN))
                .when(
                        tokenFreeze(FUNGIBLE_COMMON_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .signedBy(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                .via("freeze"),
                        tokenUnfreeze(FUNGIBLE_COMMON_TOKEN, CIVILIAN_ACCT)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY)
                                .via(UNFREEZE))
                .then(
                        validateChargedUsdWithin("freeze", EXPECTED_FREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE),
                        validateChargedUsdWithin(UNFREEZE, EXPECTED_UNFREEZE_PRICE_USD, ALLOWED_DIFFERENCE_PERCENTAGE));
    }

    @HapiTest
    final Stream<DynamicTest> feeCalcUsesNumPayerKeys() {
        SigControl SHAPE = threshSigs(2, threshSigs(2, ANY, ANY, ANY), threshSigs(2, ANY, ANY, ANY));
        KeyLabels ONE_UNIQUE_KEY = complex(complex("X", "X", "X"), complex("X", "X", "X"));
        SigControl SIGN_ONCE = threshSigs(2, threshSigs(3, ON, OFF, OFF), threshSigs(3, OFF, OFF, OFF));

        return defaultHapiSpec("PayerSigRedundancyRecognized")
                .given(
                        newKeyNamed("repeatingKey").shape(SHAPE).labels(ONE_UNIQUE_KEY),
                        cryptoCreate("testAccount").key("repeatingKey").balance(1_000_000_000L))
                .when()
                .then(
                        QueryVerbs.getAccountInfo("testAccount")
                                .sigControl(forKey("repeatingKey", SIGN_ONCE))
                                .payingWith("testAccount")
                                .numPayerSigs(5)
                                .hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE),
                        QueryVerbs.getAccountInfo("testAccount")
                                .sigControl(forKey("repeatingKey", SIGN_ONCE))
                                .payingWith("testAccount")
                                .numPayerSigs(6));
    }

    @HapiTest
    final Stream<DynamicTest> payerRecordCreationSanityChecks() {
        return defaultHapiSpec("PayerRecordCreationSanityChecks")
                .given(cryptoCreate(PAYER))
                .when(
                        createTopic("ofGeneralInterest").payingWith(PAYER),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_000L)).payingWith(PAYER),
                        submitMessageTo("ofGeneralInterest").message("I say!").payingWith(PAYER))
                .then(assertionsHold((spec, opLog) -> {
                    final var payerId = spec.registry().getAccountID(PAYER);
                    final var subOp = getAccountRecords(PAYER).logged();
                    allRunFor(spec, subOp);
                    final var records = subOp.getResponse().getCryptoGetAccountRecords().getRecordsList().stream()
                            .filter(TxnUtils::isNotEndOfStakingPeriodRecord)
                            .toList();
                    assertEquals(3, records.size());
                    for (var record : records) {
                        assertEquals(record.getTransactionFee(), -netChangeIn(record, payerId));
                    }
                }));
    }

    private long netChangeIn(TransactionRecord record, AccountID id) {
        return record.getTransferList().getAccountAmountsList().stream()
                .filter(aa -> id.equals(aa.getAccountID()))
                .mapToLong(AccountAmount::getAmount)
                .sum();
    }
}
