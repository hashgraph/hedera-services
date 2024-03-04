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

package com.swirlds.platform.eventhandling;

/**
 * The phase of the consensus round handling process.
 */
public enum ConsensusRoundHandlerPhase {
    /**
     * Nothing is happening.
     */
    IDLE,
    /**
     * The handler is waiting for the keystone event of the round to become durable before applying the contained
     * transactions to the state.
     */
    WAITING_FOR_EVENT_DURABILITY,
    /**
     * The consensus fields of the events in a round are being populated.
     */
    SETTING_EVENT_CONSENSUS_DATA,
    /**
     * The round handler is waiting for transaction prehandling to complete.
     */
    WAITING_FOR_PREHANDLE,
    /**
     * The transactions in the round are being applied to the state.
     */
    HANDLING_CONSENSUS_ROUND,
    /**
     * The platform state is being updated with results from the round.
     */
    UPDATING_PLATFORM_STATE,
    /**
     * The platform state is being updated with the running hash of the round.
     */
    UPDATING_PLATFORM_STATE_RUNNING_HASH,
    /**
     * The handler is getting the state to sign.
     */
    GETTING_STATE_TO_SIGN,
    /**
     * The handler is creating a new signed state instance.
     */
    CREATING_SIGNED_STATE
}
