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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.lockUnits;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.unlockUnits;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.LOCKING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

/**
 * A suite for user stories Transfers-1 through Transfers-3 from HIP-796.
 */
public class TransfersSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TransfersSuite.class);

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of();
    }

    /**
     * <b>Transfer-1</b>
     * <p>As an owner of an account with a partition, I want to transfer tokens to another user with the same partition.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> canTransferTokensToSamePartitionUser() {
        return defaultHapiSpec("CanTransferTokensToSamePartitionUser")
                .given(fungibleTokenWithFeatures(PARTITIONING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION))
                        .withRelation(BOB, r -> r.onlyForPartition(BLUE_PARTITION)))
                .when(cryptoTransfer(moving(123L, partition(RED_PARTITION)).between(ALICE, BOB)))
                .then(getAccountBalance(BOB)
                        .hasTokenBalance(partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE + 123L));
    }

    /**
     * <b>Transfer-2</b>
     * <p>As an owner of an account with a partition, I want to transfer tokens to another user that does not
     * already have the same partition, but can have the same partition auto-associated.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> canTransferTokensToUserWithAutoAssociation() {
        return defaultHapiSpec("CanTransferTokensToUserWithAutoAssociation")
                .given(fungibleTokenWithFeatures(PARTITIONING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION))
                        .withRelation(BOB, r -> r.onlyForPartition(RED_PARTITION)))
                .when(
                        // Note the higher fee to cover the auto-association
                        cryptoTransfer(moving(123L, partition(RED_PARTITION))
                                        .betweenWithPartitionChange(ALICE, BOB, partition(BLUE_PARTITION)))
                                .fee(ONE_HBAR))
                .then(getAccountBalance(BOB).hasTokenBalance(partition(BLUE_PARTITION), 123L));
    }

    /**
     * <b>Transfer-3</b>
     * <P>As an owner of an account with a partition with locked tokens, I want to transfer tokens to another user
     * with the same partition, either new (with auto-association) or existing. This cannot be done atomically
     * at this time. The tokens must be unlocked, transferred, and then locked again. Using HIP-551 (atomic batch
     * transactions), I would be able to unlock, transfer, and lock atomically. This has to be done in coordination
     * with the `lock-key` holder.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> canTransferTokensToUserAfterUnlock() {
        return defaultHapiSpec("CanTransferTokensToUserPostUnlock")
                .given(fungibleTokenWithFeatures(PARTITIONING, LOCKING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withRelation(
                                ALICE, r -> r.onlyForPartition(RED_PARTITION).locked())
                        .withRelation(BOB, r -> r.onlyForPartition(RED_PARTITION)))
                .when(
                        // Nothing can be transfer when Alice's balance is locked
                        cryptoTransfer(moving(123L, partition(RED_PARTITION))
                                        .betweenWithPartitionChange(ALICE, BOB, partition(BLUE_PARTITION)))
                                // FUTURE - change to a lock-specific status code
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS),
                        unlockUnits(ALICE, partition(RED_PARTITION), 123L),
                        // But now the 123 units can be transferred
                        cryptoTransfer(moving(123L, partition(RED_PARTITION))
                                .betweenWithPartitionChange(ALICE, BOB, partition(BLUE_PARTITION))),
                        // And re-locked in Bob's account
                        lockUnits(BOB, partition(BLUE_PARTITION), 123L))
                .then(
                        // So with the help of the lock key we have repositioned these 123 units
                        cryptoTransfer(moving(123L, partition(BLUE_PARTITION)).between(BOB, ALICE))
                                // FUTURE - change to a lock-specific status code
                                .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
