/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.UniqueId;

/**
 * The status of the Platform
 * <p>
 * NOTE: not all of these statuses can currently be reached. The status state machine is still under development. In
 * such cases, the documentation will indicate that the status is "not in use"
 *
 */
public enum PlatformStatus implements UniqueId {
    /**
     * The platform is starting up.
     */
    STARTING_UP(1),
    /**
     * The platform is gossiping, creating events, and accepting app transactions.
     */
    ACTIVE(2),
    /**
     * The platform is not currently connected to any other computers on the network.
     * <p>
     * NOTE: This is still in use, but will be retired once the status state machine is complete.
     */
    DISCONNECTED(3),
    /**
     * The Platform does not have the latest state, and needs to reconnect. The platform is not gossiping.
     */
    BEHIND(4),
    /**
     * A freeze timestamp has been crossed, and the platform is in the process of freezing. The platform is gossiping
     * and creating events, but not accepting app transactions.
     */
    FREEZING(5),
    /**
     * The platform has been frozen, and is idle.
     */
    FREEZE_COMPLETE(6),
    /**
     * The platform is replaying events from the preconsensus event stream.
     * <p>
     * NOTE: not in use
     */
    REPLAYING_EVENTS(7),
    /**
     * The platform has just started, and is observing the network. The platform is gossiping, but will not create
     * events.
     * <p>
     * NOTE: not in use
     */
    OBSERVING(8),
    /**
     * The platform has started up or has finished reconnecting, and is now ready to rejoin the network. The platform is
     * gossiping and creating events, but not yet accepting app transactions.
     * <p>
     * NOTE: not in use
     */
    CHECKING(9),
    /**
     * The platform has just finished reconnecting. The platform is gossiping, but is waiting to write a state to disk
     * before creating events or accepting app transactions.
     * <p>
     * NOTE: not in use
     */
    RECONNECT_COMPLETE(10),
    /**
     * The platform has encountered a failure, and is unable to continue. The platform is idle.
     * <p>
     * NOTE: not in use
     */
    CATASTROPHIC_FAILURE(11);

    /**
     * Unique ID of the enum value
     */
    private final int id;

    /**
     * Constructs an enum instance
     *
     * @param id unique ID of the instance
     */
    PlatformStatus(final int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }
}
