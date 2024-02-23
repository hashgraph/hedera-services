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

package com.swirlds.platform.event.creation.rules;

import com.swirlds.platform.event.creation.EventCreationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An object used to limit or prevent the creation of new events.
 */
public interface EventCreationRule {

    /**
     * Check if event creation is currently permitted.
     *
     * @return true if event creation is permitted, false otherwise
     */
    boolean isEventCreationPermitted();

    /**
     * This method is called whenever an event is created.
     */
    void eventWasCreated();

    /**
     * If event creation is blocked by this rule, this method should return the status that the event creator should
     * adopt.
     *
     * @return the status that the event creator should adopt if this rule blocks event creation
     */
    @NonNull
    EventCreationStatus getEventCreationStatus();
}
