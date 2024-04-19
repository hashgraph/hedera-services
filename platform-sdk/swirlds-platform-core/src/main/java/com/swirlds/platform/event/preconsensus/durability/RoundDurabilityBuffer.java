/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.preconsensus.durability;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;

/**
 * This component performs a logical "join" operation between states ready to be handled and events that are known to be
 * durable via the PCES. This allows us to wait to handle the transactions within a round until we can guarantee that
 * all events required for that round to reach consensus have been made durable on disk.
 */
public interface RoundDurabilityBuffer {

    /**
     * Specify the latest durable event, by sequence number.
     *
     * @param durableSequenceNumber the sequence number of the event (as assigned by the
     *                              {@link com.swirlds.platform.event.preconsensus.PcesSequencer}) of the latest event
     *                              that can be guaranteed to be durable
     * @return a list of zero or more rounds that are safe to handle from an event durability perspective
     */
    @InputWireLabel("durable event info")
    @NonNull
    List<ConsensusRound> setLatestDurableSequenceNumber(@NonNull Long durableSequenceNumber);

    /**
     * Provide the next round that has reached consensus.
     *
     * @param round the round that has reached consensus
     * @return a list of zero or more rounds that are safe to handle from an event durability perspective
     */
    @InputWireLabel("rounds")
    @NonNull
    List<ConsensusRound> addRound(@NonNull ConsensusRound round);

    /**
     * Clear all internal data, resetting this object to its initial state.
     */
    void clear();

    /**
     * Check for rounds that have been stuck for too long. This method takes no action other than logging.
     *
     * @param now the current time
     */
    @InputWireLabel("heartbeat")
    void checkForStaleRounds(@NonNull Instant now);
}
