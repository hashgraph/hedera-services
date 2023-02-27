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

import static com.swirlds.logging.LogMarker.CREATE_EVENT;

import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.events.BaseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Event creation rules that are static and do not need to be instantiated
 */
public final class StaticCreationRules {
    private static final Logger logger = LogManager.getLogger(StaticCreationRules.class);

    private StaticCreationRules() {}

    /**
     * A static implementation of {@link ParentBasedCreationRule} to disallow null other-parents
     *
     * @param selfParent
     * 		a potential self-parent
     * @param otherParent
     * 		a potential other-parent
     * @return DONT_CREATE if the otherParent is null, PASS otherwise
     */
    @SuppressWarnings("unused") // selfParent is needed to conform to the ParentBasedCreationRule interface
    public static EventCreationRuleResponse nullOtherParent(final BaseEvent selfParent, final BaseEvent otherParent) {
        if (otherParent == null) {
            // we only have a null other-parent when creating a genesis event
            logger.debug(CREATE_EVENT.getMarker(), "Not creating event because otherParent is null");
            return EventCreationRuleResponse.DONT_CREATE;
        }
        return EventCreationRuleResponse.PASS;
    }
}
