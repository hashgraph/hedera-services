/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import com.swirlds.platform.state.MinimumJudgeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * All information provided by {@link com.swirlds.platform.Consensus} that needs to be accessed at any time by any
 * thread.
 */
public class ThreadSafeConsensusInfo implements GraphGenerations, RoundNumberProvider {
    private static final Logger LOG = LogManager.getLogger(ThreadSafeConsensusInfo.class);

    protected final ConsensusConfig config;
    private final SequentialRingBuffer<MinimumJudgeInfo> storage;

    /**
     * The minimum judge generation number from the oldest non-expired round, if we have expired any rounds. Else, this
     * is {@link GraphGenerations#FIRST_GENERATION}.
     *
     * <p>Updated only on consensus thread, read concurrently from gossip threads.
     */
    private volatile long minRoundGeneration = GraphGenerations.FIRST_GENERATION;

    /** the minimum generation of all the judges that are not ancient */
    private volatile long minGenNonAncient = GraphGenerations.FIRST_GENERATION;

    /**
     * The minimum judge generation number from the most recent fame-decided round, if there is one. Else, this is
     * {@link GraphGenerations#FIRST_GENERATION}.
     *
     * <p>Updated only on consensus thread, read concurrently from gossip threads.
     */
    private volatile long maxRoundGeneration = GraphGenerations.FIRST_GENERATION;

    /** fame has been decided for all rounds less than this, but not for this round. */
    private volatile long fameDecidedBelow = ConsensusConstants.ROUND_FIRST;

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
            maxRoundGeneration = GraphGenerations.FIRST_GENERATION;
            minGenNonAncient = GraphGenerations.FIRST_GENERATION;
            minRoundGeneration = GraphGenerations.FIRST_GENERATION;
            return;
        }

        updateMaxRoundGeneration();
        updateMinGenNonAncient();
        updateMinRoundGeneration();
    }

    /**
     * Update the max round generation
     *
     * <p>Executed only on consensus thread.
     */
    private void updateMaxRoundGeneration() {
        final MinimumJudgeInfo info = storage.get(getLastRoundDecided());
        if (info == null) {
            // this should never happen
            LOG.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "maxRound({}) is null in updateMaxRoundGeneration()",
                    getLastRoundDecided());
            return;
        }
        long newMaxRoundGeneration = info.minimumJudgeAncientThreshold();

        // Guarantee that the round generation is non-decreasing.
        // Once we remove support for states with events, this can be removed
        newMaxRoundGeneration = Math.max(newMaxRoundGeneration, maxRoundGeneration);

        maxRoundGeneration = newMaxRoundGeneration;
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

    /** Update the min round judge generation. Executed only on consensus thread. */
    private void updateMinRoundGeneration() {
        final MinimumJudgeInfo info = storage.get(getMinRound());
        if (info == null) {
            // this should never happen
            LOG.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "minRound({}) is null in updateMinRoundGeneration()",
                    getMinRound());
            return;
        }

        long newMinRoundGeneration = info.minimumJudgeAncientThreshold();

        // Guarantee that the round generation is non-decreasing.
        // Once we remove support for states with events, this can be removed
        newMinRoundGeneration = Math.max(newMinRoundGeneration, minRoundGeneration);

        minRoundGeneration = newMinRoundGeneration;
    }

    @Override
    public long getMaxRoundGeneration() {
        return maxRoundGeneration;
    }

    @Override
    public long getMinGenerationNonAncient() {
        return minGenNonAncient;
    }

    @Override
    public long getMinRoundGeneration() {
        return minRoundGeneration;
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
}
