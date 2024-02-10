/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.simulated;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An interface for test classes that handle gossiped messages
 */
public interface GossipMessageHandler {

    /**
     * Handle a message received from a peer over the network. This is the entry point to the gossip code.
     *
     * @param msg      the message received
     * @param fromPeer the peer who sent the message
     */
    void handleMessageFromWire(@NonNull final SelfSerializable msg, @NonNull final NodeId fromPeer);

    /**
     * Get the node id of this handler
     *
     * @return the node id
     */
    @NonNull
    NodeId getNodeId();
}
