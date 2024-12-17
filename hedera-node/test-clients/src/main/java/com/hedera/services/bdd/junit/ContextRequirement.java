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

package com.hedera.services.bdd.junit;

/**
 * Enumerates reasons a {@link LeakyHapiTest} cannot be run concurrently with other tests.
 */
public enum ContextRequirement {
    /**
     * The test expects a predictable relationship between its created entity numbers,
     * meaning that it will fail if other tests are concurrently creating entities.
     */
    NO_CONCURRENT_CREATIONS,
    /**
     * The test requires that its transactions are guaranteed to be the first in a
     * staking period.
     */
    NO_CONCURRENT_STAKE_PERIOD_BOUNDARY_CROSSINGS,
    /**
     * The test requires changes to the network properties, which might break other
     * concurrent tests if they expect the default properties.
     */
    PROPERTY_OVERRIDES,
    /**
     * The test requires changes to the network permissions, which might break other
     * concurrent tests if they expect the default permissions.
     */
    PERMISSION_OVERRIDES,
    /**
     * The test requires changes to the network throttle definitions, which might break
     * other concurrent tests if they expect the default throttles.
     */
    THROTTLE_OVERRIDES,
    /**
     * The test requires changes to the network fee schedules, which might break
     * other concurrent tests if they expect the default fees.
     */
    FEE_SCHEDULE_OVERRIDES,
    /**
     * The test requires the upgrade files to be in a specific state, which could
     * be violated by other concurrent tests.
     */
    UPGRADE_FILE_CONTENT,
    /**
     * The test depends on system account balances being affected exclusively by its
     * own operations, and not by those of other concurrent tests.
     */
    SYSTEM_ACCOUNT_BALANCES,
    /**
     * The test depends on system account keys being affected exclusively by its
     * own operations, and not by those of other concurrent tests.
     */
    SYSTEM_ACCOUNT_KEYS
}
