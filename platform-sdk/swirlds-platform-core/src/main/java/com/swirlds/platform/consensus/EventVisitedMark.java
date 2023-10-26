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

package com.swirlds.platform.consensus;

import com.swirlds.platform.internal.EventImpl;

/**
 * When searching for events in a graph, this instance is used to mark the ones that have already
 * been visited in order to avoid visiting them more than once. It works by using a different number
 * for each search.
 */
public class EventVisitedMark {
    /** the first mark used by consensus */
    private static final int FIRST_MARK = 1;
    /**
     * an event with this number is "marked", all others are "unmarked". Used by the
     * ValidAncestorsIterator
     */
    private int currMark = FIRST_MARK;

    /** Called when a new search is starting to use a new mark value */
    public void nextMark() {
        // use only odd numbers so that 0 is always skipped
        // 0 is the default mark value of an event, so we cannot use it
        currMark += 2;
    }

    /**
     * Set the mark to an arbitrary value
     *
     * @param mark the value to set it to
     */
    public void setMark(final int mark) {
        if (mark % 2 == 0) {
            throw new IllegalArgumentException("Cannot set the mark to an even number");
        }
        currMark = mark;
    }

    /**
     * Mark this event as visited
     *
     * @param event the event to mark
     */
    public void markVisited(final EventImpl event) {
        event.setMark(currMark);
    }

    /**
     * Checks if this event has not been visited
     *
     * @param event the event tot check
     * @return true if the event is not null and has been marked as visited
     */
    public boolean isNotVisited(final EventImpl event) {
        return event != null && event.getMark() != currMark;
    }
}
