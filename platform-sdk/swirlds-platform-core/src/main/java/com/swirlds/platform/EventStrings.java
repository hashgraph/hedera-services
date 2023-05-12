/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.platform.event.EventStringBuilder;
import com.swirlds.platform.internal.EventImpl;

/**
 * A collection of methods for creating strings from events events.
 */
public final class EventStrings {
    private EventStrings() {}

    /**
     * A string representation of an event in the following format:
     * {@code (creatorID, generation, shortHash)}
     *
     * @param event
     * 		the event to convert to a string
     * @return A short string representation of an event
     */
    public static String toShortString(final EventImpl event) {
        return EventStringBuilder.builder(event).appendEvent().build();
    }

    /**
     * Same as {@link #toShortString(EventImpl)}
     */
    public static String toShortString(final BaseEvent event) {
        return EventStringBuilder.builder(event).appendEvent().build();
    }

    /**
     * A string representation of an event in the following format:
     * {@code (creatorID, generation, shortHash)
     * sp(creatorID, selfParentGeneration, selfParentShortHash)
     * op(otherParentID, otherParentGeneration, otherParentShortHash)}
     *
     * @param event
     * 		the event to convert to a string
     * @return A medium string representation of an event
     */
    public static String toMediumString(final EventImpl event) {
        return EventStringBuilder.builder(event)
                .appendEvent()
                .appendSelfParent()
                .appendOtherParent()
                .build();
    }

    /**
     * Same as {@link #toMediumString(EventImpl)}
     */
    public static String toMediumString(final BaseEvent event) {
        return EventStringBuilder.builder(event)
                .appendEvent()
                .appendSelfParent()
                .appendOtherParent()
                .build();
    }
}
