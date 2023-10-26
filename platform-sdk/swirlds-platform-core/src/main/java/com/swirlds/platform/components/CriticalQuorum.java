/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.EventAddedObserver;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * <p>
 * A critical quorum a heuristic used to select the group of nodes that, when gossiped with, have a high probability
 * of moving the network towards achieving quorum, thus allowing for consensus to proceed.
 * </p>
 *
 * <p>
 * If a node has gossiped with many other nodes recently then it is less likely to be in the critical quorum. Since
 * it has participated in the ongoing consensus, its input in the short term is unlikely to move consensus forward.
 * </p>
 *
 * <p>
 * If a node has not gossiped with many other nodes recently then it is more likely to be in the critical quorum.
 * This node has not participated in recent consensus operations, and so its input (or worded differently, its vote)
 * is likely to change (and advance) the consensus algorithm.
 * </p>
 *
 * <p>
 * The end goal of identifying the critical quorum is to prioritise gossip with nodes that are likely to move consensus
 * forward, and to deprioritise gossip with nodes that will not. There is no need for a node to gossip with another
 * node many times in a short span if that gossip is not aiding in the advancement of consensus.
 * </p>
 *
 * <p>
 * The algorithm for deriving a critical quorum is defined below. A critical quorum will always contain 1/3 or
 * more of the weight of the network (up to and including all weight in the network if the number of events
 * from each node is exactly the same).
 * </p>
 *
 * <ol>
 * <li> Start with a threshold of 0. </li>
 * <li> Count the weight of all nodes that have a number of events (in the current round) equal or less
 * than the threshold. </li>
 * <li> If the weight counted meets or exceeds 1/3 of the whole then stop. All nodes with a number of events that do not
 * exceed the threshold are considered to be part of the critical quorum. </li>
 * <li> If the weight counted is below 1/3 then increase the threshold by 1 and go to step 2. </li>
 * </ol>
 */
public interface CriticalQuorum extends EventAddedObserver {

    /**
     * Checks whether the node with the supplied id is in critical quorum based on the number of events
     * created in the most recent round.
     *
     * @param nodeId
     * 		the id of the node to check
     * @return true if it is in the critical quorum, false otherwise
     */
    boolean isInCriticalQuorum(@Nullable NodeId nodeId);

    /**
     * {@inheritDoc}
     */
    @Override
    void eventAdded(EventImpl event);
}
