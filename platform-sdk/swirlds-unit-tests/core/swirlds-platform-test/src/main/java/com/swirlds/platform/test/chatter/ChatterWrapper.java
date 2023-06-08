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

package com.swirlds.platform.test.chatter;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.sequence.Shiftable;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.gossip.chatter.protocol.ChatterCore;
import com.swirlds.platform.gossip.chatter.protocol.PeerMessageException;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ChatterWrapper implements SimulatedChatter {
    final ChatterCore<ChatterEvent> core;
    final List<Shiftable> shiftables;

    public ChatterWrapper(final ChatterCore<ChatterEvent> core, final List<Shiftable> shiftables) {
        this.core = core;
        this.shiftables = shiftables;
    }

    @Override
    public void newEvent(final ChatterEvent event) {
        core.eventCreated(event);
    }

    @Override
    public void handlePayload(@NonNull final SelfSerializable payload, @NonNull final NodeId sender) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(sender, "sender must not be null");
        try {
            core.getPeerInstance(sender).inputHandler().handleMessage(payload);
        } catch (final PeerMessageException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Nullable
    public GossipPayload generatePayload(
            @NonNull final Instant now, final boolean underutilizedNetwork, @NonNull final NodeId destination) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        final SelfSerializable message =
                core.getPeerInstance(destination).outputAggregator().getMessage();
        if (message == null) {
            return null;
        }
        return new GossipPayload(message, destination);
    }

    @Override
    public void shiftWindow(final long firstSequenceNumberInWindow) {
        for (final Shiftable shiftable : shiftables) {
            shiftable.shiftWindow(firstSequenceNumberInWindow);
        }
    }
}
