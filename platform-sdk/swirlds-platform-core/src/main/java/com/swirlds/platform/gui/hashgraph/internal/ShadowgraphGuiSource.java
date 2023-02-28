/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.hashgraph.internal;

import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.sync.ShadowEvent;
import com.swirlds.platform.sync.ShadowGraph;

/**
 * A {@link HashgraphGuiSource} that retrieves events from a {@link ShadowGraph}
 */
public interface ShadowgraphGuiSource extends HashgraphGuiSource {
    @Override
    default long getMaxGeneration() {
        return getShadowGraph().getTips().stream()
                .map(ShadowEvent::getEvent)
                .max(EventUtils::generationComparator)
                .map(EventImpl::getGeneration)
                .orElse(GraphGenerations.FIRST_GENERATION);
    }

    @Override
    default PlatformEvent[] getEvents(final long startGeneration, final int numGenerations) {
        return getShadowGraph()
                .findByGeneration(startGeneration, startGeneration + numGenerations, e -> true)
                .toArray(PlatformEvent[]::new);
    }

    @Override
    default boolean isReady() {
        return getShadowGraph() != null;
    }

    ShadowGraph getShadowGraph();
}
