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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FEE_COLLECTOR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
@DisplayName("Token airdrop")
public class TokenAirdropSuite {

    private static final String AIRDROPS_ENABLED = "tokens.airdrops.enabled";
    private static final String UNLIMITED_AUTO_ASSOCIATIONS_ENABLED = "entities.unlimitedAutoAssociationsEnabled";
    private static final String OWNER = "owner";
    private static final String SPENDER = "spender";
    private static final String RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS = "receiverWithUnlimitedAutoAssociations";
    private static final String RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS = "receiverWithFreeAutoAssociations";
    private static final String RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS = "receiverWithoutFreeAutoAssociations";
    private static final String RECEIVER_WITH_0_AUTO_ASSOCIATIONS = "receiverWith0AutoAssociations";
    private static final String ASSOCIATED_RECEIVER = "associatedReceiver";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String DUMMY_FUNGIBLE_TOKEN = "dummyFungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String NFT_SUPPLY_KEY = "nftSupplyKey";
    private static final String ED25519_KEY = "ed25519key";
    private static final String SECP_256K1_KEY = "secp256K1";

    @BeforeAll
    static void beforeAll(@NonNull final SpecManager specManager) throws Throwable {
        specManager.setup(overriding(AIRDROPS_ENABLED, "true"));
        specManager.setup(overriding(UNLIMITED_AUTO_ASSOCIATIONS_ENABLED, "true"));
    }

    @AfterAll
    static void afterAll(@NonNull final SpecManager specManager) throws Throwable {
        specManager.teardown(overriding(AIRDROPS_ENABLED, "false"));
        specManager.teardown(overriding(UNLIMITED_AUTO_ASSOCIATIONS_ENABLED, "false"));
    }

    @HapiTest
    @DisplayName("fungible tokens to existing accounts")
    final Stream<DynamicTest> tokenAirdropToExistingAccounts() {

        return defaultHapiSpec("should transfer if any free auto associations slots")
                .given(
                        // create fungible token and all receivers accounts
                        setUpFungibleTokenAndAllReceivers())
                .when(
                        // send token airdrop
                        tokenAirdrop(
                                        // associated receiver
                                        moveFungibleTokensTo(ASSOCIATED_RECEIVER),
                                        // free auto association slots
                                        moveFungibleTokensTo(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                        moveFungibleTokensTo(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS),
                                        // without free auto association slots
                                        moveFungibleTokensTo(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                        moveFungibleTokensTo(RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .via("fungible airdrop"))
                .then(
                        getTxnRecord("fungible airdrop")
                                // assert pending airdrops
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingFungiblePendingAirdrop(
                                                moveFungibleTokensTo(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                                moveFungibleTokensTo(RECEIVER_WITH_0_AUTO_ASSOCIATIONS))))
                                // assert transfers
                                .hasPriority(recordWith()
                                        .tokenTransfers(includingFungibleMovement(moving(30, FUNGIBLE_TOKEN)
                                                .distributing(
                                                        OWNER,
                                                        RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS,
                                                        RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS,
                                                        ASSOCIATED_RECEIVER))))
                                .logged(),

                        // assert account balances
                        getAccountBalance(OWNER).hasTokenBalance(FUNGIBLE_TOKEN, 70),
                        // transferred tokens
                        getAccountBalance(ASSOCIATED_RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        // pending airdrops
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(FUNGIBLE_TOKEN, 0));
    }

    @HapiTest
    @DisplayName("NFT to existing accounts")
    final Stream<DynamicTest> nftAirdropToExistingAccountsWorks() {
        return defaultHapiSpec("should transfer if any free auto associations slots")
                .given(
                        // create NFT and all receivers accounts
                        setUpNFTAndAllReceivers())
                .when(tokenAirdrop(
                                // without free auto association slots
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                movingUnique(NON_FUNGIBLE_TOKEN, 2L)
                                        .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                // free auto association slots
                                movingUnique(NON_FUNGIBLE_TOKEN, 3L)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                movingUnique(NON_FUNGIBLE_TOKEN, 4L)
                                        .between(OWNER, RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS),
                                // associated receiver
                                movingUnique(NON_FUNGIBLE_TOKEN, 5L).between(OWNER, ASSOCIATED_RECEIVER))
                        .payingWith(OWNER)
                        .via("non fungible airdrop"))
                .then(
                        getTxnRecord("non fungible airdrop")
                                // assert the pending list
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingNftPendingAirdrop(
                                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                        .between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                                movingUnique(NON_FUNGIBLE_TOKEN, 2L)
                                                        .between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))))
                                // assert transfer list
                                .hasPriority(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 3L)
                                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))))
                                .hasPriority(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 4L)
                                                        .between(OWNER, RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS))))
                                .hasPriority(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 5L)
                                                        .between(OWNER, ASSOCIATED_RECEIVER))))
                                .logged(),

                        // assert account balances
                        getAccountBalance(OWNER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 2),
                        getAccountBalance(ASSOCIATED_RECEIVER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        getAccountBalance(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                        getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(NON_FUNGIBLE_TOKEN, 0));
    }

    @HapiTest
    @DisplayName("in pending state, should prepay hbar custom fee")
    final Stream<DynamicTest> airdropFungibleWithFixedHbarCustomFee() {
        final long hbarFee = 1_000L;
        final long tokenTotal = 1_000L;
        final String hbarCollector = "hbarCollector";
        return defaultHapiSpec("with fixed Hbar CustomFee")
                .given(
                        cryptoCreate(hbarCollector).balance(0L),
                        cryptoCreate(OWNER).balance(ONE_MILLION_HBARS),
                        cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHbarFee(hbarFee, hbarCollector)),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(tokenAirdrop(moving(1, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .fee(ONE_HUNDRED_HBARS)
                        .via("transferTx")
                        .payingWith(OWNER))
                .then(
                        // assert balances
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        getAccountBalance(hbarCollector).hasTinyBars(hbarFee),
                        withOpContext((spec, log) -> {
                            final var record = getTxnRecord("transferTx");
                            allRunFor(spec, record);
                            final var txFee = record.getResponseRecord().getTransactionFee();
                            // the token should not be transferred but the custom fee should be charged
                            final var ownerBalance = getAccountBalance(OWNER)
                                    .hasTinyBars(ONE_MILLION_HBARS - (txFee + hbarFee))
                                    .hasTokenBalance(FUNGIBLE_TOKEN, 1000);
                            allRunFor(spec, ownerBalance);
                        }));
    }

    @HapiTest
    @DisplayName("in pending state, should prepay hts custom fee")
    final Stream<DynamicTest> transferNonFungibleWithFixedHtsCustomFees2Layers() {
        final String feeDenom = "denom";
        final String htsCollector = "feeCollector";
        final String htsCollector2 = "feeCollector2";
        final long htsFee = 100L;
        final long tokenTotal = 1_000L;

        return defaultHapiSpec("NFT with 2 layers fixed Hts CustomFees")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(htsCollector),
                        cryptoCreate(htsCollector2),
                        cryptoCreate(OWNER).balance(ONE_MILLION_HBARS),
                        cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(feeDenom).treasury(OWNER).initialSupply(tokenTotal),
                        tokenAssociate(htsCollector, feeDenom),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(tokenTotal)
                                .withCustom(fixedHtsFee(htsFee, feeDenom, htsCollector)),
                        tokenAssociate(htsCollector2, FUNGIBLE_TOKEN),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .withCustom(fixedHtsFee(htsFee, FUNGIBLE_TOKEN, htsCollector2)),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                        tokenAssociate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        cryptoTransfer(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER),
                                moving(tokenTotal, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .when(tokenAirdrop(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .fee(ONE_HUNDRED_HBARS)
                        .payingWith(OWNER))
                .then(
                        getAccountBalance(OWNER)
                                .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1)
                                .hasTokenBalance(FUNGIBLE_TOKEN, tokenTotal - htsFee)
                                .hasTokenBalance(feeDenom, tokenTotal - htsFee),
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                        getAccountBalance(htsCollector).hasTokenBalance(feeDenom, htsFee),
                        getAccountBalance(htsCollector2).hasTokenBalance(FUNGIBLE_TOKEN, htsFee));
    }

    @HapiTest
    @DisplayName("to non existing accounts")
    final Stream<DynamicTest> airdropToNonExistingAccounts() {
        // calculate evmAddress;
        final byte[] publicKey =
                CommonUtils.unhex("02641dc27aa851ddc5a238dc569718f82b4e5eb3b61030942432fe7ac9088459c5");
        final ByteString evmAddress = ByteStringUtils.wrapUnsafely(recoverAddressFromPubKey(publicKey));

        return defaultHapiSpec("should auto-create and transfer the tokens")
                .given(
                        newKeyNamed(ED25519_KEY),
                        newKeyNamed(SECP_256K1_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(30L))
                .when(tokenAirdrop(
                                moving(10, FUNGIBLE_TOKEN).between(OWNER, ED25519_KEY),
                                moving(10, FUNGIBLE_TOKEN).between(OWNER, SECP_256K1_KEY),
                                moving(10, FUNGIBLE_TOKEN).between(OWNER, evmAddress))
                        .payingWith(OWNER))
                .then(
                        // assert balances
                        getAccountBalance(OWNER).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        getAutoCreatedAccountBalance(ED25519_KEY).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAutoCreatedAccountBalance(SECP_256K1_KEY).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAliasedAccountBalance(evmAddress).hasTokenBalance(FUNGIBLE_TOKEN, 10));
    }

    @HapiTest
    @DisplayName("in pending state should be not affected by")
    final Stream<DynamicTest> consequentAirdrops() {
        // Verify that when sending 2 consequent airdrops to a recipient,
        // which associated themselves to the token after the first airdrop,
        // the second airdrop is directly transferred to the recipient and the first airdrop remains in pending state
        return defaultHapiSpec("following airdrops")
                .given(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100L),
                        cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0))
                .when(
                        // send first airdrop
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .via("first"),
                        getTxnRecord("first")
                                // assert pending airdrops
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingFungiblePendingAirdrop(moving(10, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS)))),
                        tokenAssociate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                .then(
                        // this time tokens should be transferred
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .via("second"),
                        // assert OWNER and receiver accounts to ensure first airdrop is still in pending state
                        getTxnRecord("second")
                                // assert transfers
                                .hasPriority(recordWith()
                                        .tokenTransfers(includingFungibleMovement(moving(10, FUNGIBLE_TOKEN)
                                                .between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))))
                                .logged(),
                        // since there is noway to check pending airdrop state, we just assert the account balances
                        getAccountBalance(OWNER).hasTokenBalance(FUNGIBLE_TOKEN, 90),
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 10));
    }

    @HapiTest
    @DisplayName("containing invalid token id")
    final Stream<DynamicTest> airdropInvalidTokenIdFails() {
        return defaultHapiSpec("should fail - INVALID_TOKEN_ID")
                .given(
                        cryptoCreate(OWNER),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var bogusTokenId = TokenID.newBuilder().setTokenNum(9999L);
                    spec.registry().saveTokenId("nonexistent", bogusTokenId.build());
                    allRunFor(
                            spec,
                            tokenAirdrop(movingWithDecimals(1L, "nonexistent", 2)
                                            .betweenWithDecimals(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                    .hasKnownStatus(INVALID_TOKEN_ID));
                }));
    }

    @HapiTest
    @DisplayName("containing negative NFT serial number")
    final Stream<DynamicTest> airdropNFTNegativeSerial() {
        return defaultHapiSpec("should fail - INVALID_TOKEN_NFT_SERIAL_NUMBER")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .initialSupply(0),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))))
                .when()
                .then(tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, -5)
                                .between(TOKEN_TREASURY, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .payingWith(TOKEN_TREASURY)
                        .hasPrecheck(INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    /**
     *  When we set the token value as negative value, the transfer list that we aggregate just switch
     *  the roles of sender and receiver, so the sender checks will fail.
     *  There are 3 different outcomes if we try to airdrop token with negative value:
     *  1. When receiver is not associated with the token - the node will return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT
     *  2. When receiver don't have enough balance - the node will return INVALID_ACCOUNT_AMOUNTS
     *  3. When receiver is associated and have enough balance - the node will expect signature - INVALID_SIGNATURE
     */
    @HapiTest
    @DisplayName("containing negative amount of fungible tokens")
    final Stream<DynamicTest> airdropNegativeAmountFails() {
        return defaultHapiSpec("should fail")
                .given(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(-1),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(OWNER))
                // todo add the other 3 cases
                // cryptoTransfer(moving(15, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)))
                .when()
                .then(tokenAirdrop(
                                moving(-15, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .hasKnownStatusFrom(
                                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, INVALID_ACCOUNT_AMOUNTS, INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("spender does not have enough allowance")
    final Stream<DynamicTest> spenderNotEnoughAllowanceFails() {
        return defaultHapiSpec("should fail - AMOUNT_EXCEEDS_ALLOWANCE")
                .given(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN))
                .when(
                        cryptoTransfer(moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 10)
                                .fee(ONE_HUNDRED_HBARS))
                .then(tokenAirdrop(movingWithAllowance(50, FUNGIBLE_TOKEN)
                                .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .payingWith(SPENDER)
                        .signedBy(SPENDER, OWNER)
                        .hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE));
    }

    @HapiTest
    @DisplayName("sender is not associated")
    final Stream<DynamicTest> senderWithMissingAssociationFails() {
        return defaultHapiSpec("should fail - TOKEN_NOT_ASSOCIATED_TO_ACCOUNT")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .initialSupply(0),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))))
                .when()
                .then(
                        tokenAirdrop(moving(50, FUNGIBLE_TOKEN)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT),
                        tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
    }

    @HapiTest
    @DisplayName("with missing sender's signature")
    final Stream<DynamicTest> missingSenderSigFails() {
        return defaultHapiSpec("should fail - INVALID_SIGNATURE")
                .given(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(OWNER))
                .when()
                .then(tokenAirdrop(moving(50, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("with missing owner's signature")
    final Stream<DynamicTest> missingPayerSigFails() {
        return defaultHapiSpec("should fail - INVALID_SIGNATURE")
                .given(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN))
                .when(
                        cryptoTransfer(moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100))
                .then(tokenAirdrop(movingWithAllowance(50, FUNGIBLE_TOKEN)
                                .between(SPENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        // Should be signed by spender as well
                        .signedBy(OWNER)
                        .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("owner does not have enough balance")
    final Stream<DynamicTest> ownerNotEnoughBalanceFails() {
        return defaultHapiSpec("should fail - INVALID_ACCOUNT_AMOUNTS")
                .given(
                        cryptoCreate(OWNER),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN))
                .when(cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .then(tokenAirdrop(moving(99, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .payingWith(OWNER)
                        .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS));
    }

    @HapiTest
    @DisplayName("containing duplicate entries in the transfer list")
    final Stream<DynamicTest> duplicateEntryInTokenTransferFails() {
        return defaultHapiSpec("should fail - ")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b"))),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)))
                .when()
                .then(tokenAirdrop(
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .payingWith(OWNER)
                        // todo why this status
                        .hasPrecheck(INVALID_ACCOUNT_AMOUNTS));
    }

    @HapiTest
    @DisplayName("of token with custom fee, that should be paid by receivers")
    final Stream<DynamicTest> tokenWithCustomFeesPaidByReceiverFails() {
        return defaultHapiSpec("should fail - INVALID_TRANSACTION")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(OWNER),
                        cryptoCreate(FEE_COLLECTOR).balance(0L),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .tokenType(FUNGIBLE_COMMON)
                                .withCustom(fractionalFee(1, 10L, 1L, OptionalLong.empty(), TOKEN_TREASURY))
                                .initialSupply(Long.MAX_VALUE),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .maxSupply(10L)
                                .initialSupply(0)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(
                                        royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), OWNER)))
                .when(
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        cryptoTransfer(moving(100, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .then(
                        tokenAirdrop(moving(25, FUNGIBLE_TOKEN)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .hasKnownStatus(INVALID_TRANSACTION),
                        tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .hasKnownStatus(INVALID_TRANSACTION));
    }

    @HapiTest
    @DisplayName("has transfer list size above the max")
    final Stream<DynamicTest> aboveMaxTransfersFails() {
        return defaultHapiSpec("should fail - INVALID_TRANSACTION_BODY")
                .given(
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
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
    private SpecOperation[] setUpFungibleTokenAndAllReceivers() {
        final var t = new ArrayList<SpecOperation>(List.of(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                tokenCreate(FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(100L),
                tokenCreate(DUMMY_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(100L),
                cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(-1),
                cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                cryptoCreate(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                cryptoCreate(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                // fill the auto associate slot
                cryptoTransfer(
                        moving(10, DUMMY_FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)),
                cryptoCreate(ASSOCIATED_RECEIVER),
                tokenAssociate(ASSOCIATED_RECEIVER, FUNGIBLE_TOKEN)));

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
    private SpecOperation[] setUpNFTAndAllReceivers() {
        final var t = new ArrayList<SpecOperation>(List.of(
                newKeyNamed(NFT_SUPPLY_KEY),
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                tokenCreate(DUMMY_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(100L),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .name(NON_FUNGIBLE_TOKEN)
                        .supplyKey(NFT_SUPPLY_KEY),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        List.of(
                                ByteString.copyFromUtf8("a"),
                                ByteString.copyFromUtf8("b"),
                                ByteString.copyFromUtf8("c"),
                                ByteString.copyFromUtf8("d"),
                                ByteString.copyFromUtf8("e"))),
                cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(-1),
                cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                cryptoCreate(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                cryptoCreate(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                // fill the auto associate slot
                cryptoTransfer(
                        moving(10, DUMMY_FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)),
                cryptoCreate(ASSOCIATED_RECEIVER),
                tokenAssociate(ASSOCIATED_RECEIVER, NON_FUNGIBLE_TOKEN)));

        return t.toArray(new SpecOperation[0]);
    }

    private HapiTokenCreate createTokenWithName(String tokenName) {
        return tokenCreate(tokenName).tokenType(TokenType.FUNGIBLE_COMMON).treasury(OWNER);
    }
}
