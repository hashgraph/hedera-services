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

package com.swirlds.platform.consensus;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Determines the window of rounds between the pending consensus round and the minimum round non-ancient. Provides the
 * following information:
 * <ol>
 *     <li> latestConsensusRound - the latest round to have come to consensus.</li>
 *     <li> minRoundNonAncient - the ancient threshold based on event birth round</li>
 *     <li> minGenNonAncient - the ancient threshold based on event generation</li>
 *     <li> pendingConsensusRound - the current round coming to consensus, i.e. 1  + the latestConsensusRound</li>
 * </ol>
 * <p>
 * FUTURE WORK: Remove minGenNonAncient once we throw the switch to using minRoundNonAncient as the ancient threshold.
 */
public record NonAncientEventWindow(long latestConsensusRound, long minRoundNonAncient, long minGenNonAncient) {

    /**
     * @return the pending round coming to consensus, i.e. 1  + the latestConsensusRound
     */
    public long pendingConsensusRound() {
        return latestConsensusRound + 1;
    }

    /**
     * Determines if the given event is ancient.
     *
     * @param event the event to check for being ancient.
     * @return true if the event is ancient, false otherwise.
     */
    public boolean isAncient(GossipEvent event) {
        // use minimum generation non-ancient until we throw the switch to using minimum round non-ancient
        return event.getGeneration() < minGenNonAncient;
    }

    public static NonAncientEventWindow create(
            final long latestConsensusRound,
            final long minGenNonAncient,
            @NonNull final PlatformContext platformContext) {
        return new NonAncientEventWindow(
                latestConsensusRound,
                latestConsensusRound
                        - platformContext
                                .getConfiguration()
                                .getConfigData(ConsensusConfig.class)
                                .roundsNonAncient()
                        + 1,
                minGenNonAncient);
    }
}
