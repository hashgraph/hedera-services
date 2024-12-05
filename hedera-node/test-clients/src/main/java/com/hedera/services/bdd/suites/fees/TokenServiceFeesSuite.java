/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungiblePendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNftPendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNonfungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCancelAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenClaimAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenPause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenReject;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnpause;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdateNfts;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenCancelAirdrop.pendingAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenCancelAirdrop.pendingNFTAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenReject.rejectingNFT;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenReject.rejectingToken;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.hip904.TokenAirdropBase.setUpTokensAndAllReceivers;
import static com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite.RECEIVER;
import static com.hedera.services.bdd.suites.utils.MiscEETUtils.metadata;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Paused;
import static com.hederahashgraph.api.proto.java.TokenPauseStatus.Unpaused;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenClaimAirdrop;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

public class TokenServiceFeesSuite {
    private static final double ALLOWED_DIFFERENCE_PERCENTAGE = 0.01;
    private static final double ALLOWED_DIFFERENCE = 1;
    private static String TOKEN_TREASURY = "treasury";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungible";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String METADATA_KEY = "metadataKey";
    private static final String ADMIN_KEY = "adminKey";
    private static final String PAUSE_KEY = "pauseKey";
    private static final String TREASURE_KEY = "treasureKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String FUNGIBLE_FREEZE_KEY = "fungibleTokenFreeze";
    private static final String KYC_KEY = "kycKey";
    private static final String MULTI_KEY = "multiKey";
    private static final String NAME = "012345678912";
    private static final String ALICE = "alice";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";

    private static final String UNFREEZE = "unfreeze";

    private static final String CIVILIAN_ACCT = "civilian";
    private static final String UNIQUE_TOKEN = "nftType";
    private static final String BASE_TXN = "baseTxn";

    private static final String WIPE_KEY = "wipeKey";
    private static final String NFT_TEST_METADATA = " test metadata";
    private static final String FUNGIBLE_COMMON_TOKEN = "f";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String RECEIVER_WITH_0_AUTO_ASSOCIATIONS = "receiverWith0AutoAssociations";

    private static final double EXPECTED_NFT_WIPE_PRICE_USD = 0.001;
    private static final double EXPECTED_FREEZE_PRICE_USD = 0.001;
    private static final double EXPECTED_UNFREEZE_PRICE_USD = 0.001;
    private static final double EXPECTED_NFT_BURN_PRICE_USD = 0.001;
    private static final double EXPECTED_NFT_MINT_PRICE_USD = 0.02;
    private static final double EXPECTED_FUNGIBLE_REJECT_PRICE_USD = 0.001;
    private static final double EXPECTED_NFT_REJECT_PRICE_USD = 0.001;
    private static final String OWNER = "owner";

    @HapiTest
    @DisplayName("charge association fee for FT correctly")
    final Stream<DynamicTest> chargeAssociationFeeForFT() {
        var receiver = "receiver";
        return hapiTest(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                newKeyNamed(FUNGIBLE_FREEZE_KEY),
                tokenCreate(FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .freezeKey(FUNGIBLE_FREEZE_KEY)
                        .initialSupply(1000L),
                cryptoCreate(receiver).maxAutomaticTokenAssociations(0),
                tokenAirdrop(moving(1, FUNGIBLE_TOKEN).between(OWNER, receiver))
                        .payingWith(OWNER)
                        .via("airdrop"),
                tokenAirdrop(moving(1, FUNGIBLE_TOKEN).between(OWNER, receiver))
                        .payingWith(OWNER)
                        .via("second airdrop"),
                validateChargedUsd("airdrop", 0.1, 1),
                validateChargedUsd("second airdrop", 0.05, 1));
    }

    @HapiTest
    @DisplayName("charge association fee for NFT correctly")
    final Stream<DynamicTest> chargeAssociationFeeForNFT() {
        var receiver = "receiver";
        var nftSupplyKey = "nftSupplyKey";
        return hapiTest(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                newKeyNamed(nftSupplyKey),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .name(NON_FUNGIBLE_TOKEN)
                        .supplyKey(nftSupplyKey),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        IntStream.range(0, 10)
                                .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                .toList()),
                cryptoCreate(receiver).maxAutomaticTokenAssociations(0),
                tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(OWNER, receiver))
                        .payingWith(OWNER)
                        .via("airdrop"),
                tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 2).between(OWNER, receiver))
                        .payingWith(OWNER)
                        .via("second airdrop"),
                validateChargedUsd("airdrop", 0.1, 1),
                validateChargedUsd("second airdrop", 0.1, 1));
    }

    @HapiTest
    final Stream<DynamicTest> claimFungibleTokenAirdrop() {
        var nftSupplyKey = "nftSupplyKey";
        return defaultHapiSpec("should transfer fungible tokens")
                .given(flattened(
                        setUpTokensAndAllReceivers(), cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS)))
                .when(
                        // do pending airdrop
                        newKeyNamed(nftSupplyKey),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .name(NON_FUNGIBLE_TOKEN)
                                .supplyKey(nftSupplyKey),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                IntStream.range(0, 10)
                                        .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                        .toList()),
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER))
                                .payingWith(OWNER),
                        tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(OWNER, RECEIVER))
                                .payingWith(OWNER),

                        // do claim
                        tokenClaimAirdrop(
                                        HapiTokenClaimAirdrop.pendingAirdrop(OWNER, RECEIVER, FUNGIBLE_TOKEN),
                                        HapiTokenClaimAirdrop.pendingNFTAirdrop(OWNER, RECEIVER, NON_FUNGIBLE_TOKEN, 1))
                                .payingWith(RECEIVER)
                                .via("claimTxn"))
                .then( // assert txn record
                        getTxnRecord("claimTxn")
                                .hasPriority(recordWith()
                                        .tokenTransfers(includingFungibleMovement(
                                                moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER)))
                                        .tokenTransfers(includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 1)
                                                .between(OWNER, RECEIVER)))),
                        validateChargedUsd("claimTxn", 0.001, 1),
                        // assert balance fungible tokens
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        // assert balances NFT
                        getAccountBalance(RECEIVER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        // assert token associations
                        getAccountInfo(RECEIVER).hasToken(relationshipWith(FUNGIBLE_TOKEN)),
                        getAccountInfo(RECEIVER).hasToken(relationshipWith(NON_FUNGIBLE_TOKEN)));
    }

    @HapiTest
    @DisplayName("FT happy path")
    final Stream<DynamicTest> ftHappyPath() {
        final var account = "account";
        return hapiTest(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(account),
                newKeyNamed(FUNGIBLE_FREEZE_KEY),
                tokenCreate(FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .freezeKey(FUNGIBLE_FREEZE_KEY)
                        .initialSupply(1000L),
                tokenAssociate(account, FUNGIBLE_TOKEN),
                cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(OWNER, account)),
                cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                // Create an airdrop in pending state
                tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .payingWith(account)
                        .via("airdrop"),

                // Verify that a pending state is created and the correct usd is charged
                getTxnRecord("airdrop")
                        .hasPriority(recordWith()
                                .pendingAirdrops(includingFungiblePendingAirdrop(moving(10, FUNGIBLE_TOKEN)
                                        .between(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS)))),
                getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                validateChargedUsd("airdrop", 0.1, 1),

                // Cancel the airdrop
                tokenCancelAirdrop(pendingAirdrop(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                        .payingWith(account)
                        .via("cancelAirdrop"),

                // Verify that the receiver doesn't have the token
                getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                validateChargedUsd("cancelAirdrop", 0.001, 1));
    }

    @HapiTest
    @DisplayName("NFT happy path")
    final Stream<DynamicTest> nftHappyPath() {
        var nftSupplyKey = "nftSupplyKey";
        final var account = "account";
        return hapiTest(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(account),
                newKeyNamed(nftSupplyKey),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .name(NON_FUNGIBLE_TOKEN)
                        .supplyKey(nftSupplyKey),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        IntStream.range(0, 10)
                                .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                .toList()),
                tokenAssociate(account, NON_FUNGIBLE_TOKEN),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, account)),
                cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                // Create an airdrop in pending state
                tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .payingWith(account)
                        .via("airdrop"),

                // Verify that a pending state is created and the correct usd is charged
                getTxnRecord("airdrop")
                        .hasPriority(recordWith()
                                .pendingAirdrops(includingNftPendingAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS)))),
                getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                validateChargedUsd("airdrop", 0.1, 1),

                // Cancel the airdrop
                tokenCancelAirdrop(
                                pendingNFTAirdrop(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 1L))
                        .payingWith(account)
                        .via("cancelAirdrop"),

                // Verify that the receiver doesn't have the token
                getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                validateChargedUsd("cancelAirdrop", 0.001, 1));
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
                                .via("rejectNft"))
                .then(
                        validateChargedUsdWithin(
                                "fungibleTransfer", EXPECTED_FUNGIBLE_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin("nftTransfer", EXPECTED_NFT_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin(
                                "rejectFungible", EXPECTED_FUNGIBLE_REJECT_PRICE_USD, ALLOWED_DIFFERENCE),
                        validateChargedUsdWithin("rejectNft", EXPECTED_NFT_REJECT_PRICE_USD, ALLOWED_DIFFERENCE));
    }

    @HapiTest
    final Stream<DynamicTest> baseCreationsHaveExpectedPrices() {
        final var civilian = "NonExemptPayer";

        final var expectedCommonNoCustomFeesPriceUsd = 1.00;
        final var expectedUniqueNoCustomFeesPriceUsd = 1.00;
        final var expectedCommonWithCustomFeesPriceUsd = 2.00;
        final var expectedUniqueWithCustomFeesPriceUsd = 2.00;

        final var commonNoFees = "commonNoFees";
        final var commonWithFees = "commonWithFees";
        final var uniqueNoFees = "uniqueNoFees";
        final var uniqueWithFees = "uniqueWithFees";

        final var customFeeKey = "customFeeKey";

        return hapiTest(
                cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(TOKEN_TREASURY).balance(0L),
                cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                newKeyNamed(ADMIN_KEY),
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(customFeeKey),
                tokenCreate(commonNoFees)
                        .blankMemo()
                        .entityMemo("")
                        .name(NAME)
                        .symbol("ABCD")
                        .payingWith(civilian)
                        .treasury(TOKEN_TREASURY)
                        .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .adminKey(ADMIN_KEY)
                        .via(txnFor(commonNoFees)),
                tokenCreate(commonWithFees)
                        .blankMemo()
                        .entityMemo("")
                        .name(NAME)
                        .symbol("ABCD")
                        .payingWith(civilian)
                        .treasury(TOKEN_TREASURY)
                        .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .adminKey(ADMIN_KEY)
                        .withCustom(fixedHbarFee(ONE_HBAR, TOKEN_TREASURY))
                        .feeScheduleKey(customFeeKey)
                        .via(txnFor(commonWithFees)),
                tokenCreate(uniqueNoFees)
                        .payingWith(civilian)
                        .blankMemo()
                        .entityMemo("")
                        .name(NAME)
                        .symbol("ABCD")
                        .initialSupply(0L)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .adminKey(ADMIN_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .via(txnFor(uniqueNoFees)),
                tokenCreate(uniqueWithFees)
                        .payingWith(civilian)
                        .blankMemo()
                        .entityMemo("")
                        .name(NAME)
                        .symbol("ABCD")
                        .initialSupply(0L)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .adminKey(ADMIN_KEY)
                        .withCustom(fixedHbarFee(ONE_HBAR, TOKEN_TREASURY))
                        .supplyKey(SUPPLY_KEY)
                        .feeScheduleKey(customFeeKey)
                        .via(txnFor(uniqueWithFees)),
                validateChargedUsdWithin(txnFor(commonNoFees), expectedCommonNoCustomFeesPriceUsd, 0.01),
                validateChargedUsdWithin(txnFor(commonWithFees), expectedCommonWithCustomFeesPriceUsd, 0.01),
                validateChargedUsdWithin(txnFor(uniqueNoFees), expectedUniqueNoCustomFeesPriceUsd, 0.01),
                validateChargedUsdWithin(txnFor(uniqueWithFees), expectedUniqueWithCustomFeesPriceUsd, 0.01));
    }

    @HapiTest
    final Stream<DynamicTest> baseOperationIsChargedExpectedFee() {
        final var htsAmount = 2_345L;
        final var targetToken = "immutableToken";
        final var feeDenom = "denom";
        final var htsCollector = "denomFee";
        final var feeScheduleKey = "feeSchedule";
        final var expectedBasePriceUsd = 0.001;

        return hapiTest(
                newKeyNamed(feeScheduleKey),
                cryptoCreate("civilian").key(feeScheduleKey),
                cryptoCreate(htsCollector),
                tokenCreate(feeDenom).treasury(htsCollector),
                tokenCreate(targetToken)
                        .expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                        .feeScheduleKey(feeScheduleKey),
                tokenFeeScheduleUpdate(targetToken)
                        .signedBy(feeScheduleKey)
                        .payingWith("civilian")
                        .blankMemo()
                        .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                        .via("baseFeeSchUpd"),
                validateChargedUsdWithin("baseFeeSchUpd", expectedBasePriceUsd, 1.0));
    }

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
    final Stream<DynamicTest> nftMintsScaleLinearlyBasedOnNumberOfSerialNumbers() {
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
    final Stream<DynamicTest> basePauseAndUnpauseHaveExpectedPrices() {
        final var expectedBaseFee = 0.001;
        final var token = "token";
        final var tokenPauseTransaction = "tokenPauseTxn";
        final var tokenUnpauseTransaction = "tokenUnpauseTxn";
        final var civilian = "NonExemptPayer";

        return defaultHapiSpec("BasePauseAndUnpauseHaveExpectedPrices")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(PAUSE_KEY),
                        cryptoCreate(civilian).key(PAUSE_KEY))
                .when(
                        tokenCreate(token)
                                .pauseKey(PAUSE_KEY)
                                .treasury(TOKEN_TREASURY)
                                .payingWith(civilian),
                        tokenPause(token).blankMemo().payingWith(civilian).via(tokenPauseTransaction),
                        getTokenInfo(token).hasPauseStatus(Paused),
                        tokenUnpause(token).blankMemo().payingWith(civilian).via(tokenUnpauseTransaction),
                        getTokenInfo(token).hasPauseStatus(Unpaused))
                .then(
                        validateChargedUsd(tokenPauseTransaction, expectedBaseFee),
                        validateChargedUsd(tokenUnpauseTransaction, expectedBaseFee));
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
    final Stream<DynamicTest> updateTokenChargedAsExpected() {
        final var expectedUpdatePriceUsd = 0.001;

        return hapiTest(
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(FUNGIBLE_COMMON_TOKEN)
                        .tokenType(FUNGIBLE_COMMON)
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(0)
                        .entityMemo("")
                        .symbol("a"),
                tokenUpdate(FUNGIBLE_COMMON_TOKEN)
                        .via("uniqueTokenUpdate")
                        .payingWith(TOKEN_TREASURY),
                validateChargedUsd("uniqueTokenUpdate", expectedUpdatePriceUsd));
    }

    @HapiTest
    final Stream<DynamicTest> updateNftChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.001;
        final var nftUpdateTxn = "nftUpdateTxn";

        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(METADATA_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .supplyKey(SUPPLY_KEY)
                        .metadataKey(METADATA_KEY)
                        .initialSupply(0L),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                copyFromUtf8("a"),
                                copyFromUtf8("b"),
                                copyFromUtf8("c"),
                                copyFromUtf8("d"),
                                copyFromUtf8("e"),
                                copyFromUtf8("f"),
                                copyFromUtf8("g"))),
                tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(7L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn),
                validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }

    @HapiTest
    final Stream<DynamicTest> deleteTokenChargedAsExpected() {
        final var expectedDeletePriceUsd = 0.001;

        return hapiTest(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(MULTI_KEY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(FUNGIBLE_COMMON_TOKEN)
                        .tokenType(FUNGIBLE_COMMON)
                        .adminKey(MULTI_KEY),
                tokenDelete(FUNGIBLE_COMMON_TOKEN)
                        .via("uniqueTokenDelete")
                        .payingWith(MULTI_KEY),
                validateChargedUsd("uniqueTokenDelete", expectedDeletePriceUsd));
    }

    @HapiTest
    final Stream<DynamicTest> updateMultipleNftsFeeChargedAsExpected() {
        final var expectedNftUpdatePriceUsd = 0.005;
        final var nftUpdateTxn = "nftUpdateTxn";

        return hapiTest(
                newKeyNamed(SUPPLY_KEY),
                newKeyNamed(WIPE_KEY),
                newKeyNamed(METADATA_KEY),
                cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .treasury(TOKEN_TREASURY)
                        .maxSupply(12L)
                        .wipeKey(WIPE_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .metadataKey(METADATA_KEY)
                        .initialSupply(0L),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                copyFromUtf8("a"),
                                copyFromUtf8("b"),
                                copyFromUtf8("c"),
                                copyFromUtf8("d"),
                                copyFromUtf8("e"),
                                copyFromUtf8("f"),
                                copyFromUtf8("g"))),
                tokenUpdateNfts(NON_FUNGIBLE_TOKEN, NFT_TEST_METADATA, List.of(1L, 2L, 3L, 4L, 5L))
                        .signedBy(TOKEN_TREASURY, METADATA_KEY)
                        .payingWith(TOKEN_TREASURY)
                        .fee(10 * ONE_HBAR)
                        .via(nftUpdateTxn),
                validateChargedUsdWithin(nftUpdateTxn, expectedNftUpdatePriceUsd, 0.01));
    }

    private String txnFor(String tokenSubType) {
        return tokenSubType + "Txn";
    }
}
