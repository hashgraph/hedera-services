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

package com.swirlds.platform.sync;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.platform.EventStrings;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventAddedObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Observes events and consensus in order to update the {@link ShadowGraph}
 */
public class ShadowGraphEventObserver implements EventAddedObserver, ConsensusRoundObserver {
    private static final Logger logger = LogManager.getLogger(ShadowGraphEventObserver.class);
    private final ShadowGraph shadowGraph;

    public ShadowGraphEventObserver(final ShadowGraph shadowGraph) {
        this.shadowGraph = shadowGraph;
    }

    /**
     * Expire events in {@link ShadowGraph} based on the new minimum round generation
     *
     * @param consensusRound
     * 		a new consensus round
     */
    @Override
    public void consensusRound(final ConsensusRound consensusRound) {
        shadowGraph.expireBelow(consensusRound.getGenerations().getMinRoundGeneration());
    }

    /**
     * Add an event to the {@link ShadowGraph}
     *
     * @param event
     * 		the event to add
     */
    @Override
    public void eventAdded(final EventImpl event) {
        try {
            shadowGraph.addEvent(event);
        } catch (final ShadowGraphInsertionException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "failed to add event {} to shadow graph",
                    EventStrings.toMediumString(event),
                    e);
        }
    }
}
