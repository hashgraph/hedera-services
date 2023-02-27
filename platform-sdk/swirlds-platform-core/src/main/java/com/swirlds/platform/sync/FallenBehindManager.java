/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sync;

import com.swirlds.common.system.NodeId;
import java.util.List;

public interface FallenBehindManager {
    /**
     * Notify the fallen behind manager that a node has reported that they don't have events we need. This means we have
     * probably fallen behind and will need to reconnect
     *
     * @param id
     * 		the id of the node who says we have fallen behind
     */
    void reportFallenBehind(NodeId id);

    /**
     * We have determined that we have not fallen behind, or we have reconnected, so reset everything to the initial
     * state
     */
    void resetFallenBehind();

    /**
     * Returns a list of node IDs which need to be contacted to establish if we have fallen behind.
     *
     * @return a list of node IDs, or null if there is no indication we have fallen behind
     */
    List<Long> getNeededForFallenBehind();

    /**
     * Have enough nodes reported that they don't have events we need, and that we have fallen behind?
     *
     * @return true if we have fallen behind, false otherwise
     */
    boolean hasFallenBehind();

    /**
     * Get a list of neighbors to call if we need to do a reconnect
     *
     * @return a list of neighbor IDs
     */
    List<Long> getNeighborsForReconnect();

    /**
     * Should I attempt a reconnect with this neighbor?
     *
     * @param peerId
     * 		the ID of the neighbor
     * @return true if I should attempt a reconnect
     */
    boolean shouldReconnectFrom(Long peerId);

    /**
     * @return the number of nodes that have told us we have fallen behind
     */
    int numReportedFallenBehind();
}
