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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withHeadlongAddressesFor;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.DIFFERENT_USER_PARTITION_MOVE_UNITS_FUNCTION;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.SAME_USER_PARTITION_MOVE_UNITS_FUNCTION;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.deletePartition;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.moveNftsBetweenDifferentUserPartitions;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.moveNftsBetweenSameUserPartitions;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.moveUnitsBetweenDifferentUserPartitions;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.moveUnitsBetweenSameUserPartitions;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.nonFungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.managementContractOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partitionKeyOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partitionMoveKeyOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.INTER_PARTITION_MANAGEMENT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

/**
 * A suite for user stories Move-1 through Move-5 from HIP-796.
 */
public class InterPartitionMovementSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(InterPartitionMovementSuite.class);

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                partitionMoveWithoutUserSignature(),
                partitionMoveWithUserSignature(),
                moveNftsBetweenUserPartitionsWithoutUserSignature(),
                moveNftsBetweenPartitionsWithUserSignature(),
                moveTokensViaSmartContractAsPartitionMoveKey());
    }

    /**
     * <b>Move-1</b>
     * <p>As a `partition-move-manager`, I want to move fungible tokens from one partition
     * (existing or deleted) to a different (new or existing) partition on the same user account,
     * without requiring a signature from the user holding the balance.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> partitionMoveWithoutUserSignature() {
        return defaultHapiSpec("PartitionMoveWithoutUserSignature")
                .given(fungibleTokenWithFeatures(INTER_PARTITION_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION, GREEN_PARTITION)
                        .withRelation(
                                ALICE, r -> r.onlyForPartition(RED_PARTITION).andPartition(BLUE_PARTITION)))
                .when(deletePartition(RED_PARTITION))
                .then(
                        // Even though the red partition is deleted, we can still move Alice's units out of it
                        moveUnitsBetweenSameUserPartitions(
                                ALICE, RED_PARTITION, BLUE_PARTITION, FUNGIBLE_INITIAL_BALANCE),
                        // Even though Alice was not associated to the green partition, we can still move their units
                        // into it
                        moveUnitsBetweenSameUserPartitions(
                                ALICE, BLUE_PARTITION, GREEN_PARTITION, 2 * FUNGIBLE_INITIAL_BALANCE));
    }

    /**
     * <b>Move-2</b>
     * <p>As a `partition-move-manager`, I want to move fungible tokens from one partition
     * (existing or deleted) to a different (new or existing) partition on a different user account,
     * but requiring a signature from the user's account being debited.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> partitionMoveWithUserSignature() {
        return defaultHapiSpec("PartitionMoveWithUserSignature")
                .given(fungibleTokenWithFeatures(INTER_PARTITION_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION, GREEN_PARTITION)
                        .withRelation(
                                ALICE, r -> r.onlyForPartition(RED_PARTITION).andPartition(BLUE_PARTITION))
                        .withRelation(BOB, r -> r.onlyForPartition(GREEN_PARTITION)))
                .when(deletePartition(RED_PARTITION))
                .then(
                        // Even though the red partition is deleted, we can still move Alice's units out of it
                        // if we have her signature
                        moveUnitsBetweenDifferentUserPartitions(
                                        ALICE, RED_PARTITION, BOB, BLUE_PARTITION, FUNGIBLE_INITIAL_BALANCE)
                                .signedByPayerAnd(partitionKeyOf(TOKEN_UNDER_TEST))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        moveUnitsBetweenDifferentUserPartitions(
                                ALICE, RED_PARTITION, BOB, BLUE_PARTITION, FUNGIBLE_INITIAL_BALANCE),
                        // Even though Bob was not associated to the blue partition, they can still receive units in it
                        moveUnitsBetweenDifferentUserPartitions(
                                ALICE, BLUE_PARTITION, BOB, BLUE_PARTITION, FUNGIBLE_INITIAL_BALANCE));
    }

    /**
     * <b>Move-3</b>
     * <p>As a `partition-move-manager`, I want to move non-fungible tokens from one partition
     * (existing or deleted) to another (new or existing) partition on the same user account,
     * without requiring a signature from the user.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> moveNftsBetweenUserPartitionsWithoutUserSignature() {
        return defaultHapiSpec("MoveNftsBetweenUserPartitionsWithoutUserSignature")
                .given(nonFungibleTokenWithFeatures(INTER_PARTITION_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION, GREEN_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(1L, 2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L))))
                .when(deletePartition(RED_PARTITION))
                .then(
                        // Even though the red partition is deleted, we can still move Alice's NFTs out of it
                        moveNftsBetweenSameUserPartitions(ALICE, RED_PARTITION, BLUE_PARTITION, 1L, 2L),
                        // Even though Alice was not associated to the green partition, we can still move their NFTs
                        // into it
                        moveNftsBetweenSameUserPartitions(ALICE, BLUE_PARTITION, GREEN_PARTITION, 1L, 2L, 3L));
    }

    /**
     * <b>Move-4</b>
     * <p>As a `partition-move-manager`, I want to move non-fungible tokens from one partition
     * (existing or deleted) to another (new or existing) partition on a different user account,
     * but requiring a signature from the user.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> moveNftsBetweenPartitionsWithUserSignature() {
        return defaultHapiSpec("MoveNftsBetweenPartitionsWithUserSignature")
                .given(nonFungibleTokenWithFeatures(INTER_PARTITION_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION, GREEN_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION, pr -> pr.ownedSerialNos(1L, 2L))
                                .andPartition(BLUE_PARTITION, pr -> pr.ownedSerialNos(3L)))
                        .withRelation(
                                BOB, r -> r.onlyForPartition(GREEN_PARTITION).receiverSigRequired()))
                .when(deletePartition(RED_PARTITION))
                .then(
                        // Even though the red partition is deleted, we can still move Alice's NFTs out of it
                        // if we have her signature
                        moveNftsBetweenDifferentUserPartitions(ALICE, RED_PARTITION, BOB, BLUE_PARTITION, 1L, 2L)
                                .signedByPayerAnd(partitionMoveKeyOf(TOKEN_UNDER_TEST))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        moveNftsBetweenDifferentUserPartitions(ALICE, RED_PARTITION, BOB, BLUE_PARTITION, 1L, 2L),
                        // Even though Bob was not associated to the blue partition, they can still receive Alice's
                        // NFTs in it, as long as BOTH Alice and Bob sign (note Bob's receiver sig requirement)
                        moveNftsBetweenDifferentUserPartitions(ALICE, BLUE_PARTITION, BOB, BLUE_PARTITION, 3L)
                                .signedByPayerAnd(partitionMoveKeyOf(TOKEN_UNDER_TEST))
                                .hasKnownStatus(INVALID_SIGNATURE),
                        moveNftsBetweenDifferentUserPartitions(ALICE, BLUE_PARTITION, BOB, BLUE_PARTITION, 3L)
                                .signedByPayerAnd(partitionMoveKeyOf(TOKEN_UNDER_TEST), ALICE)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        moveNftsBetweenDifferentUserPartitions(ALICE, BLUE_PARTITION, BOB, BLUE_PARTITION, 3L)
                                .signedByPayerAnd(partitionMoveKeyOf(TOKEN_UNDER_TEST), ALICE, BOB));
    }

    /**
     * <b>Move-5</b>
     * <p>As a `token-administrator` smart contract, I want to move tokens from one partition
     * to another, in the same account or to a different account, if my contract ID is specified
     * as the `partition-move-key`, and all other conditions are met.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> moveTokensViaSmartContractAsPartitionMoveKey() {
        return defaultHapiSpec("MoveTokensViaSmartContractAsPartitionMoveKey")
                .given(fungibleTokenWithFeatures(INTER_PARTITION_MANAGEMENT)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION, GREEN_PARTITION)
                        .withRelation(
                                ALICE, r -> r.onlyForPartition(RED_PARTITION).andPartition(BLUE_PARTITION))
                        .withRelation(BOB, r -> r.onlyForPartition(GREEN_PARTITION))
                        .withRelation(CAROL, r -> r.onlyForPartition(RED_PARTITION)
                                .andPartition(BLUE_PARTITION)
                                .managedBy(managementContractOf(TOKEN_UNDER_TEST)))
                        .managedByContract())
                .when(deletePartition(RED_PARTITION))
                .then(withHeadlongAddressesFor(
                        List.of(
                                ALICE,
                                BOB,
                                CAROL,
                                partition(RED_PARTITION),
                                partition(BLUE_PARTITION),
                                partition(GREEN_PARTITION)),
                        addresses -> List.of(
                                // Can move Alice's units out of deleted red partition into their blue partition
                                contractCall(
                                        managementContractOf(TOKEN_UNDER_TEST),
                                        SAME_USER_PARTITION_MOVE_UNITS_FUNCTION.getName(),
                                        addresses.get(ALICE),
                                        addresses.get(partition(RED_PARTITION)),
                                        addresses.get(partition(BLUE_PARTITION)),
                                        FUNGIBLE_INITIAL_BALANCE),
                                // Can move Alice's units out of blue partition into unassociated green partition
                                contractCall(
                                        managementContractOf(TOKEN_UNDER_TEST),
                                        SAME_USER_PARTITION_MOVE_UNITS_FUNCTION.getName(),
                                        addresses.get(ALICE),
                                        addresses.get(partition(RED_PARTITION)),
                                        addresses.get(partition(GREEN_PARTITION)),
                                        2 * FUNGIBLE_INITIAL_BALANCE),
                                // Cannot move Alice's units to Bob without Alice's signature
                                contractCall(
                                                managementContractOf(TOKEN_UNDER_TEST),
                                                DIFFERENT_USER_PARTITION_MOVE_UNITS_FUNCTION.getName(),
                                                addresses.get(ALICE),
                                                addresses.get(partition(GREEN_PARTITION)),
                                                addresses.get(BOB),
                                                addresses.get(partition(GREEN_PARTITION)),
                                                2 * FUNGIBLE_INITIAL_BALANCE)
                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                                // But CAN move contract-managed Carol's units to Bob
                                contractCall(
                                        managementContractOf(TOKEN_UNDER_TEST),
                                        DIFFERENT_USER_PARTITION_MOVE_UNITS_FUNCTION.getName(),
                                        addresses.get(CAROL),
                                        addresses.get(partition(BLUE_PARTITION)),
                                        addresses.get(BOB),
                                        addresses.get(partition(BLUE_PARTITION)),
                                        FUNGIBLE_INITIAL_BALANCE))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
