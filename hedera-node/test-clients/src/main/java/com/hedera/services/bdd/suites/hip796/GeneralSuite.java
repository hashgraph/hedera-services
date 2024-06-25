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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.assertPartitionInheritedExpectedProperties;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.lockNfts;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.lockUnits;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.nonFungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partition;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.treasuryOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.LOCKING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class GeneralSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(GeneralSuite.class);

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(canCreateFungibleTokenWithLockingAndPartitioning(), canCreateNFTWithLockingAndPartitioning());
    }

    /**
     * <b>General-1</b>
     * <p>As a `token-issuer`, I want to create a fungible token definition with locking and/or partitioning
     * capabilities.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> canCreateFungibleTokenWithLockingAndPartitioning() {
        return defaultHapiSpec("CanCreateFungibleTokenWithLockingAndPartitioning")
                .given(fungibleTokenWithFeatures(PARTITIONING, LOCKING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withRelation(ALICE, r -> r.onlyForPartition(RED_PARTITION)))
                .when(lockUnits(ALICE, partition(RED_PARTITION), FUNGIBLE_INITIAL_BALANCE / 2))
                .then(
                        assertPartitionInheritedExpectedProperties(RED_PARTITION),
                        assertPartitionInheritedExpectedProperties(BLUE_PARTITION),
                        // Alice cannot transfer any of her locked balance
                        cryptoTransfer(moving(FUNGIBLE_INITIAL_BALANCE / 2, partition(RED_PARTITION))
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - use a lock-specific status check
                                .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
                        // Alice can still transfer her unlocked balance to the token treasury
                        cryptoTransfer(moving(FUNGIBLE_INITIAL_BALANCE / 2, partition(RED_PARTITION))
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    /**
     * <b>General-2</b>
     * <p>As a `token-issuer`, I want to create a non-fungible token definition with locking and/or partitioning
     * capabilities.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> canCreateNFTWithLockingAndPartitioning() {
        return defaultHapiSpec("CanCreateNFTWithLockingAndPartitioning")
                .given(nonFungibleTokenWithFeatures(PARTITIONING, LOCKING)
                        .withPartitions(RED_PARTITION, BLUE_PARTITION)
                        .withRelation(
                                ALICE, r -> r.onlyForPartition(RED_PARTITION).ownedSerialNos(1L, 2L)))
                .when(lockNfts(ALICE, RED_PARTITION, 1L))
                .then(
                        assertPartitionInheritedExpectedProperties(RED_PARTITION),
                        assertPartitionInheritedExpectedProperties(BLUE_PARTITION),
                        // Alice cannot transfer her locked NFT
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 1L)
                                        .between(ALICE, treasuryOf(TOKEN_UNDER_TEST)))
                                // FUTURE - use a lock-specific status code
                                .hasKnownStatus(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO),
                        // Alice can still transfer her unlocked NFT to the token treasury
                        cryptoTransfer(movingUnique(partition(RED_PARTITION), 2L)
                                .between(ALICE, treasuryOf(TOKEN_UNDER_TEST))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
