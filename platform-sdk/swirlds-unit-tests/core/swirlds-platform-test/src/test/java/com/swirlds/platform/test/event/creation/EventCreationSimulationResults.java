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

package com.swirlds.platform.test.event.creation;

import com.swirlds.common.utility.DurationUtils;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;
import java.util.Collection;

/**
 * Stores results of a event creation simulation
 *
 * @param numEventsCreated
 * 		the number of events created in total
 * @param numConsEvents
 * 		the number of events that reached consensus
 * @param maxC2C
 * 		the maximum C2C of all consensus events, null if none reached consensus
 * @param avgC2C
 * 		the average C2C of all consensus events, null if none reached consensus
 * @param maxRoundSize
 * 		the maximum round size of all consensus rounds, null if none reached consensus
 * @param avgRoundSize
 * 		the average round size of all consensus rounds, null if none reached consensus
 */
public record EventCreationSimulationResults(
        int numEventsCreated,
        int numConsEvents,
        Duration maxC2C,
        Duration avgC2C,
        Integer maxRoundSize,
        Double avgRoundSize) {
    public static EventCreationSimulationResults calculateResults(
            final int numEventsCreated, final Collection<ConsensusRound> consensusRounds) {
        int numConsEvents = 0;
        Duration maxC2Ctmp = Duration.ZERO;
        Duration sumC2Ctmp = Duration.ZERO;
        int maxRoundTmp = -1;
        int sumRoundSize = 0;

        for (final ConsensusRound consensusRound : consensusRounds) {
            maxRoundTmp = Math.max(maxRoundTmp, consensusRound.getNumEvents());
            sumRoundSize += consensusRound.getNumEvents();

            for (final EventImpl event : consensusRound.getConsensusEvents()) {
                final Duration c2c = Duration.between(event.getTimeCreated(), event.getReachedConsTimestamp());
                maxC2Ctmp = DurationUtils.max(maxC2Ctmp, c2c);
                sumC2Ctmp = sumC2Ctmp.plus(c2c);
                numConsEvents++;
            }
        }

        final Duration maxC2C = maxC2Ctmp == Duration.ZERO ? null : maxC2Ctmp;
        final Duration avgC2C = sumC2Ctmp == Duration.ZERO ? null : sumC2Ctmp.dividedBy(numConsEvents);
        final Integer maxRoundSize = maxRoundTmp < 0 ? null : maxRoundTmp;
        final Double avgRoundSize =
                consensusRounds.size() == 0 ? null : ((double) sumRoundSize) / consensusRounds.size();

        return new EventCreationSimulationResults(
                numEventsCreated, numConsEvents, maxC2C, avgC2C, maxRoundSize, avgRoundSize);
    }

    public void printResults() {
        System.out.println("num events created = " + numEventsCreated);
        System.out.println("num consensus events = " + numConsEvents);
        System.out.println("maxC2C = "
                + (maxC2C == null ? "N/A" : String.format("%.3f seconds", ((double) maxC2C.toMillis()) / 1000)));
        System.out.println("avgC2C = "
                + (avgC2C == null ? "N/A" : String.format("%.3f seconds", ((double) avgC2C.toMillis()) / 1000)));
        System.out.println("maxRoundSize = " + (maxRoundSize == null ? "N/A" : maxRoundSize));
        System.out.println("avgRoundSize = " + (avgRoundSize == null ? "N/A" : avgRoundSize));
    }
}
