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

package com.swirlds.virtualmap.config;

/**
 * Various reconnect modes for virtual map nodes.
 */
public final class VirtualMapReconnectMode {

    /**
     * "Push" reconnect mode, when teacher sends requests to learner, and learner responses if it has
     * the same virtual nodes
     */
    public static final String PUSH = "push";

    /**
     * "Pull / top to bottom" reconnect mode, when learner sends requests to teacher, rank by rank
     * starting from the root of the virtual tree, and teacher responses if it has the same virtual nodes
     */
    public static final String PULL_TOP_TO_BOTTOM = "pullTopToBottom";

    /**
     * "Pull / bottom to top" reconnect mode, when learner sends requests to teacher, starting from
     * leaf parent nodes, then leaves, and teacher responses if it has the same virtual nodes
     */
    public static final String PULL_TWO_PHASE_PESSIMISTIC = "pullTwoPhasePessimistic";

    /**
     * "Pull / parallel-synchronous" reconnect mode, when learner sends request to teacher, starting
     * from leaf parent nodes, then leaves. "Synchronous" means that learner doesn't send a request
     * for the next node, until a response about the last node is received from teacher. "Parallel"
     * indicates that internal nodes are processed in chunks, each chunk is sent in this sync mode,
     * but different chunks are processed independently in parallel
     */
    public static final String PULL_PARALLEL_SYNC = "pullParallelSync";

    private VirtualMapReconnectMode() {}
}
