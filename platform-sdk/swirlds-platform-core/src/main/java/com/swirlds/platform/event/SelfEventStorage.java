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

package com.swirlds.platform.event;

import com.swirlds.platform.internal.EventImpl;

/**
 * Used for storing the latest event created by me
 */
public interface SelfEventStorage {
    /**
     * @return the most recent event created by me, or null if no such event exists.
     */
    EventImpl getMostRecentSelfEvent();

    /**
     * Sets the most recent self event to the supplied value
     *
     * @param selfEvent
     * 		the value to set
     */
    void setMostRecentSelfEvent(final EventImpl selfEvent);
}
