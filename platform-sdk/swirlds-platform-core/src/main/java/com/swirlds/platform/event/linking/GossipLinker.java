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
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Links events to their parents. Expects events to be provided in topological order.
 * <p>
 * This implementation updates the intake event counter when an ancient event is discarded by the linker.
 */
public class GossipLinker extends InOrderLinker {

    private final IntakeEventCounter counter;

    /**
     * Constructor
     *
     * @param platformContext    the platform context
     */
    public GossipLinker(@NonNull final PlatformContext platformContext, @NonNull final IntakeEventCounter counter) {
        super(platformContext);
        this.counter = Objects.requireNonNull(counter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void ancientEventAdded(@NonNull final GossipEvent event) {
        counter.eventExitedIntakePipeline(event.getSenderId());
    }
}
