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

package com.swirlds.platform.consensus;

import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.PlatformEvent;

public interface GraphGenerations {
    long FIRST_GENERATION = 0;

    /**
     * @return The minimum judge generation number from the most recent fame-decided round, if there is one.
     * 		Else this returns {@link #FIRST_GENERATION}.
     */
    long getMaxRoundGeneration();

    /**
     * @return The minimum generation of all the judges that are not ancient. If no judges are ancient, returns
     *        {@link #FIRST_GENERATION}.
     */
    long getMinGenerationNonAncient();

    /**
     * @return The minimum judge generation number from the oldest non-expired round, if we have expired any rounds.
     * 		Else this returns {@link #FIRST_GENERATION}.
     */
    long getMinRoundGeneration();

    /**
     * Checks if we have any ancient rounds yet. For a short period after genesis, there will be no ancient rounds.
     * After that, this will always return true.
     *
     * @return true if there are any ancient events, false otherwise
     */
    default boolean areAnyEventsAncient() {
        return getMinGenerationNonAncient() > FIRST_GENERATION;
    }

    /**
     * Checks if the supplied event is ancient or not. An event is ancient if its generation is smaller than the round
     * generation of the oldest non-ancient round.
     *
     * @param event
     * 		the event to check
     * @return true if its ancient, false otherwise
     */
    default boolean isAncient(final PlatformEvent event) {
        return event.getGeneration() < getMinGenerationNonAncient();
    }

    /**
     * Same as {@link #isAncient(PlatformEvent)} but for {@link BaseEvent}
     */
    default boolean isAncient(final BaseEvent event) {
        return event.getHashedData().getGeneration() < getMinGenerationNonAncient();
    }

    /**
     * Checks if the supplied event is expired
     *
     * @param event
     * 		the event to check
     * @return true if it is expired, false if its not
     */
    default boolean isExpired(final BaseEvent event) {
        return event.getHashedData().getGeneration() < getMinRoundGeneration();
    }
}
