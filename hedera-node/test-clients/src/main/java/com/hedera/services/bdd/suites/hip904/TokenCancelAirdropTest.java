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

package com.hedera.services.bdd.suites.hip904;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungiblePendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNftPendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCancelAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenReject;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenCancelAirdrop.pendingAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenCancelAirdrop.pendingNFTAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenReject.rejectingToken;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_HAS_PENDING_AIRDROPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_PENDING_AIRDROP_ID_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PENDING_AIRDROP_ID_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PENDING_AIRDROP_ID_REPEATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_HAS_NO_AIRDROPS_TO_CANCEL;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@HapiTestLifecycle
@DisplayName("Token cancel airdrop")
public class TokenCancelAirdropTest extends TokenAirdropBase {

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.overrideInClass(Map.of(
                "tokens.airdrops.enabled", "true",
                "tokens.airdrops.cancel.enabled", "true",
                "entities.unlimitedAutoAssociationsEnabled", "true"));
        lifecycle.doAdhoc(setUpTokensAndAllReceivers());
    }

    @HapiTest
    @DisplayName("FT happy path")
    final Stream<DynamicTest> ftHappyPath() {
        final var account = "account";
        return hapiTest(
                // setup initial account
                cryptoCreate(account),
                tokenAssociate(account, FUNGIBLE_TOKEN),
                cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(OWNER, account)),

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
        final var account = "account";
        return hapiTest(
                // setup initial account
                cryptoCreate(account),
                tokenAssociate(account, NON_FUNGIBLE_TOKEN),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(OWNER, account)),

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
    @DisplayName("not created NFT pending airdrop")
    final Stream<DynamicTest> cancelNotCreatedNFTPendingAirdrop() {
        return hapiTest(
                tokenCancelAirdrop(pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 5L))
                        .payingWith(OWNER)
                        .hasKnownStatus(SENDER_HAS_NO_AIRDROPS_TO_CANCEL));
    }

    @HapiTest
    @DisplayName("not created FT pending airdrop")
    final Stream<DynamicTest> cancelNotCreatedFTPendingAirdrop() {
        final var receiver = "receiver";
        return hapiTest(
                cryptoCreate(receiver),
                tokenCancelAirdrop(pendingAirdrop(OWNER, receiver, FUNGIBLE_TOKEN))
                        .payingWith(OWNER)
                        .hasKnownStatus(SENDER_HAS_NO_AIRDROPS_TO_CANCEL));
    }

    @HapiTest
    @DisplayName("with an empty airdrop list")
    final Stream<DynamicTest> cancelWithAnEmptyAirdropList() {
        return hapiTest(tokenCancelAirdrop().payingWith(OWNER).hasPrecheck(EMPTY_PENDING_AIRDROP_ID_LIST));
    }

    @HapiTest
    @DisplayName("with exceeding airdrops")
    final Stream<DynamicTest> cancelWithExceedingAirdrops() {
        return hapiTest(tokenCancelAirdrop(
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 1L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 2L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 3L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 4L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 5L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 6L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 7L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 8L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 9L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 10L),
                        pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                .payingWith(OWNER)
                .hasKnownStatus(PENDING_AIRDROP_ID_LIST_TOO_LONG));
    }

    @HapiTest
    @DisplayName("with duplicated FT")
    final Stream<DynamicTest> cancelWithDuplicatedFT() {
        return hapiTest(tokenCancelAirdrop(
                        pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN),
                        pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                .payingWith(OWNER)
                .hasPrecheck(PENDING_AIRDROP_ID_REPEATED));
    }

    @HapiTest
    @DisplayName("with duplicated NFT")
    final Stream<DynamicTest> cancelWithDuplicatedNFT() {
        return hapiTest(tokenCancelAirdrop(
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 1L),
                        pendingNFTAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 1L))
                .payingWith(OWNER)
                .hasPrecheck(PENDING_AIRDROP_ID_REPEATED));
    }

    @HapiTest
    @DisplayName("FT not signed by the owner")
    final Stream<DynamicTest> cancelFTNotSingedByTheOwner() {
        var account = "account";
        var randomAccount = "randomAccount";
        return hapiTest(
                // setup initial account
                cryptoCreate(account),
                tokenAssociate(account, FUNGIBLE_TOKEN),
                cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(OWNER, account)),
                cryptoCreate(randomAccount),
                tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .payingWith(account),
                tokenCancelAirdrop(pendingAirdrop(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                        .signedBy(randomAccount)
                        .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("NFT not signed by the owner")
    final Stream<DynamicTest> cancelNFTNotSingedByTheOwner() {
        var account = "account";
        var randomAccount = "randomAccount";
        return hapiTest(
                // setup initial account
                cryptoCreate(account),
                tokenAssociate(account, NON_FUNGIBLE_TOKEN),
                cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 2L).between(OWNER, account)),
                cryptoCreate(randomAccount),
                tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 2L).between(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .payingWith(account),
                tokenCancelAirdrop(
                                pendingNFTAirdrop(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, NON_FUNGIBLE_TOKEN, 1L))
                        .signedBy(randomAccount)
                        .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("cannot delete account when pending airdrop present")
    final Stream<DynamicTest> cannotDeleteAccountWhenPendingAirdropPresent() {
        final var account = "account";
        return hapiTest(
                cryptoCreate(account),
                tokenAssociate(account, FUNGIBLE_TOKEN),
                cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(OWNER, account)),
                tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .payingWith(account),
                cryptoDelete(account).hasKnownStatus(ACCOUNT_HAS_PENDING_AIRDROPS),
                tokenCancelAirdrop(pendingAirdrop(account, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                        .payingWith(account),
                tokenReject(rejectingToken(FUNGIBLE_TOKEN)).payingWith(account).signedBy(account),
                cryptoDelete(account));
    }
}
