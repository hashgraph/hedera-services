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

import static com.swirlds.logging.LogMarker.CREATE_EVENT;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.consensus.GraphGenerations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Prevents us from creating events with ancient parents which will almost certainly be ancient themselves
 */
public class AncientParentsRule implements ParentBasedCreationRule {
    private static final Logger logger = LogManager.getLogger(AncientParentsRule.class);
    /**
     * Supplies the key generation number from the hashgraph
     */
    private final GraphGenerations graphGenerations;

    public AncientParentsRule(final GraphGenerations graphGenerations) {
        this.graphGenerations = graphGenerations;
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent(final BaseEvent selfParent, final BaseEvent otherParent) {
        // Don't create an event if both parents are old.
        if (areBothParentsAncient(selfParent, otherParent)) {
            logger.debug(
                    CREATE_EVENT.getMarker(),
                    "Both parents are ancient, selfParent: {}, otherParent: {}",
                    () -> EventStrings.toShortString(selfParent),
                    () -> EventStrings.toShortString(otherParent));
            return EventCreationRuleResponse.DONT_CREATE;
        }
        return EventCreationRuleResponse.PASS;
    }

    /**
     * Check if both parents are ancient
     *
     * @param selfParent
     * 		the self-parent event
     * @param otherParent
     * 		the other-parent event
     * @return true iff both parents are ancient
     */
    public boolean areBothParentsAncient(final BaseEvent selfParent, final BaseEvent otherParent) {
        // This is introduced as a fix for problems seen while recovering from the mainnet hardware
        // crash on 3 June 2019. Then, 3 out of 10 nodes went offline (due to hardware problems) and had only
        // old events in the state. When the nodes started back up,
        // nodes that previously crashed synced with other nodes that crashed. This created events where both
        // parents are old, and these events could not be entered into the hashgraph on the nodes that created
        // them, and they were not gossipped out.
        // Update 15 November 2021: The code has since changed and creating ancient events no longer breaks the system.
        // But it does not make sense create an ancient event, so this rule is still in place.
        if (!graphGenerations.areAnyEventsAncient()) {
            // if there are no ancient event yet, we return immediately. This is to account for genesis, where both
            // parents will be null
            return false;
        }
        // if a parent is null, it's the same as if it were ancient
        return (selfParent == null || graphGenerations.isAncient(selfParent))
                && (otherParent == null || graphGenerations.isAncient(otherParent));
    }
}
