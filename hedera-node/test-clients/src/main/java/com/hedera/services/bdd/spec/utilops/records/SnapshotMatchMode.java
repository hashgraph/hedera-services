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

package com.hedera.services.bdd.spec.utilops.records;

/**
 * Enumerates non-default matching modes in which {@link SnapshotModeOp} fuzzy-matching can be run.
 */
public enum SnapshotMatchMode {
    /**
     * Allows for gas calculations to differ from the snapshot.
     */
    ACCEPTED_MONO_GAS_CALCULATION_DIFFERENCE,
    /**
     * Allows for non-deterministic contract call results.
     */
    NONDETERMINISTIC_CONTRACT_CALL_RESULTS,
    /**
     * Allows for non-deterministic function parameters.
     */
    NONDETERMINISTIC_FUNCTION_PARAMETERS,
    /**
     * Allows for non-deterministic constructor parameters.
     */
    NONDETERMINISTIC_CONSTRUCTOR_PARAMETERS,
    /**
     * Allows for non-deterministic amounts.
     */
    NONDETERMINISTIC_TRANSACTION_FEES,
    /**
     * Allows for non-deterministic nonce. This can happen when there is a NodeStakeUpdate transaction in the
     * mix.
     */
    NONDETERMINISTIC_NONCE,
    /**
     * Lets a spec advertise itself as being non-deterministic.
     *
     * <p>We need this to let such specs to opt out of auto record snapshots, since fuzzy-matching would never pass.
     */
    FULLY_NONDETERMINISTIC,
    /**
     * Some of the ingest checks in mono-service are moved into pureChecks or handle in modular service. So any
     * response code added in spec.streamlinedIngestChecks will not produce a record in mono-service, as it is rejected in ingest.
     * But in modular service we produce a record. This will not cause any issue for differential testing, because we test
     * transactions that have reached consensus. Use this snapshot mode to still fuzzy-match against records whose
     * receipt's status would be rejected in pre-check by mono-service.
     */
    EXPECT_STREAMLINED_INGEST_RECORDS,
    /**
     * When a transaction involving custom fees transfer fails, the fee charged for a transaction is not deterministic, because
     * of the way mono-service charges fees.This mode allows for fuzzy-matching of records that have different fees.
     */
    HIGHLY_NON_DETERMINISTIC_FEES,
    /**
     * In mono-service when a CryptoTransfer with auto-creation fails, we are re-claiming pendingAliases but not reclaiming ids.
     * So when we compare the snapshot records, we will have different ids in the transaction receipt. This mode allows for
     * fuzzy-matching of records that have different ids. Also, when auto-creation fails the charged fee to payer is not re-claimed
     * in mono-service. So the  transaction fee differs a lot.
     */
    ALLOW_SKIPPED_ENTITY_IDS,
    /**
     * Skip checks on logs that contain EVM addresses.
     */
    NONDETERMINISTIC_LOG_DATA,
    /**
     * Allows for non-deterministic ethereum data.
     */
    NONDETERMINISTIC_ETHEREUM_DATA,
    /**
     * Allows for non-deterministic token names.
     */
    NONDETERMINISTIC_TOKEN_NAMES,
    /**
     * Allows for fully deterministic matching.
     */
    FULLY_DETERMINISTIC
}
