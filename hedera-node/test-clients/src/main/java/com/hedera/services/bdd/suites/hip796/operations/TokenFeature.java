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

package com.hedera.services.bdd.suites.hip796.operations;

/**
 * Enumerates the features (or "roles") that a token can possess.
 */
public enum TokenFeature {
    /**
     * The token can be updated or deleted.
     */
    ADMIN_CONTROL,
    /**
     * The token's custom fees can be updated.
     */
    CUSTOM_FEE_SCHEDULE_MANAGEMENT,
    /**
     * Account balances of the token can be frozen or unfrozen.
     */
    FREEZING,
    /**
     * Account balances of the token can redistributed between partitions without the account's signature.
     */
    INTER_PARTITION_MANAGEMENT,
    /**
     * Account KYC status of the token can be changed.
     */
    KYC_MANAGEMENT,
    /**
     * Account balances of the token can be locked and unlocked.
     */
    LOCKING,
    /**
     * The token can be partitioned.
     */
    PARTITIONING,
    /**
     * The token can be paused and unpaused.
     */
    PAUSING,
    /**
     * The token's supply can be minted and burned.
     */
    SUPPLY_MANAGEMENT,
    /**
     * Account balances of the token can be wiped (set to zero) without the account's signature.
     */
    WIPING,
}
