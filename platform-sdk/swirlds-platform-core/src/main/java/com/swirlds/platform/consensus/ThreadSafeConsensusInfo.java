/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.state.MinimumJudgeInfo;
import com.swirlds.platform.system.events.EventConstants;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * All information provided by {@link com.swirlds.platform.Consensus} that needs to be accessed at any time by any
 * thread.
 */
public class ThreadSafeConsensusInfo implements RoundNumberProvider {
    private static final Logger LOG = LogManager.getLogger(ThreadSafeConsensusInfo.class);

    protected final ConsensusConfig config;
    private final SequentialRingBuffer<MinimumJudgeInfo> storage;

    /** the minimum generation of all the judges that are not ancient */
    private long minGenNonAncient = EventConstants.FIRST_GENERATION;

    /** fame has been decided for all rounds less than this, but not for this round. */
    private long fameDecidedBelow = ConsensusConstants.ROUND_FIRST;

    /**
     * @param platformContext platform context
     */
    public ThreadSafeConsensusInfo(@NonNull final PlatformContext platformContext) {
        final Configuration config = platformContext.getConfiguration();
        this.config = config.getConfigData(ConsensusConfig.class);
        this.storage = new SequentialRingBuffer<>(
                ConsensusConstants.ROUND_FIRST,
                config.getConfigData(ConsensusConfig.class).roundsExpired() * 2);
    }

    /**
     * @return the instance that stores all round and generation information
     */
    protected @NonNull SequentialRingBuffer<MinimumJudgeInfo> getStorage() {
        return storage;
    }

    /**
     * Update all round and generation information.
     *
     * @param fameDecidedBelow the latest round which has not had its fame decided
     */
    protected void updateRoundGenerations(final long fameDecidedBelow) {
        this.fameDecidedBelow = fameDecidedBelow;

        if (fameDecidedBelow == ConsensusConstants.ROUND_FIRST) {
            // if there are no rounds, set the defaults
            minGenNonAncient = EventConstants.FIRST_GENERATION;
            return;
        }

        updateMinGenNonAncient();
    }

    /**
     * Update the oldest non-ancient round generation
     *
     * <p>Executed only on consensus thread.
     */
    private void updateMinGenNonAncient() {
        final long nonAncientRound =
                RoundCalculationUtils.getOldestNonAncientRound(config.roundsNonAncient(), getLastRoundDecided());
        final MinimumJudgeInfo info = storage.get(nonAncientRound);
        if (info == null) {
            // should never happen
            LOG.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "nonAncientRound({}) is null in updateMinGenNonAncient()",
                    nonAncientRound);
            return;
        }
        minGenNonAncient = info.minimumJudgeAncientThreshold();
    }

    /**
     * Return the minimum generation of all the famous witnesses that are not in ancient rounds.
     *
     * <p>Define gen(R) to be the minimum generation of all the events that were famous witnesses in
     * round R.
     *
     * <p>If round R is the most recent round for which we have decided the fame of all the
     * witnesses, then any event with a generation less than gen(R - {@code Settings.state.roundsExpired}) is called an
     * “expired” event. And any non-expired event with a generation less than gen(R -
     * {@code Settings.state.roundsNonAncient} + 1) is an “ancient” event. If the event failed to achieve consensus
     * before becoming ancient, then it is “stale”. So every non-expired event with a generation before gen(R -
     * {@code Settings.state.roundsNonAncient} + 1) is either stale or consensus, not both.
     *
     * <p>Expired events can be removed from memory unless they are needed for an old signed state
     * that is still being used for something (such as still in the process of being written to disk).
     *
     * @return The minimum generation of all the judges that are not ancient. If no judges are ancient, returns
     * {@link EventConstants#FIRST_GENERATION}.
     */
    public long getMinGenerationNonAncient() {
        return minGenNonAncient;
    }

    /**
     * @return The minimum judge generation number from the oldest non-expired round, if we have expired any rounds.
     * Else this returns {@link EventConstants#FIRST_GENERATION}.
     */
    public long getMinRoundGeneration() {
        final MinimumJudgeInfo info = storage.get(getMinRound());
        return info == null ? EventConstants.FIRST_GENERATION : info.minimumJudgeAncientThreshold();
    }

    @Override
    public long getFameDecidedBelow() {
        return fameDecidedBelow;
    }

    @Override
    public long getMaxRound() {
        return storage.maxIndex();
    }

    @Override
    public long getMinRound() {
        return storage.minIndex();
    }

    /**
     * Checks if the supplied event is ancient or not. An event is ancient if its generation is smaller than the round
     * generation of the oldest non-ancient round.
     *
     * @param event the event to check
     * @return true if the event is ancient, false otherwise
     */
    public boolean isAncient(@NonNull final PlatformEvent event) {
        return event.getGeneration() < getMinGenerationNonAncient();
    }
}
