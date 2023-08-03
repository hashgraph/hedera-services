/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
 * The reason for writing the state to disk
 */
public enum StateToDiskReason {
    /**
     * The state was written to disk because the platform is starting up without a previous saved state
     */
    FIRST_ROUND_AFTER_GENESIS,
    /**
     * The state was written to disk because it is a freeze state
     */
    FREEZE_STATE,
    /**
     * The state was written to disk because it is time to take a periodic snapshot
     */
    PERIODIC_SNAPSHOT,
    /**
     * The state was written to disk because it is a reconnect state
     */
    RECONNECT,
    /**
     * The state was written to disk because an ISS was detected
     */
    ISS,
    /**
     * The state was written to disk because a fatal error was encountered
     */
    FATAL_ERROR;
}
