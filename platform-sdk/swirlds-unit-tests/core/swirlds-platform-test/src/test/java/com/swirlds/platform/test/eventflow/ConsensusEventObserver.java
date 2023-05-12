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

package com.swirlds.platform.test.eventflow;

import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple implementation that keeps track of all consensus events sent to it.
 */
public class ConsensusEventObserver implements ConsensusRoundObserver {

    private final List<EventImpl> consensusEvents = new ArrayList<>();

    /**
     * Announce that the given event has achieved consensus
     *
     * @param event
     * 		the event
     */
    @Override
    public void consensusRound(final ConsensusRound consensusRound) {
        consensusEvents.addAll(consensusRound.getConsensusEvents());
    }

    public List<EventImpl> getConsensusEvents() {
        return consensusEvents;
    }
}
