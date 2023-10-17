/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An object that creates new events.
 */
public interface EventCreator {

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    void registerEvent(@NonNull EventImpl event);

    /**
     * Update the minimum generation non-ancient.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    void setMinimumGenerationNonAncient(long minimumGenerationNonAncient);

    /**
     * Create a new event if it is legal to do so. The only time this should not create an event is if there are no
     * eligible parents.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    GossipEvent maybeCreateEvent();
}
