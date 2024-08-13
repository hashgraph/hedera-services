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
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNonfungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenClaimAirdrop;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenClaimAirdrop.pendingAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.HapiTokenClaimAirdrop.pendingNFTAirdrop;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

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
@DisplayName("Claim token airdrop")
public class TokenClaimAirdropTest extends TokenAirdropBase {
    private static final String RECEIVER = "receiver";

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle lifecycle) {
        lifecycle.overrideInClass(Map.of(
                "entities.unlimitedAutoAssociationsEnabled", "false",
                "tokens.airdrops.enabled", "false",
                "tokens.airdrops.claim.enabled", "false"));
        // create some entities with disabled airdrops
        lifecycle.doAdhoc(setUpEntitiesPreHIP904());
        // enable airdrops
        lifecycle.doAdhoc(
                overriding("entities.unlimitedAutoAssociationsEnabled", "true"),
                overriding("tokens.airdrops.enabled", "true"),
                overriding("tokens.airdrops.claim.enabled", "true"));

        lifecycle.doAdhoc(setUpTokensAndAllReceivers());
    }

    @HapiTest
    final Stream<DynamicTest> claimFungibleTokenAirdrop() {
        return defaultHapiSpec("should transfer fungible tokens")
                .given(cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS))
                .when(
                        // do pending airdrop
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER))
                                .payingWith(OWNER),
                        tokenAirdrop(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(OWNER, RECEIVER))
                                .payingWith(OWNER),

                        // do claim
                        tokenClaimAirdrop(
                                        pendingAirdrop(OWNER, RECEIVER, FUNGIBLE_TOKEN),
                                        pendingNFTAirdrop(OWNER, RECEIVER, NON_FUNGIBLE_TOKEN, 1))
                                .payingWith(RECEIVER)
                                .feeUsd(0.001)
                                .via("claimTxn"))
                .then( // assert txn record
                        getTxnRecord("claimTxn")
                                .hasPriority(recordWith()
                                        .tokenTransfers(includingFungibleMovement(
                                                moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER)))
                                        .tokenTransfers(includingNonfungibleMovement(movingUnique(NON_FUNGIBLE_TOKEN, 1)
                                                .between(OWNER, RECEIVER)))),
                        // assert balance fungible tokens
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 10),
                        // assert balances NFT
                        getAccountBalance(RECEIVER).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1),
                        // assert token associations
                        getAccountInfo(RECEIVER).hasToken(relationshipWith(FUNGIBLE_TOKEN)),
                        getAccountInfo(RECEIVER).hasToken(relationshipWith(NON_FUNGIBLE_TOKEN)));
    }

    @HapiTest
    @DisplayName("Claim frozen token airdrop")
    final Stream<DynamicTest> claimFrozenToken() {
        final var tokenFreezeKey = "freezeKey";
        return defaultHapiSpec("should fail - ACCOUNT_FROZEN_FOR_TOKEN")
                .given(
                        newKeyNamed(tokenFreezeKey),
                        cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(0),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .treasury(OWNER)
                                .freezeKey(tokenFreezeKey)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(1000L))
                .when(
                        tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(OWNER, RECEIVER))
                                .payingWith(OWNER),
                        tokenFreeze(FUNGIBLE_TOKEN, OWNER))
                .then(
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 0),
                        tokenClaimAirdrop(pendingAirdrop(OWNER, RECEIVER, FUNGIBLE_TOKEN))
                                .payingWith(RECEIVER)
                                .feeUsd(0.001)
                                .hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
                        getAccountBalance(RECEIVER).hasTokenBalance(FUNGIBLE_TOKEN, 0));
    }

    @HapiTest
    @DisplayName("hollow account with 0 free maxAutoAssociations")
    final Stream<DynamicTest> airdropToAliasWithNoFreeSlots() {
        final var validAlias = "validAlias";
        return hapiTest(
                tokenAirdrop(moving(1, FUNGIBLE_TOKEN).between(OWNER, validAlias))
                        .payingWith(OWNER),
                // check if account is hollow (has empty key)
                getAliasedAccountInfo(validAlias)
                        .has(accountWith().hasEmptyKey().noAlias()),

                // claim and check if account is finalized
                tokenClaimAirdrop(pendingAirdrop(OWNER, validAlias, FUNGIBLE_TOKEN))
                        .feeUsd(0.001)
                        .payingWith(validAlias)
                        .sigMapPrefixes(uniqueWithFullPrefixesFor(validAlias)),

                // check if account was finalized (has the correct key)
                getAliasedAccountInfo(validAlias)
                        .has(accountWith().key(validAlias).noAlias()));
    }

    @HapiTest
    @DisplayName("given two same claims, secod should fail")
    final Stream<DynamicTest> twoSameClaims() {
        return hapiTest(
                tokenAirdrop(moving(1, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .payingWith(OWNER),
                tokenClaimAirdrop(pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                        .feeUsd(0.001)
                        .payingWith(RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                tokenClaimAirdrop(pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                        .feeUsd(0.001)
                        .payingWith(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                        .hasKnownStatus(INVALID_TRANSACTION_BODY));
    }

    @HapiTest
    @DisplayName("missing pending airdrop, claim should fail")
    final Stream<DynamicTest> missingPendingClaim() {
        return hapiTest(tokenClaimAirdrop(pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                .feeUsd(0.001)
                .payingWith(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                .hasKnownStatus(INVALID_TRANSACTION_BODY));
    }

    @HapiTest
    @DisplayName("signed by account not referenced as receiver_id in each pending airdrops")
    final Stream<DynamicTest> signedByAccountNotReferencedAsReceiverId() {
        return hapiTest(
                tokenAirdrop(
                                moving(1, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS),
                                moving(1, FUNGIBLE_TOKEN).between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS))
                        .payingWith(OWNER),
                tokenClaimAirdrop(
                                pendingAirdrop(OWNER, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN),
                                pendingAirdrop(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                        .feeUsd(0.001)
                        .payingWith(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    @DisplayName("spender has insufficient balance")
    final Stream<DynamicTest> spenderHasInsufficientBalance() {
        var spender = "spenderWithInsufficientBalance";
        return hapiTest(
                cryptoCreate(spender).balance(ONE_HBAR).maxAutomaticTokenAssociations(-1),
                cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(OWNER, spender)),
                tokenAirdrop(moving(10, FUNGIBLE_TOKEN).between(spender, RECEIVER_WITH_0_AUTO_ASSOCIATIONS))
                        .payingWith(spender),
                cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(spender, OWNER)),
                tokenClaimAirdrop(pendingAirdrop(spender, RECEIVER_WITH_0_AUTO_ASSOCIATIONS, FUNGIBLE_TOKEN))
                        .payingWith(RECEIVER_WITH_0_AUTO_ASSOCIATIONS)
                        .feeUsd(0.001)
                        .hasKnownStatus(INSUFFICIENT_TOKEN_BALANCE));
    }
}
