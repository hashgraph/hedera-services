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
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNonfungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingPendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
public class TokenAirdropSuite {

    private static final String AIRDROPS_ENABLED = "tokens.airdrops.enabled";
    private static final String SENDER = "sender";
    private static final String RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS = "receiverWithUnlimitedAutoAssociations";
    private static final String RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS = "receiverWithFreeAutoAssociations";
    private static final String RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS = "receiverWithoutFreeAutoAssociations";
    private static final String RECEIVER_WITH_0_AUTO_ASSOCIATIONS = "receiverWith0AutoAssociations";
    private static final String ASSOCIATED_RECEIVER = "associatedReceiver";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String DUMMY_FUNGIBLE_TOKEN = "dummyFungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String NFT_SUPPLY_KEY = "nftSupplyKey";
    private static final String SECP_256K1_KEY = "secp256K1";
    private static final String ANOTHER_SECP_256K1_KEY = "anotherSecp256K1";

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

        return propertyPreservingHapiSpec("tokenAirdropToExistingAccountsWorks")
                .preserving(AIRDROPS_ENABLED)
                .given(
                        // create fungible token and receivers accounts
                        overriding(AIRDROPS_ENABLED, "true"),
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
                        // get transaction record
                        getTxnRecord("fungible airdrop")
                                .andAllChildRecords()
                                // assert pending airdrops
                                // todo create method to check multiple pending elements
                                .hasPriority(recordWith()
                                        .pendingAirdrops(
                                                includingPendingAirdrop(moveToReceiverWith0AutoAssociations, true)))
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingPendingAirdrop(
                                                moveToReceiverWithoutFreeAutoAssociations, true)))
                                // assert transfers
                                .hasChildRecords(recordWith()
                                        .tokenTransfers(includingFungibleMovement(moving(30, FUNGIBLE_TOKEN)
                                                .distributing(
                                                        SENDER,
                                                        RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS,
                                                        RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS,
                                                        ASSOCIATED_RECEIVER))))
                                .logged()
                        // todo check account balances
                        );
    }

    @HapiTest
    final Stream<DynamicTest> nftAirdropToExistingAccountsWorks() {
        return propertyPreservingHapiSpec("nftAirdropToExistingAccountsWorks")
                .preserving(AIRDROPS_ENABLED)
                .given(
                        //overriding(AIRDROPS_ENABLED, "true"),
                        newKeyNamed(NFT_SUPPLY_KEY),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                        cryptoCreate(ASSOCIATED_RECEIVER),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .treasury(SENDER)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .name(NON_FUNGIBLE_TOKEN)
                                .supplyKey(NFT_SUPPLY_KEY),
                        tokenAssociate(ASSOCIATED_RECEIVER, NON_FUNGIBLE_TOKEN),
                        mintToken(
                                NON_FUNGIBLE_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"))),
                        tokenAirdrop(
                                        // add to pending state
                                        movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L)
                                                .between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                        // do the transfer
                                        movingUnique(NON_FUNGIBLE_TOKEN, 3L).between(SENDER, ASSOCIATED_RECEIVER))
                                .payingWith(SENDER)
                                .via("non fungible airdrop"),
                        getTxnRecord("non fungible airdrop")
                                .andAllChildRecords()
                                // check if one of the tokens is in the pending list
                                .hasPriority(recordWith()
                                        .pendingAirdrops(includingPendingAirdrop(
                                                movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L)
                                                        .between(SENDER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                                false)))
                                .hasChildRecords(recordWith()
                                        .tokenTransfers(
                                                includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 3L)
                                                        .between(SENDER, ASSOCIATED_RECEIVER))))
                                .logged())
                .when()
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> airdropToNonExistingAccountsWorks() {
        final AtomicReference<ByteString> evmAddress = new AtomicReference<>();
        return propertyPreservingHapiSpec("nftAirdropToExistingAccountsWorks")
                .preserving(AIRDROPS_ENABLED)
                .given(
                        overriding(AIRDROPS_ENABLED, "true"),
                        newKeyNamed("aliasReceiver"),
                        newKeyNamed(SECP_256K1_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(ANOTHER_SECP_256K1_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(SENDER).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(SENDER)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(20L),
                        withOpContext((spec, opLog) -> {
                            final var ecdsaKey = spec.registry()
                                    .getKey(ANOTHER_SECP_256K1_KEY)
                                    .getECDSASecp256K1()
                                    .toByteArray();
                            final var evmAddressBytes = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                            evmAddress.set(evmAddressBytes);
                            tokenAirdrop(
                                            moving(10, FUNGIBLE_TOKEN).between(SENDER, "aliasReceiver"),
                                            moving(5, FUNGIBLE_TOKEN).between(SENDER, SECP_256K1_KEY),
                                            moving(5, FUNGIBLE_TOKEN)
                                                    .between(
                                                            SENDER,
                                                            evmAddress.get())) // these tokens should transfer directly
                                    .payingWith(SENDER)
                                    .via("non existing account");
                            getTxnRecord("non existing account")
                                    .andAllChildRecords()
                                    .logged();
                        }))
                .when()
                .then();
    }
}
