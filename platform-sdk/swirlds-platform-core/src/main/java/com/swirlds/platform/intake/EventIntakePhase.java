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

package com.swirlds.platform.intake;

/**
 * The phases of event intake
 */
public enum EventIntakePhase {
    /**
     * No event intake tasks are being performed
     */
    IDLE,
    /**
     * Events are being hashed
     */
    HASHING,
    /**
     * Events are being validated
     */
    VALIDATING,
    /**
     * Pre-consensus dispatch is being performed
     */
    PRECONSENSUS_DISPATCH,
    /**
     * Events are being linked
     */
    LINKING,
    /**
     * Events are being prehandled
     * <p>
     * This phase will only arise if the node is configured to perform prehandle on the intake thread.
     */
    PREHANDLING,
    /**
     * Events are being added to the hashgraph
     */
    ADDING_TO_HASHGRAPH,
    /**
     * Rounds that reached consensus are being handled
     */
    HANDLING_CONSENSUS_ROUNDS,
    /**
     * Event-added dispatch is being performed
     */
    EVENT_ADDED_DISPATCH,
    /**
     * Stale events are being handled
     */
    HANDLING_STALE_EVENTS
}
