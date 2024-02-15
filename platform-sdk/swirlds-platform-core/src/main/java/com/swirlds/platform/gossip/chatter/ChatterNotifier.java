/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.chatter;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;

/**
 * Transfers information from consensus to the chatter module
 */
public class ChatterNotifier {
    private final NodeId selfId;
    private final ChatterCore<GossipEvent> chatterCore;

    public ChatterNotifier(final NodeId selfId, final ChatterCore<GossipEvent> chatterCore) {
        this.selfId = selfId;
        this.chatterCore = chatterCore;
    }

    public void eventAdded(final EventImpl event) {
        if (event.isCreatedBy(selfId)) {
            chatterCore.eventCreated(event.getBaseEvent());
        } else {
            chatterCore.eventReceived(event.getBaseEvent());
        }
    }

    public void consensusRound(final ConsensusRound consensusRound) {
        chatterCore.shiftWindow(consensusRound.getGenerations().getMinRoundGeneration());
    }
}
