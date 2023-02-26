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

package com.swirlds.common.system;

import com.swirlds.common.UniqueId;

/**
 * The status of the Platform, indicating whether all is normal, or if it has some problem, such as being disconnected,
 * or not having the latest state.
 */
public enum PlatformStatus implements UniqueId {
    /**
     * The Platform is still starting up. This is the default state before ACTIVE
     */
    STARTING_UP(1),
    /**
     * All is normal: the Platform is running, connected, and syncing properly
     */
    ACTIVE(2),
    /**
     * The Platform is not currently connected to any other computers on the network
     */
    DISCONNECTED(3),
    /**
     * The Platform does not have the latest state, and needs to reconnect
     */
    BEHIND(4),
    /**
     * The Platform is undergoing maintenance
     */
    MAINTENANCE(5),
    /**
     * The Platform has stopped handling consensus transactions in preparation for a freeze
     */
    FREEZE_COMPLETE(6);

    /** unique ID */
    private final int id;

    /**
     * Constructs an enum instance
     *
     * @param id
     * 		unique ID of the instance
     */
    PlatformStatus(final int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }
}
