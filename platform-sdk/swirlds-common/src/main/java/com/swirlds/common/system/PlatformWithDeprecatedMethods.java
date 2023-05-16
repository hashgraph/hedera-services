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

package com.swirlds.common.system;

import com.swirlds.common.system.events.PlatformEvent;

/**
 * Platform methods that have been deprecated.
 *
 * @deprecated this interface is not slated for long term support, do not add new dependencies on this interface
 */
@Deprecated(forRemoval = true)
public interface PlatformWithDeprecatedMethods extends Platform {

    /**
     * Get an array of all the events in the hashgraph. This method is slow, so do not call it very often.
     * The returned array is a shallow copy, so the caller may change it, and no other threads will change
     * it. However, the events it references may have fields that are changed by other threads, and must not
     * be changed by the caller. The array will contain first the consensus events (in consensus order),
     * then the non-consensus events (sorted by time received).
     *
     * @return an array of all the events
     * @deprecated this is UI code and doesn't belong in the platform interface
     */
    @Deprecated(forRemoval = true)
    PlatformEvent[] getAllEvents();
}
