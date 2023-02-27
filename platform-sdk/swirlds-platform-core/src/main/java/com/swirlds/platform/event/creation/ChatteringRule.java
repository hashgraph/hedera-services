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

package com.swirlds.platform.event.creation;

import com.swirlds.common.system.EventCreationRule;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import java.util.Collection;

/**
 * Prevents us from creating events unless we are chattering with a certain percentage of our neighbors
 */
public class ChatteringRule implements EventCreationRule {
    private final double chatteringThreshold;
    private final Collection<CommunicationState> chatterCommunicationStates;

    /**
     * @param chatteringThreshold
     * 		the fraction of neighbors we should be chattering with in order to create events
     * @param chatterCommunicationStates
     * 		all neighbors states of communication
     */
    public ChatteringRule(
            final double chatteringThreshold, final Collection<CommunicationState> chatterCommunicationStates) {
        if (chatteringThreshold <= 0 || chatteringThreshold > 1) {
            throw new IllegalStateException(
                    "chatteringThreshold should be: '0 <= cht < 1', supplied value: " + chatteringThreshold);
        }
        this.chatteringThreshold = chatteringThreshold;
        this.chatterCommunicationStates = chatterCommunicationStates;
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent() {
        final long totalNeighbors = chatterCommunicationStates.size();
        final long numChattering = chatterCommunicationStates.stream()
                .filter(CommunicationState::isChattering)
                .count();
        if (numChattering >= chatteringThreshold * totalNeighbors) {
            return EventCreationRuleResponse.PASS;
        }
        return EventCreationRuleResponse.DONT_CREATE;
    }
}
