/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.linking;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Links events to their parents. Expects events to be provided in topological order.
 * <p>
 * This implementation updates the intake event counter when an ancient event is discarded by the linker.
 * <p>
 * Note: This class doesn't have a direct dependency on the {@link Shadowgraph ShadowGraph},
 * but it is dependent in the sense that the Shadowgraph is currently responsible for eventually unlinking events.
 */
public class GossipLinker extends AbstractInOrderLinker {

    private final IntakeEventCounter counter;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param counter         counts the number of events, per peer, that are in the intake pipeline
     */
    public GossipLinker(@NonNull final PlatformContext platformContext, @NonNull final IntakeEventCounter counter) {
        super(platformContext);
        this.counter = Objects.requireNonNull(counter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void ancientEventAdded(@NonNull final PlatformEvent event) {
        counter.eventExitedIntakePipeline(event.getSenderId());
    }
}
