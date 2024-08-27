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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenClaimAirdrop.pendingAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenReject.rejectingToken;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithDecimals;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_HAS_PENDING_AIRDROPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
@DisplayName("Token airdrop")
public class TokenAirdropTest extends TokenAirdropBase {

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.overrideInClass(Map.of(
                "tokens.airdrops.enabled", "false",
                "tokens.airdrops.claim.enabled", "false",
                "entities.unlimitedAutoAssociationsEnabled", "false"));
        // create some entities with disabled airdrops
        lifecycle.doAdhoc(setUpEntitiesPreHIP904());
        // enable airdrops
        lifecycle.doAdhoc(
                overriding("tokens.airdrops.enabled", "true"),
                overriding("tokens.airdrops.claim.enabled", "true"),
                overriding("entities.unlimitedAutoAssociationsEnabled", "true"));
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

            @HapiTest
            @DisplayName("charge association fee for FT correctly")
            final Stream<DynamicTest> chargeAssociationFeeForFT() {
                var receiver = "receiver";
                return hapiTest(
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
                return hapiTest(
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

            // AIRDROP_17
            @HapiTest
            final Stream<DynamicTest> transferMultipleFtAndNftToEOAWithNoFreeAutoAssociationsAccountResultsInPending() {
                final String NFT_FOR_MULTIPLE_PENDING_TRANSFER = "nftForMultiplePendingTransfer";
                final String FT_FOR_MULTIPLE_PENDING_TRANSFER = "ftForMultiplePendingTransfer";
                var nftSupplyKeyForMultipleTransfers = "nftSupplyKeyForMultipleTransfer";
                return defaultHapiSpec("Send multiple FT and NFT from EOA to Account without free Auto-Associations")
                        .given(
                                tokenCreate(FT_FOR_MULTIPLE_PENDING_TRANSFER)
                                        .treasury(OWNER)
                                        .tokenType(FUNGIBLE_COMMON)
                                        .initialSupply(1000L),
                                newKeyNamed(nftSupplyKeyForMultipleTransfers),
                                tokenCreate(NFT_FOR_MULTIPLE_PENDING_TRANSFER)
                                        .treasury(OWNER)
                                        .tokenType(NON_FUNGIBLE_UNIQUE)
                                        .initialSupply(0L)
                                        .name(NFT_FOR_MULTIPLE_PENDING_TRANSFER)
                                        .supplyKey(nftSupplyKeyForMultipleTransfers),
                                mintToken(
                                        NFT_FOR_MULTIPLE_PENDING_TRANSFER,
                                        IntStream.range(0, 10)
                                                .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                                .toList())
                        )
                        .when(
                                tokenAirdrop(
                                        moving(10, FT_FOR_MULTIPLE_PENDING_TRANSFER)
                                                .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                        movingUnique(NFT_FOR_MULTIPLE_PENDING_TRANSFER, 1L)
                                                .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                )
                                        .payingWith(OWNER)
                                        .signedBy(OWNER)
                                        .via("first airdrop"),
                                tokenAirdrop(
                                        moving(10, FT_FOR_MULTIPLE_PENDING_TRANSFER)
                                                .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                        movingUnique(NFT_FOR_MULTIPLE_PENDING_TRANSFER, 2L)
                                                .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))
                                        .payingWith(OWNER)
                                        .signedBy(OWNER)
                                        .via("second airdrop")
                        )
                        .then(
                                getTxnRecord("first airdrop")
                                        .hasPriority(recordWith()
                                                .pendingAirdrops(includingFungiblePendingAirdrop(
                                                        moving(10, FT_FOR_MULTIPLE_PENDING_TRANSFER).between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                                ))
                                                .pendingAirdrops(includingNftPendingAirdrop(
                                                        movingUnique(NFT_FOR_MULTIPLE_PENDING_TRANSFER, 1L)
                                                                .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))
                                                )
                                        ),
                                getTxnRecord("second airdrop")
                                        .hasPriority(recordWith()
                                                .pendingAirdrops(includingFungiblePendingAirdrop(
                                                        moving(20, FT_FOR_MULTIPLE_PENDING_TRANSFER).between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                                ))
                                                .pendingAirdrops(includingNftPendingAirdrop(
                                                        movingUnique(NFT_FOR_MULTIPLE_PENDING_TRANSFER, 2L)
                                                                .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))
                                                )
                                        ),
                                // assert account balances
                                getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(FT_FOR_MULTIPLE_PENDING_TRANSFER, 0)
                                        .hasTokenBalance(NFT_FOR_MULTIPLE_PENDING_TRANSFER, 0),
                                getAccountBalance(OWNER)
                                        .hasTokenBalance(FT_FOR_MULTIPLE_PENDING_TRANSFER, 1000)
                                        .hasTokenBalance(NFT_FOR_MULTIPLE_PENDING_TRANSFER, 10L),
                                validateChargedUsd("first airdrop", 0.2, 10),
                                validateChargedUsd("second airdrop", 0.15, 10));
            }

            // AIRDROP_21
            @HapiTest
            final Stream<DynamicTest> transferOneFTTwiceFromEOAWithOneFTInBalanceToAccountWithNoFreeAutoAssociationsResultsInPendingAggregated() {
                var sender = "sender";
                return defaultHapiSpec("Send one FT from EOA with only One FT in balance twice to Account without free Auto-Associations")
                        .given(
                                cryptoCreate(sender).maxAutomaticTokenAssociations(-1),
                                tokenAirdrop(
                                        moving(1, FUNGIBLE_TOKEN).between(OWNER, sender))
                                        .payingWith(OWNER)
                                        .via("credit sender"),
                                getTxnRecord("credit sender")
                                        .hasPriority(recordWith()
                                                .tokenTransfers(includingFungibleMovement(moving(1, FUNGIBLE_TOKEN)
                                                        .distributing(
                                                                OWNER,
                                                                sender)))))
                        .when(
                                tokenAirdrop(
                                        moving(1, FUNGIBLE_TOKEN)
                                                .between(sender, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))
                                        .payingWith(sender)
                                        .signedBy(sender)
                                        .via("first airdrop"),
                                tokenAirdrop(
                                        moving(1, FUNGIBLE_TOKEN)
                                                .between(sender, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))
                                        .payingWith(sender)
                                        .signedBy(sender)
                                        .via("second airdrop"))
                        .then(
                                getTxnRecord("first airdrop")
                                        .hasPriority(recordWith()
                                                .pendingAirdrops(includingFungiblePendingAirdrop(
                                                        moving(1, FUNGIBLE_TOKEN).between(sender, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                                ))),
                                getTxnRecord("second airdrop")
                                        .hasPriority(recordWith()
                                                .pendingAirdrops(includingFungiblePendingAirdrop(
                                                        moving(2, FUNGIBLE_TOKEN).between(sender, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                                ))),
                                // assert account balances
                                getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                        .hasTokenBalance(FUNGIBLE_TOKEN, 0),
                                getAccountBalance(sender)
                                        .hasTokenBalance(FUNGIBLE_TOKEN, 1),
                                validateChargedUsd("first airdrop", 0.1, 10),
                                validateChargedUsd("second airdrop", 0.05, 10));
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

        @HapiTest
        @DisplayName("airdrop to contract with admin key")
        final Stream<DynamicTest> airdropToContractWithAdminKey() {
            final var testContract = "ToyMaker";
            final var key = "key";
            return hapiTest(
                    newKeyNamed(key),
                    uploadInitCode(testContract),
                    contractCreate(testContract).adminKey(key),
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, testContract))
                            .signedBy(OWNER)
                            .payingWith(OWNER));
        }

        @HapiTest
        @DisplayName("after reject should keep the association")
        final Stream<DynamicTest> afterRejectShouldKeepTheAssociation() {
            final var receiver = "receiver";
            return hapiTest(
                    cryptoCreate(receiver).maxAutomaticTokenAssociations(0),

                    // Token airdrop and verify that the receiver balance is 0
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver))
                            .signedBy(OWNER)
                            .payingWith(OWNER),
                    getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 0),

                    // Claim and verify that receiver balance is 10
                    tokenClaimAirdrop(pendingAirdrop(OWNER, receiver, FUNGIBLE_TOKEN))
                            .payingWith(receiver),
                    getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 10),

                    // Reject and verify that receiver balance is 0
                    tokenReject(rejectingToken(FUNGIBLE_TOKEN)).payingWith(receiver),
                    getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 0),

                    // Airdrop without claim and verify that the receiver balance is 10
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver))
                            .signedBy(OWNER)
                            .payingWith(OWNER),
                    getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 10));
        }

        @HapiTest
        @DisplayName("airdrop after claim should result in CryptoTransfer")
        final Stream<DynamicTest> airdropAfterClaimShouldResultInCryptoTransfer() {
            final var receiver = "receiver";
            return hapiTest(
                    cryptoCreate(receiver).maxAutomaticTokenAssociations(0),

                    // Token airdrop and verify that the receiver balance is 0
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver))
                            .signedBy(OWNER)
                            .payingWith(OWNER),
                    getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 0),

                    // Claim and verify that receiver balance is 10
                    tokenClaimAirdrop(pendingAirdrop(OWNER, receiver, FUNGIBLE_TOKEN))
                            .payingWith(receiver),
                    getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 10),

                    // Airdrop without claim and verify that the receiver balance is 20
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver))
                            .signedBy(OWNER)
                            .payingWith(OWNER),
                    getAccountBalance(receiver).hasTokenBalance(FUNGIBLE_TOKEN, 20));
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
            lifecycle.doAdhoc(setUpTokensWithCustomFees(tokenTotal, hbarFee, htsFee));
        }

        @HapiTest
        @DisplayName("fungible token with fixed Hbar fee")
        final Stream<DynamicTest> airdropFungibleWithFixedHbarCustomFee() {
            final var initialBalance = 100 * ONE_HUNDRED_HBARS;
            return defaultHapiSpec(" sender should prepay hbar custom fee")
                    .given(
                            cryptoCreate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES).balance(initialBalance),
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
                                        .hasTinyBars(initialBalance - (txFee + hbarFee))
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
                            cryptoCreate(OWNER_OF_TOKENS_WITH_CUSTOM_FEES).balance(100 * ONE_HUNDRED_HBARS),
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
                                    .hasTokenBalance(NFT_WITH_HTS_FIXED_FEE, 0),
                            getAccountBalance(HTS_COLLECTOR).hasTokenBalance(DENOM_TOKEN, htsFee),
                            getAccountBalance(HTS_COLLECTOR).hasTokenBalance(FT_WITH_HTS_FIXED_FEE, htsFee));
        }

        @HapiTest
        @DisplayName("FT with fractional fee and net of transfers true")
        final Stream<DynamicTest> ftWithFractionalFeeNetOfTransfersTre() {
            return defaultHapiSpec("should be successful transfer")
                    .given(
                            tokenAssociate(OWNER, FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS),
                            cryptoTransfer(moving(100, FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS)
                                    .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)))
                    .when(tokenAirdrop(moving(10, FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS)
                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .payingWith(OWNER)
                            .via("fractionalTxn"))
                    .then(
                            validateChargedUsd("fractionalTxn", 0.1, 10),
                            // sender should pay 1 token for fractional fee
                            getAccountBalance(OWNER).hasTokenBalance(FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS, 89),
                            getAccountBalance(HTS_COLLECTOR)
                                    .hasTokenBalance(FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS, 1),
                            getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                    .hasTokenBalance(FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS, 10));
        }

        @HapiTest
        @DisplayName("FT with fractional fee with netOfTransfers=false")
        final Stream<DynamicTest> ftWithFractionalFeeNetOfTransfersFalse() {
            return defaultHapiSpec("should be successful transfer")
                    .given(
                            tokenAssociate(OWNER, FT_WITH_FRACTIONAL_FEE),
                            cryptoTransfer(
                                    moving(100, FT_WITH_FRACTIONAL_FEE).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)))
                    .when(tokenAirdrop(moving(10, FT_WITH_FRACTIONAL_FEE)
                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .payingWith(OWNER)
                            .via("fractionalTxn"))
                    .then(
                            validateChargedUsd("fractionalTxn", 0.1, 10),
                            getAccountBalance(OWNER).hasTokenBalance(FT_WITH_FRACTIONAL_FEE, 90),
                            // the fee is charged from the transfer value
                            getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                    .hasTokenBalance(FT_WITH_FRACTIONAL_FEE, 9));
        }

        @HapiTest
        @DisplayName("FT with fractional fee with netOfTransfers=false, in pending state")
        final Stream<DynamicTest> ftWithFractionalFeeNetOfTransfersFalseInPendingState() {
            var sender = "sender";
            return defaultHapiSpec("the value should be reduced")
                    .given(
                            cryptoCreate(sender).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(-1),
                            tokenAssociate(sender, FT_WITH_FRACTIONAL_FEE),
                            cryptoTransfer(moving(100, FT_WITH_FRACTIONAL_FEE)
                                    .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, sender)))
                    .when(tokenAirdrop(moving(100, FT_WITH_FRACTIONAL_FEE)
                                    .between(sender, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                            .payingWith(sender)
                            .via("fractionalTxn"))
                    .then(
                            validateChargedUsd("fractionalTxn", 0.1, 10),
                            // the fee is charged from the transfer value,
                            // so we expect 90% of the value to be in the pending state
                            getTxnRecord("fractionalTxn")
                                    .hasPriority(recordWith()
                                            .pendingAirdrops(
                                                    includingFungiblePendingAirdrop(moving(90, FT_WITH_FRACTIONAL_FEE)
                                                            .between(sender, RECEIVER_WITH_0_AUTO_ASSOCIATIONS)))));
        }

        @HapiTest
        @DisplayName("FT with fractional fee with netOfTransfers=false and dissociated collector")
        final Stream<DynamicTest> ftWithFractionalFeeNetOfTransfersFalseNotAssociatedCollector() {
            var sender = "sender";
            return defaultHapiSpec("should have 2 pending airdrops and the value should be reduced")
                    .given(
                            cryptoCreate(sender).balance(ONE_HUNDRED_HBARS),
                            tokenAssociate(sender, FT_WITH_FRACTIONAL_FEE_2),
                            cryptoTransfer(moving(100, FT_WITH_FRACTIONAL_FEE_2)
                                    .between(TREASURY_FOR_CUSTOM_FEE_TOKENS, sender)))
                    .when(
                            tokenDissociate(HTS_COLLECTOR, FT_WITH_FRACTIONAL_FEE_2),
                            tokenAirdrop(moving(100, FT_WITH_FRACTIONAL_FEE_2)
                                            .between(sender, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                    .payingWith(sender)
                                    .via("fractionalTxn"))
                    .then(
                            validateChargedUsd("fractionalTxn", 0.2, 10),
                            getTxnRecord("fractionalTxn")
                                    .hasPriority(recordWith()
                                            .pendingAirdrops(includingFungiblePendingAirdrop(
                                                    moving(90, FT_WITH_FRACTIONAL_FEE_2)
                                                            .between(sender, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                                    moving(10, FT_WITH_FRACTIONAL_FEE_2)
                                                            .between(sender, HTS_COLLECTOR)))));
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
    }

    @Nested
    @DisplayName("to non existing account")
    class AirdropToNonExistingAccounts {
        @HapiTest
        @DisplayName("ED25519 key")
        final Stream<DynamicTest> airdropToNonExistingED25519Account() {
            var ed25519key = "ed25519key";
            return defaultHapiSpec("should auto-create and transfer the tokens")
                    .given(newKeyNamed(ed25519key).shape(SigControl.ED25519_ON))
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
                    .given(newKeyNamed(secp256K1).shape(SigControl.SECP256K1_ON))
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

        //AIRDROP_19
        @LeakyHapiTest(overrides = {"entities.unlimitedAutoAssociationsEnabled"})
        final Stream<DynamicTest> airdropNFTToNonExistingEvmAddressWithoutAutoAssociationsResultingInPendingAirdropToHollowAccount() {
            final var validAliasForAirdrop = "validAliasForAirdrop";
            return defaultHapiSpec("Send one NFT from EOA to EVM address without auto-associations resulting in the creation of Hollow account and pending airdrop")
                    .given()
                    .when(
                            tokenAirdrop(
                                    movingUnique(NON_FUNGIBLE_TOKEN, 7L)
                                            .between(OWNER, validAliasForAirdrop))
                                    .payingWith(OWNER)
                                    .signedBy(OWNER)
                                    .via("EVM address NFT airdrop"))
                    .then(
                            getTxnRecord("EVM address NFT airdrop")
                                    .hasPriority(recordWith()
                                            .pendingAirdrops(includingNftPendingAirdrop(
                                                    movingUnique(NON_FUNGIBLE_TOKEN, 7L)
                                                            .between(OWNER, validAliasForAirdrop)))),
                            // assert hollow account
                            getAliasedAccountInfo(validAliasForAirdrop)
                                    .isHollow()
                                    .hasAlreadyUsedAutomaticAssociations(0)
                                    .hasMaxAutomaticAssociations(0)
                                    .hasNoTokenRelationship(NON_FUNGIBLE_TOKEN),
                            // assert owner account balance
                            getAccountBalance(OWNER)
                                    .hasTokenBalance(NON_FUNGIBLE_TOKEN, 17L),
                            validateChargedUsd("EVM address NFT airdrop", 0.1, 10));
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
        @DisplayName("fungible token with allowance")
        final Stream<DynamicTest> airdropFtWithAllowance() {
            var spender = "spender";
            return defaultHapiSpec("should fail - NOT_SUPPORTED")
                    .given(cryptoCreate(spender).balance(ONE_HUNDRED_HBARS))
                    .when(cryptoApproveAllowance()
                            .payingWith(OWNER)
                            .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, spender, 100))
                    .then(tokenAirdrop(movingWithAllowance(50, FUNGIBLE_TOKEN)
                                    .between(spender, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .signedBy(OWNER, spender)
                            .hasPrecheck(NOT_SUPPORTED));
        }

        @HapiTest
        @DisplayName("NFT with allowance")
        final Stream<DynamicTest> airdropNftWithAllowance() {
            var spender = "spender";
            return defaultHapiSpec("should fail - NOT_SUPPORTED")
                    .given(cryptoCreate(spender).balance(ONE_HUNDRED_HBARS))
                    .when(cryptoApproveAllowance()
                            .payingWith(OWNER)
                            .addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, spender, true, List.of()))
                    .then(tokenAirdrop(movingUniqueWithAllowance(NON_FUNGIBLE_TOKEN, 1L)
                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .signedBy(OWNER, spender)
                            .hasPrecheck(NOT_SUPPORTED));
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
                            .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS));
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
        @DisplayName("airdrop to contract without admin key")
        final Stream<DynamicTest> airdropToContractWithoutAdminKey() {
            final var testContract = "ToyMaker";
            return hapiTest(
                    uploadInitCode(testContract),
                    contractCreate(testContract).omitAdminKey(),
                    tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, testContract))
                            .signedBy(OWNER)
                            .payingWith(OWNER)
                            .hasKnownStatus(NOT_SUPPORTED));
        }

        @HapiTest
        @DisplayName("self airdrop fails")
        final Stream<DynamicTest> selfAirdropFails() {
            return hapiTest(tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, OWNER))
                    .signedBy(OWNER)
                    .payingWith(OWNER)
                    .hasPrecheck(INVALID_TRANSACTION_BODY));
        }

        @HapiTest
        @DisplayName("airdrop to 0x0 address")
        final Stream<DynamicTest> airdropTo0x0Address() {
            final byte[] publicKey =
                    CommonUtils.unhex("0000000000000000000000000000000000000000000000000000000000000000");
            final ByteString evmAddress = ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(publicKey));

            return hapiTest(tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, evmAddress))
                    .payingWith(OWNER)
                    .hasKnownStatus(INVALID_ACCOUNT_ID));
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

    @Nested
    @DisplayName("to contracts")
    class ToContracts {
        // 1 EOA Airdrops a token to a Contract who is associated to the token
        @HapiTest
        @DisplayName("single token to associated contract should transfer")
        final Stream<DynamicTest> singleTokenToAssociatedContract() {
            var mutableContract = "PayReceivable";
            return hapiTest(flattened(
                    deployMutableContract(mutableContract, 0),
                    tokenAssociate(mutableContract, FUNGIBLE_TOKEN),
                    tokenAirdrop(moving(1, FUNGIBLE_TOKEN).between(OWNER, mutableContract))
                            .payingWith(OWNER),
                    getAccountBalance(mutableContract).hasTokenBalance(FUNGIBLE_TOKEN, 1)));
        }

        // 2 EOA airdrops multiple tokens to a contract that is associated to all of them
        @HapiTest
        @DisplayName("multiple tokens to associated contract should transfer")
        final Stream<DynamicTest> multipleTokensToAssociatedContract() {
            var mutableContract = "PayReceivable";
            return hapiTest(flattened(
                    deployMutableContract(mutableContract, 0),
                    tokenAssociate(mutableContract, FUNGIBLE_TOKEN),
                    tokenAssociate(mutableContract, NFT_FOR_CONTRACT_TESTS),
                    tokenAirdrop(
                                    moving(1, FUNGIBLE_TOKEN).between(OWNER, mutableContract),
                                    movingUnique(NFT_FOR_CONTRACT_TESTS, 1).between(OWNER, mutableContract))
                            .payingWith(OWNER),
                    getAccountBalance(mutableContract).hasTokenBalance(FUNGIBLE_TOKEN, 1),
                    getAccountBalance(mutableContract).hasTokenBalance(NFT_FOR_CONTRACT_TESTS, 1)));
        }

        // 3 Airdrop multiple tokens to a contract that is associated to SOME of them when the contract has free auto
        // association slots.
        // Case 1:
        // associated only to FT
        @HapiTest
        @DisplayName("multiple tokens, but only FT is associated to the contract")
        final Stream<DynamicTest> multipleTokensOnlyFTIsAssociated() {
            var mutableContract = "PayReceivable";
            return hapiTest(flattened(
                    deployMutableContract(mutableContract, 0),
                    tokenAssociate(mutableContract, FUNGIBLE_TOKEN),
                    tokenAirdrop(
                                    moving(1, FUNGIBLE_TOKEN).between(OWNER, mutableContract),
                                    movingUnique(NFT_FOR_CONTRACT_TESTS, 2).between(OWNER, mutableContract))
                            .payingWith(OWNER)
                            .via("airdropToContract"),
                    getTxnRecord("airdropToContract")
                            .hasPriority(recordWith()
                                    .pendingAirdrops(includingNftPendingAirdrop(movingUnique(NFT_FOR_CONTRACT_TESTS, 2)
                                            .between(OWNER, mutableContract)))),
                    getAccountBalance(mutableContract).hasTokenBalance(FUNGIBLE_TOKEN, 1)));
        }

        // 3 Airdrop multiple tokens to a contract that is associated to SOME of them when the contract has free auto
        // association slots.
        // Case 2:
        // associated only to NFT
        @HapiTest
        @DisplayName("multiple tokens, but only NFT is associated to the contract")
        final Stream<DynamicTest> multipleTokensOnlyNFTIsAssociated() {
            var mutableContract = "PayReceivable";
            return hapiTest(flattened(
                    deployMutableContract(mutableContract, 0),
                    tokenAssociate(mutableContract, NFT_FOR_CONTRACT_TESTS),
                    tokenAirdrop(
                                    moving(1, FUNGIBLE_TOKEN).between(OWNER, mutableContract),
                                    movingUnique(NFT_FOR_CONTRACT_TESTS, 3).between(OWNER, mutableContract))
                            .payingWith(OWNER)
                            .via("airdropToContract"),
                    getTxnRecord("airdropToContract")
                            .hasPriority(recordWith()
                                    .pendingAirdrops(includingFungiblePendingAirdrop(
                                            moving(1, FUNGIBLE_TOKEN).between(OWNER, mutableContract)))),
                    getAccountBalance(mutableContract).hasTokenBalance(NFT_FOR_CONTRACT_TESTS, 1)));
        }
    }
}
