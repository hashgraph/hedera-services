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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithAllowance;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingWithDecimals;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
public class TokenAirdropSuite {

    private static final String AIRDROPS_ENABLED = "tokens.airdrops.enabled";
    public static final String UNLIMITED_AUTO_ASSOCIATIONS_ENABLED = "entities.unlimitedAutoAssociationsEnabled";
    private static final String SENDER = "sender";
    private static final String OWNER = "owner";
    private static final String SPENDER = "spender";
    private static final String RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS = "receiverWithUnlimitedAutoAssociations";
    private static final String RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS = "receiverWithFreeAutoAssociations";
    private static final String RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS = "receiverWithoutFreeAutoAssociations";
    private static final String RECEIVER_WITH_0_AUTO_ASSOCIATIONS = "receiverWith0AutoAssociations";
    private static final String ASSOCIATED_RECEIVER = "associatedReceiver";
    public static final String NFT_TOKEN_MINT_TXN = "nftTokenMint";
    public static final String FUNGIBLE_TOKEN_MINT_TXN = "tokenMint";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String DUMMY_FUNGIBLE_TOKEN = "dummyFungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String NFT_SUPPLY_KEY = "nftSupplyKey";
    private static final String ED25519_KEY = "ed25519key";
    private static final String SECP_256K1_KEY = "secp256K1";
    private static final String ANOTHER_SECP_256K1_KEY = "anotherSecp256K1";

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
    final Stream<DynamicTest> tokenAirdropToExistingAccountsWorks() {
        var moveToReceiverWithUnlimitedAutoAssociations =
                moving(10, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS);
        var moveToReceiverWith0AutoAssociations =
                moving(10, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS);
        var moveToReceiverWith1FreeAutoAssociations =
                moving(10, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS);
        var moveToAssociatedReceiver = moving(10, FUNGIBLE_TOKEN).between(SENDER, ASSOCIATED_RECEIVER);
        var moveToReceiverWithoutFreeAutoAssociations =
                moving(10, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS);

        return defaultHapiSpec("tokenAirdropToExistingAccountsWorks")
                .given(
                        // create fungible token and receivers accounts
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100L),
                        tokenCreate(DUMMY_FUNGIBLE_TOKEN)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100L),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(-1),
                        cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                        cryptoCreate(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                        cryptoCreate(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                        // fill the auto associate slot
                        cryptoTransfer(moving(10, DUMMY_FUNGIBLE_TOKEN)
                                .between(SENDER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)),
                        cryptoCreate(ASSOCIATED_RECEIVER),
                        tokenAssociate(ASSOCIATED_RECEIVER, FUNGIBLE_TOKEN))
                .when(
                        // send token airdrop
                        tokenAirdrop(
                                        moveToReceiverWithUnlimitedAutoAssociations,
                                        moveToReceiverWith0AutoAssociations,
                                        moveToReceiverWith1FreeAutoAssociations,
                                        moveToReceiverWithoutFreeAutoAssociations,
                                        moveToAssociatedReceiver)
                                .payingWith(SENDER)
                                .via("fungible airdrop"))
                .then(
                        getTxnRecord("fungible airdrop")
                                .andAllChildRecords()
                                // assert pending airdrops
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingFungiblePendingAirdrop(
                                                moveToReceiverWith0AutoAssociations,
                                                moveToReceiverWithoutFreeAutoAssociations)))
                                // assert transfers
                                .hasChildRecords(recordWith()
                                        .tokenTransfers(includingFungibleMovement(moving(30, FUNGIBLE_TOKEN)
                                                .distributing(
                                                        SENDER,
                                                        RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS,
                                                        RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS,
                                                        ASSOCIATED_RECEIVER))))
                                .logged(),

                        // assert account balances
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 70),
                        getAccountBalance(ASSOCIATED_RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(FUNGIBLE_TOKEN, 0));
    }

    @HapiTest
    final Stream<DynamicTest> nftAirdropToExistingAccountsWorks() {
        return defaultHapiSpec("nftAirdropToExistingAccountsWorks")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(DUMMY_FUNGIBLE_TOKEN)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100L),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(SENDER)
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
                        cryptoTransfer(moving(10, DUMMY_FUNGIBLE_TOKEN)
                                .between(SENDER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)),
                        cryptoCreate(ASSOCIATED_RECEIVER),
                        tokenAssociate(ASSOCIATED_RECEIVER, NON_FUNGIBLE_TOKEN))
                .when(tokenAirdrop(
                                // add to pending state
                                movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                movingUnique(NON_FUNGIBLE_TOKEN, 2L)
                                        .between(SENDER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS),
                                // do the transfer
                                movingUnique(NON_FUNGIBLE_TOKEN, 3L)
                                        .between(SENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                                movingUnique(NON_FUNGIBLE_TOKEN, 4L)
                                        .between(SENDER, RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS),
                                movingUnique(NON_FUNGIBLE_TOKEN, 5L).between(SENDER, ASSOCIATED_RECEIVER))
                        .payingWith(SENDER)
                        .via("non fungible airdrop"))
                .then(
                        getTxnRecord("non fungible airdrop")
                                .andAllChildRecords()
                                // check if tokens are in the pending list
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingNftPendingAirdrop(
                                                movingUnique(NON_FUNGIBLE_TOKEN, 1L)
                                                        .between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                                movingUnique(NON_FUNGIBLE_TOKEN, 2L)
                                                        .between(SENDER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))))
                                .hasChildRecords(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 3L)
                                                        .between(SENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))))
                                .hasChildRecords(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 4L)
                                                        .between(SENDER, RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS))))
                                .hasChildRecords(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 5L)
                                                        .between(SENDER, ASSOCIATED_RECEIVER))))
                                .logged(),

                        // assert account balances
                        getAccountBalance(SENDER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 2),
                        getAccountBalance(ASSOCIATED_RECEIVER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        getAccountBalance(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        getAccountBalance(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
                        getAccountBalance(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)
                                .hasTokenBalance(NON_FUNGIBLE_TOKEN, 0));
    }

    @HapiTest
    final Stream<DynamicTest> airdropToNonExistingAccountsWorks() {
        final AtomicReference<ByteString> evmAddress = new AtomicReference<>();
        return defaultHapiSpec("nftAirdropToExistingAccountsWorks")
                .given(
                        newKeyNamed(ED25519_KEY),
                        newKeyNamed(SECP_256K1_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ANOTHER_SECP_256K1_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(30L))
                .when(
                        withOpContext((spec, opLog) -> {
                            final var ecdsaKey = spec.registry()
                                    .getKey(ANOTHER_SECP_256K1_KEY)
                                    .getECDSASecp256K1()
                                    .toByteArray();
                            final var evmAddressBytes = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                            evmAddress.set(evmAddressBytes);
                        }),
                        sourcing(() -> tokenAirdrop(
                                        moving(10, FUNGIBLE_TOKEN).between(SENDER, ED25519_KEY),
                                        moving(10, FUNGIBLE_TOKEN).between(SENDER, SECP_256K1_KEY),
                                        moving(10, FUNGIBLE_TOKEN).between(SENDER, evmAddress.get()))
                                .payingWith(SENDER)))
                .then(withOpContext((spec, log) -> {
                    getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 15);
                    getAutoCreatedAccountBalance(ED25519_KEY).hasTokenBalance(FUNGIBLE_TOKEN, 10);
                    getAutoCreatedAccountBalance(SECP_256K1_KEY).hasTokenBalance(FUNGIBLE_TOKEN, 10);
                    getAutoCreatedAccountBalance(ANOTHER_SECP_256K1_KEY).hasTokenBalance(FUNGIBLE_TOKEN, 10);
                }));
    }

    @HapiTest
    final Stream<DynamicTest> consequentAirdrops() {
        // Verify that when sending 2 consequent airdrops to a recipient,
        // which associated themselves to the token after the first airdrop,
        // the second airdrop is directly transferred to the recipient and the first airdrop remains in pending state
        return defaultHapiSpec("consequentAirdrops")
                .given(
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(100L),
                        cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0))
                .when(
                        // send first airdrop
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                .payingWith(SENDER)
                                .via("first"),
                        getTxnRecord("first")
                                .andAllChildRecords()
                                // assert pending airdrops
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingFungiblePendingAirdrop(moving(10, FUNGIBLE_TOKEN)
                                                .between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS)))),
                        tokenAssociate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN)
                        // assert pending list and balances
                        )
                .then(
                        // this time tokens should be transferred
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                                .payingWith(SENDER)
                                .via("second"),
                        // assert sender and receiver accounts to ensure first airdrop is still in pending state
                        getTxnRecord("second")
                                .andAllChildRecords()
                                // assert transfers
                                .hasChildRecords(recordWith()
                                        .tokenTransfers(includingFungibleMovement(moving(10, FUNGIBLE_TOKEN)
                                                .between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))))
                                .logged(),

                        // since there is noway to check pending airdrop state, we just assert the account balances
                        getAccountBalance(SENDER).hasTokenBalance(FUNGIBLE_TOKEN, 90),
                        getAccountBalance(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).hasTokenBalance(FUNGIBLE_TOKEN, 10));
    }

    @HapiTest
    final Stream<DynamicTest> airdropInvalidTokenIdFails() {
        return defaultHapiSpec("airdropMissingTokenIdFails")
                .given(
                        cryptoCreate(SENDER),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        withOpContext((spec, opLog) -> {
                            final var acctCreate = cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS);
                            allRunFor(spec, acctCreate);
                            // Here we take an account ID and store it as a token ID in the registry, so that when the
                            // "token
                            // number" is submitted by the test client, it will recreate the bug scenario:
                            final var bogusTokenId = TokenID.newBuilder().setTokenNum(acctCreate.numOfCreatedAccount());
                            spec.registry().saveTokenId("nonexistent", bogusTokenId.build());
                        }))
                .when()
                .then(sourcing(() -> tokenAirdrop(movingWithDecimals(1L, "nonexistent", 2)
                                .betweenWithDecimals(SENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .payingWith(SENDER)
                        .hasKnownStatus(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final Stream<DynamicTest> airdropNFTNegativeSerial() {
        return defaultHapiSpec("airdropNFTNegativeSerial")
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

    @HapiTest
    final Stream<DynamicTest> airdropNegativeAmountFails() {
        return defaultHapiSpec("airdropNegativeAmountFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .maxSupply(10_000)
                                .initialSupply(5000)
                                .treasury(SENDER),
                        tokenAssociate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                .when()
                .then(tokenAirdrop(
                                moving(-15, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .payingWith(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS)
                        .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS));
    }

    @HapiTest
    final Stream<DynamicTest> spenderNotEnoughAllowanceFails() {
        return defaultHapiSpec("spenderNotEnoughAllowanceFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .maxSupply(10_000)
                                .initialSupply(5000)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        mintToken(FUNGIBLE_TOKEN, 500).via(FUNGIBLE_TOKEN_MINT_TXN))
                .when(
                        cryptoTransfer(moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 10)
                                .fee(ONE_HUNDRED_HBARS))
                .then(
                        tokenAirdrop(movingWithAllowance(50, FUNGIBLE_TOKEN)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                .payingWith(SPENDER)
                                .signedBy(SPENDER, OWNER)
                                .hasKnownStatus(AMOUNT_EXCEEDS_ALLOWANCE)
                                .via("spenderNotEnoughAllowance"),
                        getTxnRecord("spenderNotEnoughAllowance").logged());
    }

    @HapiTest
    final Stream<DynamicTest> senderWithMissingAssociationFails() {
        return defaultHapiSpec("senderWithMissingAssociationFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .maxSupply(10_000)
                                .initialSupply(5000)
                                .treasury(TOKEN_TREASURY))
                .when()
                .then(
                        tokenAirdrop(moving(50, FUNGIBLE_TOKEN)
                                        .between(SENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                .payingWith(SENDER)
                                .hasKnownStatus(INVALID_TRANSACTION)
                                .via("senderWithMissingAssociation"),
                        getTxnRecord("senderWithMissingAssociation").logged());
    }

    @HapiTest
    final Stream<DynamicTest> missingSenderSigFails() {
        return defaultHapiSpec("missingSenderSigFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .maxSupply(10_000)
                                .initialSupply(5000)
                                .treasury(SENDER))
                .when()
                .then(tokenAirdrop(
                                moving(50, FUNGIBLE_TOKEN).between(SENDER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> missingPayerSigFails() {
        return defaultHapiSpec("missingPayerSigFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(SPENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .maxSupply(10_000)
                                .initialSupply(5000)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN),
                        mintToken(FUNGIBLE_TOKEN, 500).via(FUNGIBLE_TOKEN_MINT_TXN))
                .when(
                        cryptoTransfer(moving(1000, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
                        cryptoApproveAllowance()
                                .payingWith(OWNER)
                                .addTokenAllowance(OWNER, FUNGIBLE_TOKEN, SPENDER, 100)
                                .fee(ONE_HUNDRED_HBARS))
                .then(tokenAirdrop(movingWithAllowance(50, FUNGIBLE_TOKEN)
                                .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                        .payingWith(SPENDER)
                        // Should be signed by spender as well
                        .signedBy(OWNER)
                        .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> ownerNotEnoughBalanceFails() {
        return defaultHapiSpec("ownerNotEnoughBalanceFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(OWNER).maxAutomaticTokenAssociations(10),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.FINITE)
                                .supplyKey(NFT_SUPPLY_KEY)
                                .maxSupply(10_000)
                                .initialSupply(5000)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(OWNER, FUNGIBLE_TOKEN))
                .when(cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)))
                .then(
                        tokenAirdrop(moving(99, FUNGIBLE_TOKEN)
                                        .between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS))
                                .payingWith(OWNER)
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS)
                                .via("ownerNotEnoughBalance"),
                        getTxnRecord("ownerNotEnoughBalance").logged());
    }

    @HapiTest
    final Stream<DynamicTest> duplicateEntryInTokenTransferFails() {
        return defaultHapiSpec("duplicateEntryInTokenTransferFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
                        cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(100 * ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(10),
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
                                        List.of(ByteString.copyFromUtf8("a"), ByteString.copyFromUtf8("b")))
                                .via(NFT_TOKEN_MINT_TXN),
                        cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)))
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
    final Stream<DynamicTest> aboveMaxTransfersFails() {
        return defaultHapiSpec("aboveMaxTransfersFails")
                .given(
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(10),
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

    private HapiTokenCreate createTokenWithName(String tokenName) {
        return tokenCreate(tokenName)
                .tokenType(TokenType.FUNGIBLE_COMMON)
                .supplyType(TokenSupplyType.FINITE)
                .supplyKey(NFT_SUPPLY_KEY)
                .maxSupply(10_000)
                .initialSupply(5000)
                .treasury(OWNER);
    }
}
