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

package com.hedera.services.bdd.suites.crypto.airdrops;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadDefaultFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.uploadGivenFeeSchedules;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithChild;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.accountId;
import static com.hedera.services.bdd.suites.contract.Utils.ocWith;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.NON_FUNGIBLE_TOKEN;
import static com.hedera.services.bdd.suites.crypto.CryptoDeleteSuite.TREASURY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@HapiTestLifecycle
@DisplayName("UnlimitedAutoAssociationSuite")
@Tag(TOKEN)
class UnlimitedAutoAssociationSuite {
    private static final double expectedCreateHollowAccountFee = 0.0472956012;
    private static final double transferFee = 0.00189;
    private static final double expectedFeeForOneAssociation = 0.05;
    private static final double transferAndAssociationFee =
            expectedCreateHollowAccountFee + transferFee + 2 * expectedFeeForOneAssociation;
    private static final String ALICE = "ALICE";
    private static final String BOB = "BOB";
    private static final String CAROL = "CAROL";
    private static final String DAVE = "DAVE";

    @BeforeAll
    static void setUp(@NonNull final SpecManager manager) throws Throwable {
        manager.setup(
                overriding("entities.unlimitedAutoAssociationsEnabled", "true"),
                uploadGivenFeeSchedules(GENESIS, "unlimited-associations-fee-schedules.json"));
    }

    @AfterAll
    static void tearDown(@NonNull final SpecManager manager) throws Throwable {
        manager.setup(
                overriding("entities.unlimitedAutoAssociationsEnabled", "false"), uploadDefaultFeeSchedules(GENESIS));
    }

    @DisplayName("Auto-associate tokens will create a child record for association")
    @HapiTest
    final Stream<DynamicTest> autoAssociateTokensHappyPath() {
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";
        final String firstUser = "firstUser";
        final String secondUser = "secondUser";
        final String transferFungible = "transferFungible";
        final String transferNonFungible = "transferNonFungible";
        return hapiTest(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(firstUser).balance(ONE_HUNDRED_HBARS),
                cryptoCreate(secondUser).balance(ONE_HBAR).maxAutomaticTokenAssociations(10),
                tokenCreate(tokenA)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(Long.MAX_VALUE)
                        .treasury(firstUser),
                tokenCreate(tokenB)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .supplyKey(MULTI_KEY)
                        .treasury(firstUser),
                mintToken(tokenB, List.of(ByteString.copyFromUtf8("metadata1"), ByteString.copyFromUtf8("metadata2"))),
                // Transfer fungible token
                cryptoTransfer(moving(1, tokenA).between(firstUser, secondUser))
                        .payingWith(firstUser)
                        .via(transferFungible),
                getTxnRecord(transferFungible)
                        .andAllChildRecords()
                        .hasChildRecordCount(1)
                        .hasNewTokenAssociation(tokenA, secondUser)
                        .logged(),
                // Transfer NFT
                cryptoTransfer(movingUnique(tokenB, 1).between(firstUser, secondUser))
                        .payingWith(firstUser)
                        .via(transferNonFungible),
                getTxnRecord(transferNonFungible)
                        .andAllChildRecords()
                        .hasChildRecordCount(1)
                        .hasNewTokenAssociation(tokenB, secondUser)
                        .logged(),
                getAccountInfo(secondUser)
                        .hasAlreadyUsedAutomaticAssociations(2)
                        .logged(),
                // Total fee should include  a token association fee ($0.05) and CryptoTransfer fee ($0.001)
                validateChargedUsdWithChild(transferFungible, 0.05 + 0.001, 0.1),
                validateChargedUsdWithChild(transferNonFungible, 0.05 + 0.001, 0.1));
    }

    @DisplayName("Hollow account creation has correct auto associations")
    @HapiTest
    final Stream<DynamicTest> createHollowAccountWithFtTransfer() {
        final var tokenA = "tokenA";
        final var tokenB = "tokenB";
        final var hollowKey = "hollowKey";
        final AtomicReference<TokenID> tokenIdA = new AtomicReference<>();
        final AtomicReference<TokenID> tokenIdB = new AtomicReference<>();
        final AtomicReference<ByteString> treasuryAlias = new AtomicReference<>();
        final AtomicReference<ByteString> hollowAccountAlias = new AtomicReference<>();
        final var hollowAccountTxn = "hollowAccountTxn";
        return hapiTest(
                newKeyNamed(hollowKey).shape(SECP_256K1_SHAPE),
                cryptoCreate(TREASURY).balance(10_000 * ONE_MILLION_HBARS),
                tokenCreate(tokenA)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(10L)
                        .treasury(TREASURY),
                tokenCreate(tokenB)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(10L)
                        .treasury(TREASURY),
                withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var treasuryAccountId = registry.getAccountID(TREASURY);
                    treasuryAlias.set(ByteString.copyFrom(asSolidityAddress(treasuryAccountId)));

                    // Save the alias for the hollow account
                    final var ecdsaKey = spec.registry()
                            .getKey(hollowKey)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddressBytes = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    hollowAccountAlias.set(evmAddressBytes);
                    tokenIdA.set(registry.getTokenID(tokenA));
                    tokenIdB.set(registry.getTokenID(tokenB));
                }),
                // Create hollow account with 2 token transfers
                cryptoTransfer((s, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(tokenIdA.get())
                                        .addTransfers(aaWith(treasuryAlias.get(), -1))
                                        .addTransfers(aaWith(hollowAccountAlias.get(), +1)))
                                .addTokenTransfers(TokenTransferList.newBuilder()
                                        .setToken(tokenIdB.get())
                                        .addTransfers(aaWith(treasuryAlias.get(), -1))
                                        .addTransfers(aaWith(hollowAccountAlias.get(), +1))))
                        .payingWith(TREASURY)
                        .signedBy(TREASURY)
                        .via(hollowAccountTxn),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // Verify there is an auto association to FT1 and FT2
                        getAliasedAccountInfo(hollowKey)
                                .hasToken(relationshipWith(tokenA))
                                .hasToken(relationshipWith(tokenB))
                                .hasAlreadyUsedAutomaticAssociations(2)
                                .hasMaxAutomaticAssociations(-1)
                                .has(accountWith().hasEmptyKey())
                                .exposingIdTo(id -> spec.registry().saveAccountId(hollowKey, id))
                                .logged())),
                // Transfer some hbars to the hollow account so that it could pay the next transaction
                cryptoTransfer(movingHbar(ONE_MILLION_HBARS).between(TREASURY, hollowKey)),
                // Send transfer to complete the hollow account
                cryptoTransfer(moving(1, tokenA).between(hollowKey, TREASURY))
                        .payingWith(hollowKey)
                        .signedBy(hollowKey, TREASURY)
                        .sigMapPrefixes(uniqueWithFullPrefixesFor(hollowKey)),
                // Verify hollow account completion and keep max automatic associations to -1
                getAliasedAccountInfo(hollowKey)
                        .has(accountWith().key(hollowKey).maxAutoAssociations(-1))
                        .hasAlreadyUsedAutomaticAssociations(2),
                validateChargedUsdWithChild(hollowAccountTxn, transferAndAssociationFee, 1.0));
    }

    @DisplayName("Hollow account creation with NFT transfer has correct auto associations")
    @HapiTest
    final Stream<DynamicTest> createHollowAccountWithNftTransfer() {
        final var tokenA = "tokenA";
        final var tokenB = "tokenB";
        final var hollowAccountKey = "hollowAccountKey";
        final var hollowTransferTxn = "hollowTransferTxn";
        final AtomicReference<TokenID> tokenIdA = new AtomicReference<>();
        final AtomicReference<TokenID> tokenIdB = new AtomicReference<>();
        final AtomicReference<ByteString> treasuryAlias = new AtomicReference<>();
        final AtomicReference<ByteString> hollowAccountAlias = new AtomicReference<>();
        return hapiTest(
                newKeyNamed(hollowAccountKey).shape(SECP_256K1_SHAPE),
                newKeyNamed(MULTI_KEY),
                cryptoCreate(TREASURY).balance(10_000 * ONE_MILLION_HBARS),
                // Create NFT1
                tokenCreate(tokenA)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .supplyKey(MULTI_KEY)
                        .treasury(TREASURY),
                // Create NFT2
                tokenCreate(tokenB)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .supplyKey(MULTI_KEY)
                        .treasury(TREASURY),
                withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var treasuryAccountId = registry.getAccountID(TREASURY);
                    treasuryAlias.set(ByteString.copyFrom(asSolidityAddress(treasuryAccountId)));

                    // Save the alias for the hollow account
                    final var ecdsaKey = spec.registry()
                            .getKey(hollowAccountKey)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddressBytes = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    hollowAccountAlias.set(evmAddressBytes);
                    tokenIdA.set(registry.getTokenID(tokenA));
                    tokenIdB.set(registry.getTokenID(tokenB));
                }),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // Mint all NFTs
                        mintToken(tokenA, List.of(ByteString.copyFromUtf8("metadata1"))),
                        mintToken(tokenA, List.of(ByteString.copyFromUtf8("metadata2"))),
                        mintToken(tokenB, List.of(ByteString.copyFromUtf8("metadata3"))),
                        mintToken(tokenB, List.of(ByteString.copyFromUtf8("metadata4"))),
                        // create hollow account with maxAutomaticAssociations set to -1
                        cryptoCreate("testAccount")
                                .key(hollowAccountKey)
                                .maxAutomaticTokenAssociations(-1)
                                .alias(hollowAccountAlias.get()),
                        // Verify maxAutomaticAssociations is set to -1 and there is no auto association
                        getAccountInfo("testAccount")
                                .hasAlreadyUsedAutomaticAssociations(0)
                                .has(accountWith().key(hollowAccountKey).maxAutoAssociations(-1)),
                        // Delete the hollow account
                        cryptoDelete("testAccount").hasKnownStatus(SUCCESS),
                        // Create hollow account
                        cryptoTransfer((s, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                                .setToken(tokenIdA.get())
                                                .addNftTransfers(ocWith(
                                                        accountId(treasuryAlias.get()),
                                                        accountId(hollowAccountAlias.get()),
                                                        1L)))
                                        .addTokenTransfers(TokenTransferList.newBuilder()
                                                .setToken(tokenIdB.get())
                                                .addNftTransfers(ocWith(
                                                        accountId(treasuryAlias.get()),
                                                        accountId(hollowAccountAlias.get()),
                                                        1L))))
                                .payingWith(TREASURY)
                                .signedBy(TREASURY)
                                .via(hollowTransferTxn),
                        // Verify maxAutomaticAssociations is set to -1 and there is an auto association to NFT1 and
                        // NFT2
                        getAliasedAccountInfo(hollowAccountKey)
                                .hasToken(relationshipWith(tokenA))
                                .hasToken(relationshipWith(tokenB))
                                .hasMaxAutomaticAssociations(-1)
                                .hasAlreadyUsedAutomaticAssociations(2)
                                .has(accountWith().hasEmptyKey())
                                .exposingIdTo(id -> spec.registry().saveAccountId(hollowAccountKey, id)),
                        // Transfer some hbars to the hollow account so that it could pay the next transaction
                        cryptoTransfer(movingHbar(ONE_MILLION_HBARS).between(TREASURY, hollowAccountKey)),
                        cryptoTransfer(movingUnique(tokenA, 1L).between(hollowAccountKey, TREASURY))
                                .payingWith(hollowAccountKey)
                                .signedBy(hollowAccountKey, TREASURY)
                                .sigMapPrefixes(uniqueWithFullPrefixesFor(hollowAccountKey)),
                        // Verify hollow account completion
                        getAliasedAccountInfo(hollowAccountKey)
                                .has(accountWith().key(hollowAccountKey).maxAutoAssociations(-1))
                                .hasAlreadyUsedAutomaticAssociations(2))),
                validateChargedUsdWithChild(hollowTransferTxn, transferAndAssociationFee, 1.0));
    }

    @DisplayName("Hollow account creation with multiple senders correct auto associations")
    @HapiTest
    final Stream<DynamicTest> createHollowAccountWithMultipleSenders() {
        final var transfersToHollowAccountTxn = "transfersToHollowAccountTxn";
        final AtomicReference<TokenID> tokenIdA = new AtomicReference<>();
        final AtomicReference<TokenID> tokenIdB = new AtomicReference<>();
        final AtomicReference<ByteString> aliceAlias = new AtomicReference<>();
        final AtomicReference<ByteString> bobAlias = new AtomicReference<>();
        final AtomicReference<ByteString> carolHollowAccountAlias = new AtomicReference<>();
        final double expectedCryptoTransferAndAssociationUsd =
                expectedCreateHollowAccountFee + 2 * transferFee + 2 * expectedFeeForOneAssociation;

        return hapiTest(
                newKeyNamed(MULTI_KEY),
                cryptoCreate(ALICE).balance(10_000 * ONE_MILLION_HBARS),
                cryptoCreate(BOB).balance(10_000 * ONE_MILLION_HBARS),
                newKeyNamed(CAROL).shape(SECP_256K1_SHAPE),
                cryptoCreate(DAVE).maxAutomaticTokenAssociations(1),
                cryptoCreate(TREASURY).balance(ONE_MILLION_HBARS),
                tokenCreate(FUNGIBLE_TOKEN)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(5)
                        .treasury(ALICE),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .supplyKey(MULTI_KEY)
                        .treasury(BOB),
                withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    aliceAlias.set(ByteString.copyFrom(asSolidityAddress(registry.getAccountID(ALICE))));
                    bobAlias.set(ByteString.copyFrom(asSolidityAddress(registry.getAccountID(BOB))));

                    // Save the alias for the hollow account
                    final var ecdsaKey =
                            spec.registry().getKey(CAROL).getECDSASecp256K1().toByteArray();
                    final var evmAddressBytes = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    carolHollowAccountAlias.set(evmAddressBytes);
                    tokenIdA.set(registry.getTokenID(FUNGIBLE_TOKEN));
                    tokenIdB.set(registry.getTokenID(NON_FUNGIBLE_TOKEN));
                }),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // Mint the NFT
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8("metadata1"))),
                        // Transfer both tokens to hollow account with different senders.
                        cryptoTransfer((s, b) -> b.addTokenTransfers(TokenTransferList.newBuilder()
                                                .setToken(tokenIdA.get())
                                                .addTransfers(aaWith(aliceAlias.get(), -1))
                                                .addTransfers(aaWith(carolHollowAccountAlias.get(), +1)))
                                        .addTokenTransfers(TokenTransferList.newBuilder()
                                                .setToken(tokenIdB.get())
                                                .addNftTransfers(ocWith(
                                                        accountId(bobAlias.get()),
                                                        accountId(carolHollowAccountAlias.get()),
                                                        1L))))
                                .payingWith(ALICE)
                                .signedBy(ALICE, BOB)
                                .via(transfersToHollowAccountTxn),
                        // Verify the auto associations to the fungible token and the NFT in the hollow account
                        getAliasedAccountInfo(CAROL)
                                .hasToken(relationshipWith(FUNGIBLE_TOKEN))
                                .hasToken(relationshipWith(NON_FUNGIBLE_TOKEN))
                                .hasMaxAutomaticAssociations(-1)
                                .has(accountWith().hasEmptyKey())
                                .exposingIdTo(id -> spec.registry().saveAccountId(CAROL, id)),
                        // Transfer some hbars to the hollow account so that it could pay the next transaction
                        cryptoTransfer(movingHbar(ONE_MILLION_HBARS).between(BOB, CAROL)),
                        // Send transfer to complete the hollow account
                        cryptoTransfer(moving(1, FUNGIBLE_TOKEN).between(CAROL, DAVE))
                                .payingWith(CAROL)
                                .signedBy(CAROL, DAVE)
                                .sigMapPrefixes(uniqueWithFullPrefixesFor(CAROL)),
                        validateChargedUsdWithChild(
                                transfersToHollowAccountTxn, expectedCryptoTransferAndAssociationUsd, 1.0))));
    }
}
