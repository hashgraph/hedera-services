/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip796;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdExceeds;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.nonFungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.treasuryOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

/**
 * A suite for user stories Association-1 through Association-7 from HIP-796.
 */
public class PartitionAssociationsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PartitionAssociationsSuite.class);

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                associateWithPartitionedTokenAutomatically(),
                associateWithPartitionAndToken(),
                autoAssociateSiblingPartitions(),
                autoAssociateChildPartitions(),
                disassociateEmptyPartition(),
                doNotAllowDisassociateWithNonEmptyPartition(),
                ensurePartitionsRemovedBeforeTokenDisassociation());
    }

    /**
     * <b>Association-1</b>
     * <p>As a user, I want to associate with a `token-definition` that has `partition-definitions`.
     * When tokens are sent to my account for a partition of that `token-definition`, then I want to
     * automatically associate with that `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> associateWithPartitionedTokenAutomatically() {
        return defaultHapiSpec("AssociateWithPartitionedTokenAutomatically")
                .given(nonFungibleTokenWithFeatures(PARTITIONING)
                        .withPartition(BLUE_PARTITION, p -> p.assignedSerialNos(2L)))
                .when(
                        tokenAssociate(ALICE, TOKEN_UNDER_TEST),
                        // We transfer a serial no of the BLUE partition to Alice, who only the parent associated
                        cryptoTransfer(movingUnique(partition(BLUE_PARTITION), 2L)
                                .between(treasuryOf(TOKEN_UNDER_TEST), ALICE)))
                .then(
                        // Alice now owns SN#2, despite not being explicitly associated with the BLUE partition
                        getTokenNftInfo(partition(BLUE_PARTITION), 2L).hasAccountID(ALICE));
    }

    /**
     * <b>Association-2</b>
     * <p>As a user, I want to associate with a `partition-definition`, exactly as I would for associating with any other
     * `token-definition`, and automatically get the `token-definition` associated too without extra cost, without using
     * the auto-association slots.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> associateWithPartitionAndToken() {
        return defaultHapiSpec("AssociateWithPartitionAndToken")
                .given(
                        cryptoCreate(ALICE).balance(ONE_HUNDRED_HBARS),
                        fungibleTokenWithFeatures(PARTITIONING).withPartitions(RED_PARTITION))
                .when(
                        // Alice associates with a partition of the token
                        tokenAssociate(ALICE, partition(RED_PARTITION)),
                        // And receives units of the parent token type from its treasury
                        cryptoTransfer(moving(1L, TOKEN_UNDER_TEST).between(treasuryOf(TOKEN_UNDER_TEST), ALICE))
                                .payingWith(treasuryOf(TOKEN_UNDER_TEST))
                                .blankMemo()
                                .via("parentTokenTransfer"))
                .then(
                        // There is no extra cost for the association to the parent token type, so the charged
                        // fee is within 10% of the canonical fungible transfer fee of 1/10 of a cent
                        validateChargedUsd("parentTokenTransfer", 0.001, 10),
                        // This should not use any auto-association slots
                        getAccountInfo(ALICE).has(accountWith().maxAutoAssociations(0)));
    }

    /**
     * <b>Association-3</b>
     * <p>As a user, once associated with a `partition-definition`, I want transfers into my account for "sibling"
     * `partition-definition`s to be auto-partition-associated with extra cost, without using the auto-association slots.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> autoAssociateSiblingPartitions() {
        return defaultHapiSpec("AutoAssociateSiblingPartitions")
                .given(fungibleTokenWithFeatures(PARTITIONING)
                        .withPartitions(RED_PARTITION)
                        .withPartitions(BLUE_PARTITION)
                        // No parent association, just the RED partition
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)))
                .when(
                        // Alice receives units of the BLUE partition from its treasury
                        cryptoTransfer(moving(1L, partition(BLUE_PARTITION))
                                        .between(treasuryOf(TOKEN_UNDER_TEST), ALICE))
                                .payingWith(treasuryOf(TOKEN_UNDER_TEST))
                                .blankMemo()
                                .via("blueTokenTransfer"))
                .then(
                        // There is significant extra cost for the association to the BLUE partition, so the
                        // charged fee exceeds even twice the fungible transfer fee of 1/10 of a cent
                        validateChargedUsdExceeds("blueTokenTransfer", 0.002),
                        // But this should not use any auto-association slots
                        getAccountInfo(ALICE).has(accountWith().maxAutoAssociations(0)));
    }

    /**
     * <b>Association-4</b>
     * <p>As a user, once associated with a `token-definition`, I want any transfers into my account for "child"
     * `partition-definition`s to be auto-partition-associated with extra cost, without using the auto-association slots.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> autoAssociateChildPartitions() {
        return defaultHapiSpec("AutoAssociateChildPartitions")
                .given(
                        cryptoCreate(CIVILIAN_PAYER).balance(ONE_HUNDRED_HBARS),
                        fungibleTokenWithFeatures(PARTITIONING)
                                .withPartitions(RED_PARTITION)
                                // No partitions associated yet, just the parent token definition
                                .withRelation(ALICE))
                .when(
                        // Alice receives units of the RED partition from its treasury
                        cryptoTransfer(moving(1L, partition(RED_PARTITION))
                                        .between(treasuryOf(TOKEN_UNDER_TEST), ALICE))
                                .payingWith(CIVILIAN_PAYER)
                                .blankMemo()
                                .via("redTokenTransfer"))
                .then(
                        // There is significant extra cost for the association to the RED partition, so the
                        // charged fee exceeds even twice the fungible transfer fee of 1/10 of a cent
                        validateChargedUsdExceeds("redTokenTransfer", 0.002),
                        // But this should not use any auto-association slots
                        getAccountInfo(ALICE).has(accountWith().maxAutoAssociations(0)));
    }

    /**
     * <b>Association-5</b>
     * <p>As a user, if a partition in my account holds no tokens, I want to disassociate from that
     * `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> disassociateEmptyPartition() {
        return defaultHapiSpec("DisassociateEmptyPartition")
                .given(
                        fungibleTokenWithFeatures(PARTITIONING)
                                .withPartitions(RED_PARTITION)
                                // No parent association, just the RED partition
                                .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)
                                        .balance(0L)),
                        nonFungibleTokenWithFeatures("NFT", PARTITIONING)
                                .withPartitions(RED_PARTITION)
                                // No parent association, just the RED partition
                                .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)))
                .when()
                .then(
                        // Ok to dissociate with no fungible units
                        tokenDissociate(ALICE, partition(RED_PARTITION)),
                        // Ok to dissociate with no serial nos
                        tokenDissociate(ALICE, partition("NFT", RED_PARTITION)));
    }

    /**
     * <b>Association-6</b>
     * <p>As a user, if a partition in my account holds tokens, I do not want to permit disassociation from
     * that `partition-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> doNotAllowDisassociateWithNonEmptyPartition() {
        return defaultHapiSpec("DoNotAllowDisassociateWithNonEmptyPartition")
                .given(
                        fungibleTokenWithFeatures(PARTITIONING)
                                .withPartitions(RED_PARTITION)
                                // No parent association, just the RED partition
                                .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)
                                        .balance(1L)),
                        nonFungibleTokenWithFeatures("NFT", PARTITIONING)
                                .withPartitions(RED_PARTITION)
                                // No parent association, just the RED partition
                                .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)
                                        .ownedSerialNos(1L)))
                .when()
                .then(
                        // Not ok to dissociate with fungible units
                        tokenDissociate(ALICE, partition(RED_PARTITION))
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
                        // Not ok to dissociate with serial nos
                        tokenDissociate(ALICE, partition("NFT", RED_PARTITION))
                                .hasKnownStatus(ACCOUNT_STILL_OWNS_NFTS));
    }

    /**
     * <b>Association-7</b>
     * <p>As a node operator, I do not want to permit a user to disassociate from a `token-definition`
     * if the user account has any related partitions. The partitions must be removed first.
     *
     * <p><b>IMPLEMENTATION DETAIL:</b> This will likely require a new {@link com.hedera.hapi.node.state.token.TokenRelation}
     * field to track the number of child partitions of a parent token type that a particular account
     * is associated to. Otherwise we would need to iterate over all partitions of a parent token type to
     * determine if any are associated with the account being dissociated from the parent.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> ensurePartitionsRemovedBeforeTokenDisassociation() {
        return defaultHapiSpec("EnsurePartitionsRemovedBeforeTokenDisassociation")
                .given(fungibleTokenWithFeatures(PARTITIONING)
                        .withPartitions(RED_PARTITION)
                        // Both a parent association and the RED partition association (though 0 balance)
                        .withRelation(
                                ALICE, r -> r.alsoForPartition(RED_PARTITION).balance(0L)))
                .when(
                        // Alice cannot dissociate from the parent token type since they are associated to RED
                        tokenDissociate(ALICE, TOKEN_UNDER_TEST)
                                // FUTURE - replace with a partition-specific status code
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
                        // But now if Alice dissociates from RED, they can dissociate from the parent token type
                        tokenDissociate(ALICE, partition(RED_PARTITION)))
                .then(tokenDissociate(ALICE, TOKEN_UNDER_TEST));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
