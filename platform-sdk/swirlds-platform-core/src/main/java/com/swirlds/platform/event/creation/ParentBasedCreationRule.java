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

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.events.BaseEvent;

/**
 * Determines if an event should be created by the potential parents supplied
 */
public interface ParentBasedCreationRule {
    /**
     * @param selfParent
     * 		the potential self-parent
     * @param otherParent
     * 		the potential other-parent
     * @return the appropriate action to take
     */
    EventCreationRuleResponse shouldCreateEvent(BaseEvent selfParent, BaseEvent otherParent);
}
