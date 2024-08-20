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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUniqueWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithDecimals;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_HAS_PENDING_AIRDROPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
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
                            getAccountBalance(HTS_COLLECTOR2).hasTokenBalance(FT_WITH_HTS_FIXED_FEE, htsFee));
        }

        @HapiTest
        @DisplayName("fungible token with fractional fee")
        final Stream<DynamicTest> fungibleTokenWithFractionalFeesPaidByReceiverFails() {
            return defaultHapiSpec("should be successful transfer")
                    .given(
                            tokenAssociate(OWNER, FT_WITH_FRACTIONAL_FEE),
                            cryptoTransfer(
                                    moving(100, FT_WITH_FRACTIONAL_FEE).between(TREASURY_FOR_CUSTOM_FEE_TOKENS, OWNER)))
                    .when(tokenAirdrop(moving(25, FT_WITH_FRACTIONAL_FEE)
                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                            .payingWith(OWNER)
                            .via("fractionalTxn"))
                    .then(
                            validateChargedUsd("fractionalTxn", 0.1, 10),
                            getTxnRecord("fractionalTxn")
                                    .hasPriority(recordWith()
                                            .tokenTransfers(includingFungibleMovement(moving(25, FT_WITH_FRACTIONAL_FEE)
                                                    .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)))),
                            getAccountBalance(OWNER).hasTokenBalance(FT_WITH_FRACTIONAL_FEE, 75),
                            getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                    .hasTokenBalance(FT_WITH_FRACTIONAL_FEE, 25));
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
        @DisplayName("fungible token with allowance")
        final Stream<DynamicTest> airdropFtWithAllowance() {
            var spender = "spender";
            return defaultHapiSpec("should fail - INVALID_TRANSACTION")
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
            return defaultHapiSpec("should fail - INVALID_TRANSACTION")
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
}
