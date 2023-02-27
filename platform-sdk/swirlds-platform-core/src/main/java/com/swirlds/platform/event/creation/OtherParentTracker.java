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

package com.swirlds.platform.event.creation;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.events.BaseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which events were used as other parents and prevents us from using the same other parent twice. It assumes the
 * creator will only use newer other parents and only tracks the most recent event by creator ID.
 */
public class OtherParentTracker implements ParentBasedCreationRule {
    private final Map<Long, Hash> otherParentUsed;

    public OtherParentTracker() {
        this.otherParentUsed = new HashMap<>();
    }

    /**
     * Track the other parent used
     *
     * @param event
     * 		the newly created event
     */
    public void track(final BaseEvent event) {
        if (event.getHashedData().hasOtherParent()) {
            otherParentUsed.put(
                    event.getUnhashedData().getOtherId(), event.getHashedData().getOtherParentHash());
        }
    }

    @Override
    public EventCreationRuleResponse shouldCreateEvent(final BaseEvent selfParent, final BaseEvent otherParent) {
        if (otherParent == null) {
            return EventCreationRuleResponse.PASS;
        }
        // We don't want to create multiple events with the same other parent, so we have to check if we
        // already created an event with this particular other parent.
        final Hash lastParentUsed =
                otherParentUsed.get(otherParent.getHashedData().getCreatorId());
        if (lastParentUsed != null
                && lastParentUsed.equals(otherParent.getHashedData().getHash())) {
            return EventCreationRuleResponse.DONT_CREATE;
        }
        return EventCreationRuleResponse.PASS;
    }
}
