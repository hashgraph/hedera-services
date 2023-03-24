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
import java.time.Instant;

/**
 * Platform methods that have been deprecated.
 *
 * @deprecated this interface is not slated for long term support, do not add new dependencies on this interface
 */
@Deprecated(forRemoval = true)
public interface PlatformWithDeprecatedMethods extends Platform {

    /**
     * Set the number of milliseconds the Platform should delay after each gossip sync it initiates. This is
     * zero by default, but can be changed to slow down the system. This can be useful for testing.
     *
     * @param delay
     * 		the delay in milliseconds
     * @deprecated this has no effect when chatter is enabled
     */
    @Deprecated(forRemoval = true)
    void setSleepAfterSync(final long delay);

    /**
     * Get command line arguments.
     *
     * @deprecated use configuration engine
     */
    @Deprecated(forRemoval = true)
    String[] getParameters();

    /**
     * Get the ID of the current swirld. A given app can be used to create many different swirlds (also
     * called networks, or ledgers, or shared worlds). This is a unique identifier for this particular
     * swirld.
     *
     * @return a copy of the swirld ID
     * @deprecated this is only used by GUI code, will be removed after inversion of control
     */
    @Deprecated
    byte[] getSwirldId();

    /**
     * Get the latest mutable state. This method is not thread safe. use at your own risk.
     *
     * @deprecated this workflow is not thread safe
     */
    @Deprecated(forRemoval = true)
    <T extends SwirldState> T getState();

    /**
     * Should be called after {@link #getState()}.
     *
     * @deprecated this workflow is not thread safe
     */
    @Deprecated(forRemoval = true)
    void releaseState();

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

    /**
     * @return consensusTimestamp of the last signed state
     * @deprecated this method doesn't belong in the platform
     */
    @Deprecated(forRemoval = true)
    Instant getLastSignedStateTimestamp();
}
