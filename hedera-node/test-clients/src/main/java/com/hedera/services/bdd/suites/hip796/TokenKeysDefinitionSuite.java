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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withKeyValuesFor;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.LOCK_KEY_TYPE;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.PARTITION_KEY_TYPE;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.PARTITION_MOVE_KEY_TYPE;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.REMOVE_KEY_FUNCTION;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.ROTATE_KEY_FUNCTION;
import static com.hedera.services.bdd.suites.hip796.Hip796Verbs.fungibleTokenWithFeatures;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.lockKeyOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.managementContractOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partitionKeyOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenAttributeNames.partitionMoveKeyOf;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.ADMIN_CONTROL;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.INTER_PARTITION_MANAGEMENT;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.LOCKING;
import static com.hedera.services.bdd.suites.hip796.operations.TokenFeature.PARTITIONING;

import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

/**
 * A suite for user stories Keys-1 through Keys-4 from HIP-796.
 */
public class TokenKeysDefinitionSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenKeysDefinitionSuite.class);

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                manageLockKeyCapabilities(),
                managePartitionKeyCapabilities(),
                managePartitionMoveKeyCapabilities(),
                manageKeysViaSmartContract());
    }

    /**
     * <b>Keys-1</b>
     * <p>As a `token-administrator`, I want to administer (set, rotate/update, or remove)
     * a `lock-key` on the `token-definition`
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> manageLockKeyCapabilities() {
        return defaultHapiSpec("ManageLockKeyCapabilities")
                .given(fungibleTokenWithFeatures(ADMIN_CONTROL, LOCKING), newKeyNamed("newLockKey"))
                .when(
                        getTokenInfo(TOKEN_UNDER_TEST).hasLockKey(lockKeyOf(TOKEN_UNDER_TEST)),
                        // We can rotate the lock key
                        tokenUpdate(TOKEN_UNDER_TEST).lockKey("newLockKey"),
                        getTokenInfo(TOKEN_UNDER_TEST).hasLockKey("newLockKey"))
                .then(
                        // And remove it entirely
                        tokenUpdate(TOKEN_UNDER_TEST).removingRoles(LOCKING),
                        getTokenInfo(TOKEN_UNDER_TEST).hasNoneOfRoles(LOCKING));
    }

    /**
     * <b>Keys-2</b>
     * <p>As a `token-administrator`, I want to administer (set, rotate/update, or remove) a
     * `partition-key` on the `token-definition`
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> managePartitionKeyCapabilities() {
        return defaultHapiSpec("ManagePartitionKeyCapabilities")
                .given(fungibleTokenWithFeatures(ADMIN_CONTROL, PARTITIONING), newKeyNamed("newPartitionKey"))
                .when(
                        getTokenInfo(TOKEN_UNDER_TEST).hasPartitionKey(partitionKeyOf(TOKEN_UNDER_TEST)),
                        // We can rotate the partition key
                        tokenUpdate(TOKEN_UNDER_TEST).partitionKey("newPartitionKey"),
                        getTokenInfo(TOKEN_UNDER_TEST).hasPartitionKey("newPartitionKey"))
                .then(
                        // And remove it entirely
                        tokenUpdate(TOKEN_UNDER_TEST).removingRoles(PARTITIONING),
                        getTokenInfo(TOKEN_UNDER_TEST).hasNoneOfRoles(PARTITIONING));
    }

    /**
     * <b>Keys-3</b>
     * <p>As a `token-administrator`, I want to administer (set, rotate/update, or remove) a
     * `partition-move-key` on the `token-definition`.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> managePartitionMoveKeyCapabilities() {
        return defaultHapiSpec("ManagePartitionMoveKeyCapabilities")
                .given(
                        fungibleTokenWithFeatures(ADMIN_CONTROL, INTER_PARTITION_MANAGEMENT),
                        newKeyNamed("newPartitionMoveKey"))
                .when(
                        getTokenInfo(TOKEN_UNDER_TEST).hasPartitionMoveKey(partitionMoveKeyOf(TOKEN_UNDER_TEST)),
                        // We can rotate the partition key
                        tokenUpdate(TOKEN_UNDER_TEST).partitionMoveKey("newPartitionMoveKey"),
                        getTokenInfo(TOKEN_UNDER_TEST).hasPartitionMoveKey("newPartitionMoveKey"))
                .then(
                        // And remove it entirely
                        tokenUpdate(TOKEN_UNDER_TEST).removingRoles(INTER_PARTITION_MANAGEMENT),
                        getTokenInfo(TOKEN_UNDER_TEST).hasNoneOfRoles(INTER_PARTITION_MANAGEMENT));
    }

    /**
     * <b>Keys-4</b>
     * <p>As a `token-administrator` smart contract, I want to administer each of the
     * above-mentioned keys.
     *
     * @return the HapiSpec for this HIP-796 user story
     */
    final Stream<DynamicTest> manageKeysViaSmartContract() {
        return defaultHapiSpec("ManageKeysViaSmartContract")
                .given(
                        fungibleTokenWithFeatures(ADMIN_CONTROL, LOCKING, PARTITIONING, INTER_PARTITION_MANAGEMENT)
                                .managedByContract(),
                        newKeyNamed("newLockKey"),
                        newKeyNamed("newPartitionKey"),
                        newKeyNamed("newPartitionMoveKey"))
                .when(
                        // The contract can rotate keys
                        withKeyValuesFor(
                                List.of("newLockKey", "newPartitionKey", "newPartitionMoveKey"),
                                keyValues -> List.of(
                                        contractCall(
                                                managementContractOf(TOKEN_UNDER_TEST),
                                                ROTATE_KEY_FUNCTION.getName(),
                                                LOCK_KEY_TYPE,
                                                keyValues.get("newLockKey")),
                                        contractCall(
                                                managementContractOf(TOKEN_UNDER_TEST),
                                                ROTATE_KEY_FUNCTION.getName(),
                                                PARTITION_KEY_TYPE,
                                                keyValues.get("newPartitionKey")),
                                        contractCall(
                                                managementContractOf(TOKEN_UNDER_TEST),
                                                ROTATE_KEY_FUNCTION.getName(),
                                                PARTITION_MOVE_KEY_TYPE,
                                                keyValues.get("newPartitionMoveKey")))),
                        getTokenInfo(TOKEN_UNDER_TEST).hasLockKey("newLockKey"),
                        getTokenInfo(TOKEN_UNDER_TEST).hasPartitionKey("newPartitionKey"),
                        getTokenInfo(TOKEN_UNDER_TEST).hasPartitionMoveKey("newPartitionMoveKey"))
                .then(
                        // And the contract can remove them
                        contractCall(
                                managementContractOf(TOKEN_UNDER_TEST), REMOVE_KEY_FUNCTION.getName(), LOCK_KEY_TYPE),
                        contractCall(
                                managementContractOf(TOKEN_UNDER_TEST),
                                REMOVE_KEY_FUNCTION.getName(),
                                PARTITION_KEY_TYPE),
                        contractCall(
                                managementContractOf(TOKEN_UNDER_TEST),
                                REMOVE_KEY_FUNCTION.getName(),
                                PARTITION_MOVE_KEY_TYPE),
                        getTokenInfo(TOKEN_UNDER_TEST)
                                .hasNoneOfRoles(LOCKING, PARTITIONING, INTER_PARTITION_MANAGEMENT));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
