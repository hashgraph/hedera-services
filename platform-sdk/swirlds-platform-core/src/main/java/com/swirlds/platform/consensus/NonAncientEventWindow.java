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

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Determines the non-ancient lower bound (inclusive) on events and communicates the window of rounds between the
 * pendingConsensusRound and the minimumRoundNonAncient (inclusive).
 */
public class NonAncientEventWindow {

    private final AncientMode ancientMode;
    private final long latestConsensusRound;
    private final long minRoundNonAncient;
    private final long minGenNonAncient;

    /**
     * Create a new NonAncientEventWindow with the given bounds. The latestConsensusRound must be greater than or equal
     * to the first round of consensus.  If the minimum round non-ancient is set to a number lower than the first round
     * of consensus, the first round of consensus is used instead.  The minGenNonAncient value must be greater than or
     * equal to the first generation for events.
     *
     * @param latestConsensusRound the latest round that has come to consensus
     * @param minRoundNonAncient   the minimum round that is non-ancient
     * @param minGenNonAncient     the minimum generation that is non-ancient
     * @param ancientMode          the ancient mode
     * @throws IllegalArgumentException if the latestConsensusRound is less than the first round of consensus or if the
     *                                  minGenNonAncient value is less than the first generation for events.
     */
    public NonAncientEventWindow(
            final long latestConsensusRound,
            final long minRoundNonAncient,
            final long minGenNonAncient,
            @NonNull final AncientMode ancientMode) {
        if (latestConsensusRound < ConsensusConstants.ROUND_FIRST) {
            throw new IllegalArgumentException(
                    "The latest consensus round cannot be less than the first round of consensus.");
        }
        if (minGenNonAncient < EventConstants.FIRST_GENERATION) {
            throw new IllegalArgumentException(
                    "the minimum generation non-ancient cannot be lower than the first generation for events.");
        }
        this.latestConsensusRound = latestConsensusRound;
        this.minRoundNonAncient = Math.max(minRoundNonAncient, ConsensusConstants.ROUND_FIRST);
        this.minGenNonAncient = minGenNonAncient;
        this.ancientMode = ancientMode;
    }

    /**
     * Creates a genesis non-ancient event window for the given ancient mode.
     *
     * @param ancientMode the ancient mode to use
     * @return a genesis non-ancient event window.
     */
    @NonNull
    public static NonAncientEventWindow getGenesisNonAncientEventWindow(@NonNull final AncientMode ancientMode) {
        return new NonAncientEventWindow(
                ConsensusConstants.ROUND_FIRST,
                ConsensusConstants.ROUND_FIRST,
                EventConstants.FIRST_GENERATION,
                ancientMode);
    }

    /**
     * @return true if this is a genesis non-ancient event window, false otherwise.
     */
    public boolean isGenesis() {
        return this.latestConsensusRound == ConsensusConstants.ROUND_FIRST
                && this.minRoundNonAncient == ConsensusConstants.ROUND_FIRST
                && this.minGenNonAncient == EventConstants.FIRST_GENERATION;
    }

    /**
     * @return the ancient mode.
     */
    @NonNull
    public AncientMode getAncientMode() {
        return ancientMode;
    }

    /**
     * @return the pending round coming to consensus, i.e. 1  + the latestConsensusRound
     */
    public long pendingConsensusRound() {
        return latestConsensusRound + 1;
    }

    /**
     * @return the lower bound of the non-ancient event window.
     */
    public long getAncientThreshold() {
        if (ancientMode == AncientMode.BIRTH_ROUND_THRESHOLD) {
            return minRoundNonAncient;
        } else {
            return minGenNonAncient;
        }
    }

    /**
     * Determines if the given event is ancient.
     *
     * @param event the event to check for being ancient.
     * @return true if the event is ancient, false otherwise.
     */
    public boolean isAncient(@NonNull final GossipEvent event) {
        return event.getAncientIndicator(ancientMode) < getAncientThreshold();
    }

    /**
     * Determines if the given event is ancient.
     *
     * @param event the event to check for being ancient.
     * @return true if the event is ancient, false otherwise.
     */
    public boolean isAncient(@NonNull final EventDescriptor event) {
        return event.getAncientIndicator(ancientMode) < getAncientThreshold();
    }

    /**
     * Determines if the given long value is ancient.
     *
     * @param testValue the value to check for being ancient.
     * @return true if the value is ancient, false otherwise.
     */
    public boolean isAncient(final long testValue) {
        return testValue < getAncientThreshold();
    }

    /**
     * Create a NonAncientEventWindow by calculating the minRoundNonAncient value from the latestConsensusRound and
     * roundsNonAncient.
     *
     * @param latestConsensusRound the latest round that has come to consensus
     * @param minGenNonAncient     the minimum generation that is non-ancient
     * @param roundsNonAncient     the number of rounds that are non-ancient
     * @return the new NonAncientEventWindow
     */
    @NonNull
    public static NonAncientEventWindow createUsingRoundsNonAncient(
            final long latestConsensusRound,
            final long minGenNonAncient,
            final long roundsNonAncient,
            @NonNull final AncientMode ancientMode) {
        return new NonAncientEventWindow(
                latestConsensusRound, latestConsensusRound - roundsNonAncient + 1, minGenNonAncient, ancientMode);
    }

    @NonNull
    public static NonAncientEventWindow createUsingPlatformContext(
            final long latestConsensusRound,
            final long minGenNonAncient,
            @NonNull final PlatformContext platformContext) {
        final long roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        return createUsingRoundsNonAncient(latestConsensusRound, minGenNonAncient, roundsNonAncient, ancientMode);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("latestConsensusRound", latestConsensusRound)
                .append("minRoundNonAncient", minRoundNonAncient)
                .append("minGenNonAncient", minGenNonAncient)
                .toString();
    }
}
