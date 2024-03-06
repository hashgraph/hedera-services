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

package com.swirlds.platform.state.signed;

/**
 * Fields written to the signed state metadata file.
 */
public enum SavedStateMetadataField {
    /**
     * The round of the signed state.
     */
    ROUND,
    /**
     * The root hash of the state.
     */
    HASH,
    /**
     * The root hash of the state in mnemonic form.
     */
    HASH_MNEMONIC,
    /**
     * The number of consensus events, starting from genesis, that have been handled to create this state.
     */
    NUMBER_OF_CONSENSUS_EVENTS,
    /**
     * The consensus timestamp of this state.
     */
    CONSENSUS_TIMESTAMP,
    /**
     * The running hash of all events, starting from genesis, that have been handled to create this state.
     */
    RUNNING_EVENT_HASH,
    /**
     * The running hash of all events, starting from genesis, that have been handled to create this state, in mnemonic
     * form.
     */
    RUNNING_EVENT_HASH_MNEMONIC,
    /**
     * The minimum generation of non-ancient events after this state reached consensus. Future work: this needs to be
     * migrated once we have switched to {@link com.swirlds.platform.event.AncientMode#BIRTH_ROUND_THRESHOLD}.
     */
    MINIMUM_GENERATION_NON_ANCIENT,
    /**
     * The application software version that created this state.
     */
    SOFTWARE_VERSION,
    /**
     * The wall clock time when this state was written to disk.
     */
    WALL_CLOCK_TIME,
    /**
     * The ID of the node that wrote this state to disk.
     */
    NODE_ID,
    /**
     * A comma separated list of node IDs that signed this state.
     */
    SIGNING_NODES,
    /**
     * The sum of all signing nodes' weights.
     */
    SIGNING_WEIGHT_SUM,
    /**
     * The total weight of all nodes in the network.
     */
    TOTAL_WEIGHT,
    /**
     * The epoch hash of the state. Used by emergency recovery protocols.
     */
    EPOCH_HASH,
    /**
     * The epoch hash of the state in mnemonic form.
     */
    EPOCH_HASH_MNEMONIC
}
