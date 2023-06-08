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

package com.swirlds.common.system.platformstatus;

/**
 * An enum representing things that can occur which may have an impact on the {@link PlatformStatus}
 */
public enum PlatformStatusAction {
    /**
     * Replay of events from the PreconsensusEventStream has started.
     */
    STARTED_REPLAYING_EVENTS,
    /**
     * Replay of events from the PreconsensusEventStream has finished.
     */
    DONE_REPLAYING_EVENTS,
    /**
     * An own event was observed reaching consensus.
     */
    OWN_EVENT_REACHED_CONSENSUS,
    /**
     * A freeze timestamp has been crossed.
     */
    FREEZE_PERIOD_ENTERED,
    /**
     * The node has fallen behind.
     */
    FALLEN_BEHIND,
    /**
     * A reconnect is complete.
     */
    RECONNECT_COMPLETE,
    /**
     * A state has been written to disk.
     */
    STATE_WRITTEN_TO_DISK,
    /**
     * Something has happened that the platform can't recover from.
     */
    CATASTROPHIC_FAILURE,
    /**
     * An amount of time has elapsed.
     */
    TIME_ELAPSED
}
