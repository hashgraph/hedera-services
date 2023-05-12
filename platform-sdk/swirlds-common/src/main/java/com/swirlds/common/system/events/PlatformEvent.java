/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.events;

import com.swirlds.common.crypto.Hash;
import java.time.Instant;

/**
 * @deprecated This interface will be removed at a later date. Use {@link Event} or {@link ConsensusEvent} instead.
 */
@Deprecated
public interface PlatformEvent extends ConsensusEvent {
    /**
     * Whether this is a witness event or not. True if either this event's round &gt; selfParent's
     * round, or there is no self parent.
     *
     * @return boolean value to tell whether this event is a witness or not
     */
    boolean isWitness();

    /**
     * Tells whether this event is a witness and the fame election is over or not.
     *
     * @return True means this event is a witness and the fame election is over
     */
    boolean isFameDecided();

    /**
     * Is this event both a witness and famous?
     *
     * @return True means this event is both a witness and famous
     */
    boolean isFamous();

    /**
     * Is this event part of the consensus order?
     *
     * @return True means this is part of consensus order
     */
    boolean isConsensus();

    /**
     * The community's consensus timestamp for this event if a consensus is reached. Otherwise.
     * it will be an estimate.
     *
     * @return the consensus timestamp
     */
    Instant getConsensusTimestamp();

    /**
     * ID of otherParent. -1 if otherParent doesn't exist.
     *
     * @return Other parent event's ID
     */
    long getOtherId();

    /**
     * This event's parent event. null if none exists.
     *
     * @return The parent event of this event
     */
    PlatformEvent getSelfParent();

    /**
     * The other parent event of this event. null if other parent doesn't exist.
     *
     * @return The other parent event
     */
    PlatformEvent getOtherParent();

    /**
     * This event's generation, which is 1 plus max of parents' generations.
     *
     * @return This event's generation
     */
    long getGeneration();

    /**
     * The created round of this event, which is the max of parents' created around, plus either 0 or 1.
     *
     * @return The round number this event is created
     */
    long getRoundCreated();

    /**
     * ID of this event's creator.
     *
     * @return ID of this event's creator
     */
    long getCreatorId();

    /**
     * If isConsensus is true, the round where all unique famous witnesses see this event.
     *
     * @return the round number as described above
     */
    long getRoundReceived();

    /**
     * if isConsensus is true,  the order of this in history (0 first), else -1
     *
     * @return consensusOrder the consensus order sequence number
     */
    long getConsensusOrder();

    /**
     * @return The hash instance of the hashed base event data.
     */
    Hash getBaseHash();
}
