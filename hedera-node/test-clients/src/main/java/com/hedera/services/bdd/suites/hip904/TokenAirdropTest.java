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

package com.hedera.services.bdd.suites.hip904;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungiblePendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNftPendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNonfungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithDecimals;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FREEZE_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_HAS_PENDING_AIRDROPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
@DisplayName("Token airdrop")
public class TokenAirdropTest {
    private static final String OWNER = "owner";
    // receivers
    private static final String RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS = "receiverWithUnlimitedAutoAssociations";
    private static final String RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS = "receiverWithFreeAutoAssociations";
    private static final String RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS = "receiverWithoutFreeAutoAssociations";
    private static final String RECEIVER_WITH_0_AUTO_ASSOCIATIONS = "receiverWith0AutoAssociations";
    private static final String ASSOCIATED_RECEIVER = "associatedReceiver";
    // tokens
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    // tokens with custom fees
    private static final String FT_WITH_HBAR_FIXED_FEE = "fungibleTokenWithHbarCustomFee";
    private static final String FT_WITH_HTS_FIXED_FEE = "fungibleTokenWithHtsCustomFee";
    private static final String FT_WITH_FRACTIONAL_FEE = "fungibleTokenWithFractionalFee";
    private static final String NFT_WITH_HTS_FIXED_FEE = "NftWithHtsFixedFee";
    private static final String NFT_WITH_ROYALTY_FEE = "NftWithRoyaltyFee";
    private static final String DENOM_TOKEN = "denomToken";
    private static final String HTS_COLLECTOR = "htsCollector";
    private static final String HTS_COLLECTOR2 = "htsCollector2";
    private static final String HBAR_COLLECTOR = "hbarCollector";
    private static final String TREASURY_FOR_CUSTOM_FEE_TOKENS = "treasuryForCustomFeeTokens";
    private static final String OWNER_OF_TOKENS_WITH_CUSTOM_FEES = "ownerOfTokensWithCustomFees";
    // all collectors exempt
    private static final String NFT_ALL_COLLECTORS_EXEMPT_OWNER = "nftAllCollectorsExemptOwner";
    private static final String NFT_ALL_COLLECTORS_EXEMPT_RECEIVER = "nftAllCollectorsExemptReceiver";
    private static final String NFT_ALL_COLLECTORS_EXEMPT_COLLECTOR = "nftAllCollectorsExemptCollector";
    private static final String NFT_ALL_COLLECTORS_EXEMPT_TOKEN = "nftAllCollectorsExemptToken";
    private static final String NFT_ALL_COLLECTORS_EXEMPT_KEY = "nftAllCollectorsExemptKey";

    private static final String FT_ALL_COLLECTORS_EXEMPT_OWNER = "ftAllCollectorsExemptOwner";
    private static final String FT_ALL_COLLECTORS_EXEMPT_RECEIVER = "ftAllCollectorsExemptReceiver";
    private static final String FT_ALL_COLLECTORS_EXEMPT_COLLECTOR = "ftAllCollectorsExemptCollector";
    private static final String FT_ALL_COLLECTORS_EXEMPT_TOKEN = "ftAllCollectorsExemptToken";

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.overrideInClass(Map.of(
                "tokens.airdrops.enabled", "true",
                "entities.unlimitedAutoAssociationsEnabled", "true"));
        lifecycle.doAdhoc(setUpTokensAndAllReceivers());
    }

    @Nested
    @DisplayName("to existing accounts")
    class AirdropToExistingAccounts {

        @Nested
        @DisplayName("with free auto associations slots")
        class AirdropToExistingAccountsWhitFreeAutoAssociations {

            @HapiTest
            final Stream<DynamicTest> tokenAirdropToExistingAccountsTransfers() {
                return defaultHapiSpec("should transfer fungible tokens")
                        .given()
                        .when( // associated receiver and receivers with free auto association slots
                                tokenAirdrop(
                                                moveFungibleTokensTo(ASSOCIATED_RECEIVER),
                                                moveFungibleTokensTo(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                                moveFungibleTokensTo(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS))
                                        .payingWith(OWNER)
                                        .via("fungible airdrop"))
                        .then( // assert txn record
                                getTxnRecord("fungible airdrop")
                                        .hasPriority(recordWith()
                                                .tokenTransfers(includingFungibleMovement(moving(30, FUNGIBLE_TOKEN)
                                                        .distributing(
                                                                OWNER,
                                                                RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS,
                                                                RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS,
                                                                ASSOCIATED_RECEIVER)))),
                                // assert balance
                                getAccountBalance(ASSOCIATED_RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                                getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(FUNGIBLE_TOKEN, 10),
                                getAccountBalance(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(FUNGIBLE_TOKEN, 10),
                                // associate receiver - will be simple transfer
                                // unlimited associations receiver - 0.1 (because not associated yet)
                                // free auto associations receiver - 0.1 (because not associated yet)
                                validateChargedUsd("fungible airdrop", 0.2, 1));
            }

            @HapiTest
            final Stream<DynamicTest> nftAirdropToExistingAccountsTransfers() {
                return defaultHapiSpec("should transfer NFTs")
                        .given()
                        .when( // receivers with free auto association slots
                                tokenAirdrop(
                                                movingUnique(NON_FUNGIBLE_TOKEN, 3L)
                                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                                movingUnique(NON_FUNGIBLE_TOKEN, 4L)
                                                        .between(OWNER, RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS),
                                                movingUnique(NON_FUNGIBLE_TOKEN, 5L)
                                                        .between(OWNER, ASSOCIATED_RECEIVER))
                                        .payingWith(OWNER)
                                        .via("non fungible airdrop"))
                        .then( // assert txn record
                                getTxnRecord("non fungible airdrop")
                                        .hasPriority(recordWith()
                                                .tokenTransfers(includingNonfungibleMovement(
                                                        movingUnique(NON_FUNGIBLE_TOKEN, 3L, 4L, 5L)
                                                                .distributing(
                                                                        RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS,
                                                                        RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS,
                                                                        ASSOCIATED_RECEIVER)))),
                                // assert account balances
                                getAccountBalance(ASSOCIATED_RECEIVER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                                getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                                getAccountBalance(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                                // associate receiver - will be simple transfer
                                // unlimited associations receiver - 0.1 (because not associated yet)
                                // free auto associations receiver - 0.1 (because not associated yet)
                                validateChargedUsd("non fungible airdrop", 0.2, 1));
            }
        }

        @Nested
        @DisplayName("without free auto associations slots")
        class AirdropToExistingAccountsWithoutFreeAutoAssociations {
            @HapiTest
            final Stream<DynamicTest> tokenAirdropToExistingAccountsPending() {
                return defaultHapiSpec("fungible tokens should be in pending state")
                        .given()
                        .when(tokenAirdrop(
                                        moveFungibleTokensTo(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                        moveFungibleTokensTo(RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .via("fungible airdrop"))
                        .then( // assert txn record
                                getTxnRecord("fungible airdrop")
                                        .hasPriority(recordWith()
                                                .pendingAirdrops(includingFungiblePendingAirdrop(
                                                        moveFungibleTokensTo(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                                        moveFungibleTokensTo(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)))),
                                // assert balances
                                getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(FUNGIBLE_TOKEN, 0),
                                getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(FUNGIBLE_TOKEN, 0),
                                // zero auto associations receiver - 0.1 (creates pending airdrop)
                                // without free auto associations receiver - 0.1 (creates pending airdrop)
                                validateChargedUsd("fungible airdrop", 0.2, 1));
            }

            @HapiTest
            final Stream<DynamicTest> nftAirdropToExistingAccountsPending() {
                return defaultHapiSpec("NFTs should be in pending state")
                        .given()
                        .when( // without free auto association slots
                                tokenAirdrop(
                                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                        .between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                                movingUnique(NON_FUNGIBLE_TOKEN, 2L)
                                                        .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))
                                        .payingWith(OWNER)
                                        .via("non fungible airdrop"))
                        .then( // assert the pending list
                                getTxnRecord("non fungible airdrop")
                                        .hasPriority(recordWith()
                                                .pendingAirdrops(includingNftPendingAirdrop(
                                                        movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                                .between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                                        movingUnique(NON_FUNGIBLE_TOKEN, 2L)
                                                                .between(
                                                                        OWNER,
                                                                        RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)))),

                                // assert account balances
                                getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                                getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                                // zero auto associations receiver - 0.1 (creates pending airdrop)
                                // without free auto associations receiver - 0.1 (creates pending airdrop)
                                validateChargedUsd("non fungible airdrop", 0.2, 1));
            }
        }

        @HapiTest
        @DisplayName("in pending state")
        final Stream<DynamicTest> consequentAirdrops() {
            // Verify that when sending 2 consequent airdrops to a recipient,
            // which associated themselves to the token after the first airdrop,
            // the second airdrop is directly transferred to the recipient and the first airdrop remains in pending
            // state
            var receiver = "receiver";
            return defaultHapiSpec("should be not affected by following airdrops")
                    .given(
                            cryptoCreate(receiver).maxAutomaticTokenAssociations(0),
                            // send first airdrop
                            tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver))
                                    .payingWith(OWNER)
                                    .via("first"),
                            getTxnRecord("first")
                                    // assert pending airdrops
                                    .hasPriority(recordWith()
                                            .pendingAirdrops(includingFungiblePendingAirdrop(
                                                    moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver)))),
                            // creates pending airdrop
                            validateChargedUsd("first", 0.1, 10))
                    .when(tokenAssociate(receiver, FUNGIBLE_TOKEN))
                    .then( // this time tokens should be transferred
                            tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver))
                                    .payingWith(OWNER)
                                    .via("second"),
                            // assert OWNER and receiver accounts to ensure first airdrop is still in pending state
                            getTxnRecord("second")
                                    // assert transfers
                                    .hasPriority(recordWith()
                                            .tokenTransfers(includingFungibleMovement(
                                                    moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver)))),
                            // just a crypto transfer
                            validateChargedUsd("second", 0.001, 10),
                            // assert the account balance
                            getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 10));
        }
    }

    // custom fees
    @Nested
    @DisplayName("with custom fees for")
    class AirdropTokensWithCustomFees {

        private static long hbarFee = 1_000L;
        private static long tokenTotal = 1_000L;
        private static long htsFee = 100L;

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle lifecycle) {
            lifecycle.doAdhoc(setUpTokensWithCustomFees(1_000_000L, hbarFee, htsFee));
        }

        @HapiTest
        @DisplayName("fungible token with fixed Hbar fee")
        final Stream<DynamicTest> airdropFungibleWithFixedHbarCustomFee() {
            return defaultHapiSpec(" sender should prepay hbar custom fee")
                    .given(
                            cryptoCreate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES).balance(ONE_MILLION_HBARS),
                            tokenAssociate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES, FT_WITH_HBAR_FIXED_FEE),
                            cryptoTransfer(moving(1000, FT_WITH_HBAR_FIXED_FEE)
                                    .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER_OF_TOKENS_WITH_CUSTOM_FEES)))
                    .when(tokenAirdrop(moving(1, FT_WITH_HBAR_FIXED_FEE)
                                    .between(OWNER_OF_TOKENS_WITH_CUSTOM_FEES, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                            .fee(ONE_HUNDRED_HBARS)
                            .payingWith(OWNER_OF_TOKENS_WITH_CUSTOM_FEES)
                            .via("transferTx"))
                    .then(
                            // assert balances
                            getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                                    .hasTokenBalance(FT_WITH_HBAR_FIXED_FEE, 0),
                            getAccountBalance(HBAR_COLLECTOR).hasTinyBars(hbarFee),
                            withOpContext((spec, log) -> {
                                final var record = getTxnRecord("transferTx");
                                allRunFor(spec, record);
                                final var txFee = record.getResponseRecord().getTransactionFee();
                                // the token should not be transferred but the custom fee should be charged
                                final var ownerBalance = getAccountBalance(OWNER_OF_TOKENS_WITH_CUSTOM_FEES)
                                        .hasTinyBars(ONE_MILLION_HBARS - (txFee + hbarFee))
                                        .hasTokenBalance(FT_WITH_HBAR_FIXED_FEE, 1000);
                                allRunFor(spec, ownerBalance);
                            }),
                            // pending airdrop should be created
                            validateChargedUsd("transferTx", 0.1, 10));
        }

        @HapiTest
        @DisplayName("NFT with 2 layers fixed Hts fee")
        final Stream<DynamicTest> transferNonFungibleWithFixedHtsCustomFees2Layers() {
            return defaultHapiSpec("sender should prepay hts custom fee")
                    .given(
                            cryptoCreate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES).balance(ONE_MILLION_HBARS),
                            tokenAssociate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES, DENOM_TOKEN),
                            tokenAssociate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES, FT_WITH_HTS_FIXED_FEE),
                            tokenAssociate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES, NFT_WITH_HTS_FIXED_FEE),
                            tokenAssociate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FT_WITH_HTS_FIXED_FEE),
                            cryptoTransfer(
                                    movingUnique(NFT_WITH_HTS_FIXED_FEE, 1L)
                                            .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER_OF_TOKENS_WITH_CUSTOM_FEES),
                                    moving(tokenTotal, FT_WITH_HTS_FIXED_FEE)
                                            .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER_OF_TOKENS_WITH_CUSTOM_FEES),
                                    moving(tokenTotal, DENOM_TOKEN)
                                            .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER_OF_TOKENS_WITH_CUSTOM_FEES)))
                    .when(
                            tokenAirdrop(movingUnique(NFT_WITH_HTS_FIXED_FEE, 1L)
                                            .between(
                                                    OWNER_OF_TOKENS_WITH_CUSTOM_FEES,
                                                    RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                    .fee(ONE_HUNDRED_HBARS)
                                    .payingWith(OWNER_OF_TOKENS_WITH_CUSTOM_FEES)
                                    .via("transferTx"),
                            // pending airdrop should be created
                            validateChargedUsd("transferTx", 0.1, 10))
                    .then(
                            getAccountBalance(OWNER_OF_TOKENS_WITH_CUSTOM_FEES)
                                    .hasTokenBalance(NFT_WITH_HTS_FIXED_FEE, 1)
                                    .hasTokenBalance(FT_WITH_HTS_FIXED_FEE, tokenTotal - htsFee)
                                    .hasTokenBalance(DENOM_TOKEN, tokenTotal - htsFee),
                            getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                                    .hasTokenBalance(NFT_WITH_HTS_FIXED_FEE, 0));
        }

        @HapiTest
        @DisplayName("fungible token with fractional fee")
        final Stream<DynamicTest> fungibleTokenWithFractionalFeesPaidByReceiverFails() {
            return defaultHapiSpec("should fail - INVALID_TRANSACTION")
                    .given()
                    .when(
                            tokenAssociate(OWNER, FT_WITH_FRACTIONAL_FEE),
                            cryptoTransfer(
                                    moving(100, FT_WITH_FRACTIONAL_FEE).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)))
                    .then(tokenAirdrop(moving(25, FT_WITH_FRACTIONAL_FEE)
                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .payingWith(OWNER)
                            .hasKnownStatus(INVALID_TRANSACTION));
        }

        @HapiTest
        @DisplayName("NFT with royalty fee")
        final Stream<DynamicTest> nftWithRoyaltyFeesPaidByReceiverFails() {
            return defaultHapiSpec("should fail - INVALID_TRANSACTION")
                    .given()
                    .when(
                            tokenAssociate(OWNER, NFT_WITH_ROYALTY_FEE),
                            cryptoTransfer(movingUnique(NFT_WITH_ROYALTY_FEE, 1L)
                                    .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)))
                    .then(tokenAirdrop(movingUnique(NFT_WITH_ROYALTY_FEE, 1L)
                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .signedByPayerAnd(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS, OWNER)
                            .hasKnownStatus(INVALID_TRANSACTION));
        }

        @HapiTest
        @DisplayName("NFT with royalty fee with fee collector as receiver")
        final Stream<DynamicTest> nftWithRoyaltyFeesPaidByReceiverWithFeeCollectorReceiver() {
            return hapiTest(
                    cryptoCreate(OWNER),
                    tokenAssociate(OWNER, NFT_WITH_ROYALTY_FEE),
                    cryptoTransfer(
                            movingUnique(NFT_WITH_ROYALTY_FEE, 2L).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)),
                    tokenAirdrop(movingUnique(NFT_WITH_ROYALTY_FEE, 2L).between(OWNER, HTS_COLLECTOR))
                            .signedByPayerAnd(HTS_COLLECTOR, OWNER));
        }

        @HapiTest
        @DisplayName("FT with royalty fee with fee collector as receiver")
        final Stream<DynamicTest> ftWithRoyaltyFeesPaidByReceiverWithFeeCollectorReceiver() {
            return hapiTest(
                    cryptoCreate(OWNER),
                    tokenAssociate(OWNER, FT_WITH_HTS_FIXED_FEE),
                    tokenAssociate(OWNER, DENOM_TOKEN),
                    cryptoTransfer(
                            moving(tokenTotal, DENOM_TOKEN).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER),
                            moving(tokenTotal, FT_WITH_HTS_FIXED_FEE).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)),
                    tokenAirdrop(moving(50, FT_WITH_HTS_FIXED_FEE).between(OWNER, HTS_COLLECTOR))
                            .signedByPayerAnd(HTS_COLLECTOR, OWNER));
        }

        @HapiTest
        @DisplayName("NFT with royalty fee with treasury as receiver")
        final Stream<DynamicTest> nftWithRoyaltyFeesPaidByReceiverWithTreasuryReceiver() {
            return hapiTest(
                    cryptoCreate(OWNER),
                    tokenAssociate(OWNER, NFT_WITH_ROYALTY_FEE),
                    cryptoTransfer(
                            movingUnique(NFT_WITH_ROYALTY_FEE, 3L).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)),
                    tokenAirdrop(movingUnique(NFT_WITH_ROYALTY_FEE, 3L).between(OWNER, TREASURY_FOR_CUSTOM_FEE_TOKENS))
                            .signedByPayerAnd(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER));
        }

        @HapiTest
        @DisplayName("FT with royalty fee with treasury as receiver")
        final Stream<DynamicTest> ftWithRoyaltyFeesPaidByReceiverWithTreasuryReceiver() {
            return hapiTest(
                    cryptoCreate(OWNER),
                    tokenAssociate(OWNER, FT_WITH_HTS_FIXED_FEE),
                    tokenAssociate(OWNER, DENOM_TOKEN),
                    cryptoTransfer(
                            moving(tokenTotal, DENOM_TOKEN).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER),
                            moving(tokenTotal, FT_WITH_HTS_FIXED_FEE).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)),
                    tokenAirdrop(moving(50, FT_WITH_HTS_FIXED_FEE).between(OWNER, TREASURY_FOR_CUSTOM_FEE_TOKENS))
                            .signedByPayerAnd(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER));
        }

        // When a receiver is a custom fee collector it should be exempt from the custom fee
        @HapiTest
        @DisplayName("NFT with royalty fee and allCollectorsExempt=true airdrop to NFT collector")
        final Stream<DynamicTest> nftWithARoyaltyFeeAndAllCollectorsExemptTrueAirdropToCollector() {
            return hapiTest(
                    mintToken(
                            NFT_ALL_COLLECTORS_EXEMPT_TOKEN, List.of(ByteStringUtils.wrapUnsafely("meta".getBytes()))),
                    cryptoTransfer(movingUnique(NFT_ALL_COLLECTORS_EXEMPT_TOKEN, 1L)
                            .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, NFT_ALL_COLLECTORS_EXEMPT_OWNER)),
                    tokenAirdrop(movingUnique(NFT_ALL_COLLECTORS_EXEMPT_TOKEN, 1L)
                                    .between(NFT_ALL_COLLECTORS_EXEMPT_OWNER, NFT_ALL_COLLECTORS_EXEMPT_RECEIVER))
                            .signedByPayerAnd(NFT_ALL_COLLECTORS_EXEMPT_RECEIVER, NFT_ALL_COLLECTORS_EXEMPT_OWNER));
        }

        // When a receiver is a custom fee collector it should be exempt from the custom fee
        @HapiTest
        @DisplayName("FT with royalty fee and allCollectorsExempt=true airdrop to NFT collector")
        final Stream<DynamicTest> ftWithARoyaltyFeeAndAllCollectorsExemptTrueAirdropToCollector() {
            return hapiTest(
                    cryptoTransfer(moving(50, FT_ALL_COLLECTORS_EXEMPT_TOKEN)
                            .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, FT_ALL_COLLECTORS_EXEMPT_OWNER)),
                    tokenAirdrop(moving(50, FT_ALL_COLLECTORS_EXEMPT_TOKEN)
                                    .between(FT_ALL_COLLECTORS_EXEMPT_OWNER, FT_ALL_COLLECTORS_EXEMPT_RECEIVER))
                            .signedByPayerAnd(FT_ALL_COLLECTORS_EXEMPT_RECEIVER, FT_ALL_COLLECTORS_EXEMPT_OWNER));
        }
    }

    @Nested
    @DisplayName("to non existing account")
    class AirdropToNonExistingAccounts {
        @HapiTest
        @DisplayName("ED25519 key")
        final Stream<DynamicTest> airdropToNonExistingED25519Account() {
            var ed25519key = "ed25519key";
            return defaultHapiSpec("should auto-create and transfer the tokens")
                    .given(newKeyNamed(ed25519key))
                    .when(tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, ed25519key))
                            .payingWith(OWNER)
                            .via("ed25519Receiver"))
                    .then(
                            getAutoCreatedAccountBalance(ed25519key).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                            // Any new auto-creation needs to explicitly associate token. So it will be $0.1
                            validateChargedUsd("ed25519Receiver", 0.1, 1));
        }

        @HapiTest
        @DisplayName("SECP256K1 key account")
        final Stream<DynamicTest> airdropToNonExistingSECP256K1Account() {
            var secp256K1 = "secp256K1";
            return defaultHapiSpec("should auto-create and transfer the tokens")
                    .given(newKeyNamed(secp256K1))
                    .when(tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, secp256K1))
                            .payingWith(OWNER)
                            .via("secp256k1Receiver"))
                    .then(
                            getAutoCreatedAccountBalance(secp256K1).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                            // Any new auto-creation needs to explicitly associate token. So it will be $0.1
                            validateChargedUsd("secp256k1Receiver", 0.1, 1));
        }

        @HapiTest
        @DisplayName("EVM address account")
        final Stream<DynamicTest> airdropToNonExistingEvmAddressAccount() {
            // calculate evmAddress;
            final byte[] publicKey =
                    CommonUtils.unhex("02641dc27aa851ddc5a238dc569718f82b4e5eb3b61030942432fe7ac9088459c5");
            final ByteString evmAddress = ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(publicKey));

            return defaultHapiSpec("should lazy-create and transfer the tokens")
                    .given()
                    .when(tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, evmAddress))
                            .payingWith(OWNER)
                            .via("evmAddressReceiver"))
                    .then(
                            getAliasedAccountBalance(evmAddress).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                            // Any new auto-creation needs to explicitly associate token. So it will be $0.1
                            validateChargedUsd("evmAddressReceiver", 0.1, 1));
        }
    }

    @Nested
    @DisplayName("negative scenarios")
    class InvalidAirdrops {
        @HapiTest
        @DisplayName("containing invalid token id")
        final Stream<DynamicTest> airdropInvalidTokenIdFails() {
            return defaultHapiSpec("should fail - INVALID_TOKEN_ID")
                    .given()
                    .when()
                    .then(withOpContext((spec, opLog) -> {
                        final var bogusTokenId = TokenID.newBuilder().setTokenNum(9999L);
                        spec.registry().saveTokenId("nonexistent", bogusTokenId.build());
                        allRunFor(
                                spec,
                                tokenAirdrop(movingWithDecimals(1L, "nonexistent", 2)
                                                .betweenWithDecimals(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                        .payingWith(OWNER)
                                        .via("transferTx")
                                        .hasKnownStatus(INVALID_TOKEN_ID),
                                validateChargedUsd("transferTx", 0.001, 10));
                    }));
        }

        @HapiTest
        @DisplayName("containing negative NFT serial number")
        final Stream<DynamicTest> airdropNFTNegativeSerial() {
            return defaultHapiSpec("should fail - INVALID_TOKEN_NFT_SERIAL_NUMBER")
                    .given()
                    .when()
                    .then(tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, -5)
                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER));
        }

        /**
         *  When we set the token value as negative value, the transfer list that we aggregate just switch
         *  the roles of sender and receiver, so the sender checks will fail.
         */
        @HapiTest
        @DisplayName("containing negative amount")
        final Stream<DynamicTest> airdropNegativeAmountFails3() {
            var receiver = "receiver";
            return defaultHapiSpec("should fail - INVALID_SIGNATURE")
                    .given(
                            cryptoCreate(receiver),
                            tokenAssociate(receiver, FUNGIBLE_TOKEN),
                            cryptoTransfer(moving(15, FUNGIBLE_TOKEN).between(OWNER, receiver)))
                    .when()
                    .then(tokenAirdrop(moving(-15, FUNGIBLE_TOKEN).between(OWNER, receiver))
                            .hasKnownStatus(INVALID_SIGNATURE));
        }

        @HapiTest
        @DisplayName("with missing sender's signature")
        final Stream<DynamicTest> missingSenderSigFails() {
            return defaultHapiSpec("should fail - INVALID_SIGNATURE")
                    .given()
                    .when()
                    .then(tokenAirdrop(
                                    moving(1, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .hasPrecheck(INVALID_SIGNATURE));
        }

        @HapiTest
        @DisplayName("with missing owner's signature")
        final Stream<DynamicTest> missingPayerSigFails() {
            var spender = "spender";
            return defaultHapiSpec("should fail - INVALID_SIGNATURE")
                    .given(cryptoCreate(spender).balance(ONE_HUNDRED_HBARS))
                    .when(cryptoApproveAllowance()
                            .payingWith(OWNER)
                            .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, spender, 100))
                    .then(tokenAirdrop(movingWithAllowance(50, FUNGIBLE_TOKEN)
                                    .between(spender, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            // Should be signed by owner as well
                            .signedBy(spender)
                            .hasPrecheck(INVALID_SIGNATURE));
        }

        @HapiTest
        @DisplayName("owner does not have enough balance")
        final Stream<DynamicTest> ownerNotEnoughBalanceFails() {
            var lowBalanceOwner = "lowBalanceOwner";
            return defaultHapiSpec("should fail - INVALID_ACCOUNT_AMOUNTS")
                    .given(
                            cryptoCreate(lowBalanceOwner),
                            tokenAssociate(lowBalanceOwner, FUNGIBLE_TOKEN),
                            cryptoTransfer(moving(1, FUNGIBLE_TOKEN).between(OWNER, lowBalanceOwner)))
                    .when()
                    .then(tokenAirdrop(moving(99, FUNGIBLE_TOKEN)
                                    .between(lowBalanceOwner, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .payingWith(lowBalanceOwner)
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE));
        }

        @HapiTest
        @DisplayName("containing duplicate entries in the transfer list")
        final Stream<DynamicTest> duplicateEntryInTokenTransferFails() {
            return defaultHapiSpec("should fail - INVALID_ACCOUNT_AMOUNTS")
                    .given()
                    .when()
                    .then(tokenAirdrop(
                                    movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                            .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                    movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                            .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .payingWith(OWNER)
                            .hasPrecheck(INVALID_ACCOUNT_AMOUNTS));
        }

        @HapiTest
        @DisplayName("already exists in pending airdrop state")
        final Stream<DynamicTest> duplicateEntryInPendingStateFails() {
            var receiver = "receiver";
            return defaultHapiSpec("should fail - PENDING_NFT_AIRDROP_ALREADY_EXISTS")
                    .given(cryptoCreate(receiver).maxAutomaticTokenAssociations(0))
                    .when()
                    .then(
                            tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, receiver))
                                    .payingWith(OWNER),
                            tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, receiver))
                                    .payingWith(OWNER)
                                    .hasKnownStatus(PENDING_NFT_AIRDROP_ALREADY_EXISTS));
        }

        @HapiTest
        @DisplayName("has transfer list size above the max")
        final Stream<DynamicTest> aboveMaxTransfersFails() {
            return defaultHapiSpec("should fail - INVALID_TRANSACTION_BODY")
                    .given(
                            createTokenWithName("FUNGIBLE1"),
                            createTokenWithName("FUNGIBLE2"),
                            createTokenWithName("FUNGIBLE3"),
                            createTokenWithName("FUNGIBLE4"),
                            createTokenWithName("FUNGIBLE5"),
                            createTokenWithName("FUNGIBLE6"),
                            createTokenWithName("FUNGIBLE7"),
                            createTokenWithName("FUNGIBLE8"),
                            createTokenWithName("FUNGIBLE9"),
                            createTokenWithName("FUNGIBLE10"),
                            createTokenWithName("FUNGIBLE11"))
                    .when()
                    .then(tokenAirdrop(
                                    defaultMovementOfToken("FUNGIBLE1"),
                                    defaultMovementOfToken("FUNGIBLE2"),
                                    defaultMovementOfToken("FUNGIBLE3"),
                                    defaultMovementOfToken("FUNGIBLE4"),
                                    defaultMovementOfToken("FUNGIBLE5"),
                                    defaultMovementOfToken("FUNGIBLE6"),
                                    defaultMovementOfToken("FUNGIBLE7"),
                                    defaultMovementOfToken("FUNGIBLE8"),
                                    defaultMovementOfToken("FUNGIBLE9"),
                                    defaultMovementOfToken("FUNGIBLE10"),
                                    defaultMovementOfToken("FUNGIBLE11"))
                            .payingWith(OWNER)
                            .hasPrecheck(INVALID_TRANSACTION_BODY));
        }

        @HapiTest
        @DisplayName("airdrop from sender that is not associated with the fungible token")
        final Stream<DynamicTest> airdropFungibleTokenNotAssociatedWithSender() {
            final String OWNER_TWO = "owner2";
            return hapiTest(
                    cryptoCreate(OWNER_TWO).balance(ONE_HUNDRED_HBARS),
                    tokenAirdrop(moving(50, FUNGIBLE_TOKEN).between(OWNER_TWO, ASSOCIATED_RECEIVER))
                            .signedByPayerAnd(OWNER_TWO)
                            .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
        }

        @HapiTest
        @DisplayName("airdrop from sender that is not associated with the NFT")
        final Stream<DynamicTest> airdropNFTNotAssociatedWithSender() {
            final String OWNER_TWO = "owner2";
            return hapiTest(
                    cryptoCreate(OWNER_TWO).balance(ONE_HUNDRED_HBARS),
                    tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER_TWO, ASSOCIATED_RECEIVER))
                            .signedByPayerAnd(OWNER_TWO)
                            .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
        }

        @HapiTest
        @DisplayName("with different payer signature")
        final Stream<DynamicTest> missingTheRightPayerSigFails() {
            final String OWNER_TWO = "owner2";
            return hapiTest(
                    cryptoCreate(OWNER_TWO).balance(ONE_HUNDRED_HBARS),
                    tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, ASSOCIATED_RECEIVER))
                            .signedByPayerAnd(OWNER_TWO)
                            .hasKnownStatus(INVALID_SIGNATURE));
        }

        @HapiTest
        @DisplayName("when sending fungible token to system address")
        final Stream<DynamicTest> fungibleTokenReceiverSystemAddress() {
            final String ALICE = "alice";
            final String FUNGIBLE_TOKEN_A = "fungibleTokenA";
            return hapiTest(
                    cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
                    tokenCreate(FUNGIBLE_TOKEN_A)
                            .treasury(ALICE)
                            .tokenType(FUNGIBLE_COMMON)
                            .initialSupply(100L),
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN_A).between(ALICE, FREEZE_ADMIN))
                            .signedByPayerAnd(ALICE)
                            .hasKnownStatus(INVALID_RECEIVING_NODE_ACCOUNT));
        }

        @HapiTest
        @DisplayName("when sending nft to system address")
        final Stream<DynamicTest> nftTokenReceiverSystemAddress() {
            final String ALICE = "alice";
            return hapiTest(
                    cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
                    tokenAssociate(ALICE, NON_FUNGIBLE_TOKEN),
                    tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(ALICE, FREEZE_ADMIN))
                            .signedByPayerAnd(ALICE)
                            .hasPrecheck(INVALID_RECEIVING_NODE_ACCOUNT));
        }

        @HapiTest
        @DisplayName("with missing crypto allowance")
        final Stream<DynamicTest> missingSpenderAllowancesFails() {
            var spender = "spender";
            return hapiTest(
                    cryptoCreate(spender).balance(ONE_HUNDRED_HBARS),
                    tokenAssociate(spender, NON_FUNGIBLE_TOKEN),
                    tokenAirdrop(TokenMovement.movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1L)
                                    .between(spender, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .signedByPayerAnd(spender, OWNER)
                            .hasKnownStatus(SPENDER_DOES_NOT_HAVE_ALLOWANCE));
        }

        @HapiTest
        @DisplayName("spender has more allowances more that the owner balance")
        final Stream<DynamicTest> spenderHasMoreAllowancesThatTheOwner() {
            final String ALICE = "alice";
            final String BOB = "bob";
            final String CAROL = "carol";
            final String FUNGIBLE_TOKEN_A = "fungibleTokenA";
            return hapiTest(
                    cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(BOB).balance(ONE_HUNDRED_HBARS),
                    cryptoCreate(CAROL).balance(ONE_HUNDRED_HBARS),
                    tokenCreate(FUNGIBLE_TOKEN_A)
                            .treasury(ALICE)
                            .tokenType(FUNGIBLE_COMMON)
                            .initialSupply(15L),
                    tokenAssociate(BOB, FUNGIBLE_TOKEN_A),
                    tokenAssociate(CAROL, FUNGIBLE_TOKEN_A),
                    cryptoApproveAllowance().payingWith(ALICE).addTokenAllowance(ALICE, FUNGIBLE_TOKEN_A, BOB, 10L),
                    tokenAirdrop(moving(10L, FUNGIBLE_TOKEN_A).between(ALICE, CAROL))
                            .signedByPayerAnd(ALICE),
                    tokenAirdrop(movingWithAllowance(6L, FUNGIBLE_TOKEN_A).between(ALICE, CAROL))
                            .signedBy(BOB)
                            .payingWith(BOB)
                            .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE));
        }

        @HapiTest
        @DisplayName("FT to deleted ECDSA account")
        final Stream<DynamicTest> ftOnDeletedECDSAAccount() {
            final var ecdsaKey = "ecdsaKey";
            final var deletedAccount = "deletedAccount";
            return hapiTest(
                    newKeyNamed(ecdsaKey).shape(SigControl.SECP256K1_ON),
                    cryptoCreate(deletedAccount).key(ecdsaKey),
                    cryptoDelete(deletedAccount),
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, deletedAccount))
                            .signedBy(OWNER)
                            .payingWith(OWNER)
                            .hasKnownStatus(ACCOUNT_DELETED));
        }

        @HapiTest
        @DisplayName("NFT to deleted ECDSA account")
        final Stream<DynamicTest> nftToDeletedECDSAAccount() {
            final var ecdsaKey = "ecdsaKey";
            final var deletedAccount = "deletedAccount";
            return hapiTest(
                    newKeyNamed(ecdsaKey).shape(SigControl.SECP256K1_ON),
                    cryptoCreate(deletedAccount).key(ecdsaKey),
                    cryptoDelete(deletedAccount),
                    tokenAirdrop(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 6)
                                    .between(OWNER, deletedAccount))
                            .signedBy(OWNER)
                            .payingWith(OWNER)
                            .hasKnownStatus(ACCOUNT_DELETED));
        }

        @HapiTest
        @DisplayName("FT on deleted ED25519 account")
        final Stream<DynamicTest> ftOnDeletedED25519Account() {
            final var ed25519 = "ED25519";
            final var deletedAccount = "deletedAccount";
            return hapiTest(
                    newKeyNamed(ed25519).shape(SigControl.ED25519_ON),
                    cryptoCreate(deletedAccount).key(ed25519),
                    cryptoDelete(deletedAccount),
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, deletedAccount))
                            .signedBy(OWNER)
                            .payingWith(OWNER)
                            .hasKnownStatus(ACCOUNT_DELETED));
        }

        @HapiTest
        @DisplayName("NFT on deleted ED25519 account")
        final Stream<DynamicTest> nftOnDeletedED25519Account() {
            final var ed25519 = "ED25519";
            final var deletedAccount = "deletedAccount";
            return hapiTest(
                    newKeyNamed(ed25519).shape(SigControl.SECP256K1_ON),
                    cryptoCreate(deletedAccount).key(ed25519),
                    cryptoDelete(deletedAccount),
                    tokenAirdrop(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 7)
                                    .between(OWNER, deletedAccount))
                            .signedBy(OWNER)
                            .payingWith(OWNER)
                            .hasKnownStatus(ACCOUNT_DELETED));
        }
    }

    @Nested
    @DisplayName("delete account with relation ")
    class DeleteAccount {
        @HapiTest
        @DisplayName("to fungible token pending airdrop")
        final Stream<DynamicTest> canNotDeleteAccountRelatedToAirdrop() {
            var receiver = "receiverToDelete";
            return defaultHapiSpec("should fail - ACCOUNT_HAS_PENDING_AIRDROPS")
                    .given()
                    .when()
                    .then(
                            cryptoCreate(receiver).maxAutomaticTokenAssociations(0),
                            tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver))
                                    .payingWith(OWNER),
                            cryptoDelete(OWNER).hasKnownStatus(ACCOUNT_HAS_PENDING_AIRDROPS));
        }

        @HapiTest
        @DisplayName("to non-fungible token pending airdrop")
        final Stream<DynamicTest> canNotDeleteAccountRelatedToNFTAirdrop() {
            return defaultHapiSpec("should fail - ACCOUNT_HAS_PENDING_AIRDROPS")
                    .given()
                    .when()
                    .then(
                            tokenAirdrop(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 6L)
                                            .between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                    .payingWith(OWNER),
                            cryptoDelete(OWNER).hasKnownStatus(ACCOUNT_HAS_PENDING_AIRDROPS));
        }
    }

    private TokenMovement defaultMovementOfToken(String token) {
        return moving(10, token).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS);
    }

    private TokenMovement moveFungibleTokensTo(String receiver) {
        return moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver);
    }

    /**
     * Create Fungible token and set up all scenario receivers
     * - receiver with unlimited auto associations
     * - associated receiver
     * - receiver with 0 auto associations
     * - receiver with free auto associations
     * - receiver with positive number associations, but without fee ones
     *
     * @return array of operations
     */
    private static SpecOperation[] setUpTokensAndAllReceivers() {
        var nftSupplyKey = "nftSupplyKey";
        final var t = new ArrayList<SpecOperation>(List.of(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                // base tokens
                tokenCreate(FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(1000L),
                tokenCreate("dummy").treasury(OWNER).tokenType(FUNGIBLE_COMMON).initialSupply(100L),
                newKeyNamed(nftSupplyKey),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .name(NON_FUNGIBLE_TOKEN)
                        .supplyKey(nftSupplyKey),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"),
                                ByteString.copyFromUtf8("c"),
                                ByteString.copyFromUtf8("d"),
                                ByteString.copyFromUtf8("e"),
                                ByteString.copyFromUtf8("f"),
                                ByteString.copyFromUtf8("g"),
                                ByteString.copyFromUtf8("h"))),

                // all kind of receivers
                cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(-1),
                cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                cryptoCreate(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(100),
                cryptoCreate(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                // fill the auto associate slot
                cryptoTransfer(moving(10, "dummy").between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)),
                cryptoCreate(ASSOCIATED_RECEIVER),
                tokenAssociate(ASSOCIATED_RECEIVER, FUNGIBLE_TOKEN),
                tokenAssociate(ASSOCIATED_RECEIVER, NON_FUNGIBLE_TOKEN)));

        return t.toArray(new SpecOperation[0]);
    }

    private static SpecOperation[] setUpTokensWithCustomFees(long tokenTotal, long hbarFee, long htsFee) {
        var nftWithCustomFeeSupplyKey = "nftWithCustomFeeSupplyKey";
        final var t = new ArrayList<SpecOperation>(List.of(
                // tokens with custom fees
                cryptoCreate(TREASURY_FOR_CUSTOM_FEE_TOKENS),
                cryptoCreate(HBAR_COLLECTOR).balance(0L),
                tokenCreate(FT_WITH_HBAR_FIXED_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(tokenTotal)
                        .withCustom(fixedHbarFee(hbarFee, HBAR_COLLECTOR)),
                cryptoCreate(HTS_COLLECTOR),
                cryptoCreate(HTS_COLLECTOR2),
                tokenCreate(DENOM_TOKEN)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .initialSupply(tokenTotal),
                tokenAssociate(HTS_COLLECTOR, DENOM_TOKEN),
                tokenCreate(FT_WITH_HTS_FIXED_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(tokenTotal)
                        .withCustom(fixedHtsFee(htsFee, DENOM_TOKEN, HTS_COLLECTOR)),
                tokenAssociate(HTS_COLLECTOR2, FT_WITH_HTS_FIXED_FEE),
                newKeyNamed(nftWithCustomFeeSupplyKey),
                tokenCreate(NFT_WITH_HTS_FIXED_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .supplyKey(nftWithCustomFeeSupplyKey)
                        .supplyType(TokenSupplyType.INFINITE)
                        .initialSupply(0)
                        .withCustom(fixedHtsFee(htsFee, FT_WITH_HTS_FIXED_FEE, HTS_COLLECTOR2)),
                mintToken(NFT_WITH_HTS_FIXED_FEE, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                tokenCreate(FT_WITH_FRACTIONAL_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(FUNGIBLE_COMMON)
                        .withCustom(fractionalFee(1, 10L, 1L, OptionalLong.empty(), TREASURY_FOR_CUSTOM_FEE_TOKENS))
                        .initialSupply(Long.MAX_VALUE),
                tokenCreate(NFT_WITH_ROYALTY_FEE)
                        .maxSupply(100L)
                        .initialSupply(0)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(nftWithCustomFeeSupplyKey)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .withCustom(
                                royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), HTS_COLLECTOR)),
                tokenAssociate(HTS_COLLECTOR, NFT_WITH_ROYALTY_FEE),
                mintToken(NFT_WITH_ROYALTY_FEE, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),

                // all collectors exempt setup
                cryptoCreate(NFT_ALL_COLLECTORS_EXEMPT_OWNER),
                cryptoCreate(NFT_ALL_COLLECTORS_EXEMPT_RECEIVER),
                cryptoCreate(NFT_ALL_COLLECTORS_EXEMPT_COLLECTOR),
                newKeyNamed(NFT_ALL_COLLECTORS_EXEMPT_KEY),
                tokenCreate(NFT_ALL_COLLECTORS_EXEMPT_TOKEN)
                        .maxSupply(100L)
                        .initialSupply(0)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(NFT_ALL_COLLECTORS_EXEMPT_KEY)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        // setting a custom fee with allCollectorsExempt=true(see HIP-573)
                        .withCustom(royaltyFeeWithFallback(
                                1,
                                2,
                                fixedHbarFeeInheritingRoyaltyCollector(1),
                                NFT_ALL_COLLECTORS_EXEMPT_COLLECTOR,
                                true))
                        // set the receiver as a custom fee collector
                        .withCustom(royaltyFeeWithFallback(
                                1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), NFT_ALL_COLLECTORS_EXEMPT_RECEIVER)),
                tokenAssociate(NFT_ALL_COLLECTORS_EXEMPT_OWNER, NFT_ALL_COLLECTORS_EXEMPT_TOKEN),
                cryptoCreate(FT_ALL_COLLECTORS_EXEMPT_OWNER),
                cryptoCreate(FT_ALL_COLLECTORS_EXEMPT_RECEIVER),
                cryptoCreate(FT_ALL_COLLECTORS_EXEMPT_COLLECTOR).balance(0L),
                tokenCreate(FT_ALL_COLLECTORS_EXEMPT_TOKEN)
                        .initialSupply(100L)
                        .tokenType(FUNGIBLE_COMMON)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        // setting a custom fee with allCollectorsExempt=true(see HIP-573)
                        .withCustom(fixedHbarFee(100, FT_ALL_COLLECTORS_EXEMPT_COLLECTOR, true))
                        // set the receiver as a custom fee collector
                        .withCustom(fixedHbarFee(100, FT_ALL_COLLECTORS_EXEMPT_RECEIVER)),
                tokenAssociate(FT_ALL_COLLECTORS_EXEMPT_OWNER, FT_ALL_COLLECTORS_EXEMPT_TOKEN)));

        // mint 99 NFTs
        for (int i = 0; i < 99; i++) {
            t.add(mintToken(NFT_WITH_ROYALTY_FEE, List.of(ByteStringUtils.wrapUnsafely(("meta" + i).getBytes()))));
        }

        return t.toArray(new SpecOperation[0]);
    }

    /**
     * Create and mint NFT and set up all scenario receivers
     * - receiver with unlimited auto associations
     * - associated receiver
     * - receiver with 0 auto associations
     * - receiver with free auto associations
     * - receiver with positive number associations, but without fee ones
     *
     * @return array of operations
     */
    private HapiTokenCreate createTokenWithName(String tokenName) {
        return tokenCreate(tokenName).tokenType(TokenType.FUNGIBLE_COMMON).treasury(OWNER);
    }
}
