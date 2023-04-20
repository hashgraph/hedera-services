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

package com.swirlds.platform.uptime;

import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.common.units.TimeUnit.UNIT_SECONDS;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors the uptime of nodes in the network.
 */
public class UptimeDetector { // TODO test

    private final long selfId;
    private final Time time;
    private final AddressBook addressBook;

    private final UptimeMetrics uptimeMetrics;
    private final Duration degradationThreshold;

    private final AtomicReference<Instant> lastEventTime = new AtomicReference<>();

    /**
     * Construct a new uptime detector.
     *
     * @param platformContext the platform context
     * @param time            the time
     * @param addressBook     the address book
     */
    public UptimeDetector(
            @NonNull PlatformContext platformContext,
            final AddressBook addressBook,
            final long selfId,
            @NonNull final Time time) {

        this.selfId = selfId;
        this.time = Objects.requireNonNull(time);
        this.addressBook = addressBook;
        this.degradationThreshold = platformContext
                .getConfiguration()
                .getConfigData(UptimeConfig.class)
                .degradationThreshold();
        this.uptimeMetrics = new UptimeMetrics(platformContext.getMetrics(), addressBook, this::isDegraded);
    }

    /**
     * Look at the events in a round to determine which nodes are up and which nodes are down.
     *
     * @param round      the round to analyze
     * @param uptimeData the uptime data that is in the current round's state, is modified by this method
     */
    public void handleRound(@NonNull final Round round, @NonNull final UptimeData uptimeData) {
        pruneRemovedNodes(addressBook, uptimeData);
        if (round.isEmpty()) {
            return;
        }

        final Map<Long, ConsensusEvent> lastEventsInRoundByCreator = new HashMap<>();
        final Map<Long, ConsensusEvent> judgesByCreator = new HashMap<>();

        scanRound(round, lastEventsInRoundByCreator, judgesByCreator);
        updateState(uptimeData, lastEventsInRoundByCreator, judgesByCreator);
        reportUptime(uptimeData, ((ConsensusRound) round).getLastEvent().getConsensusTimestamp());
    }

    /**
     * Check if this node should consider itself to be degraded.
     *
     * @return true if this node should consider itself to be degraded
     */
    public boolean isDegraded() {
        final Instant lastEventTime = this.lastEventTime.get();
        if (lastEventTime == null) {
            // Consider a node to be degraded until it has its first event reach consensus.
            return true;
        }

        final Instant now = time.now();
        final Duration durationSinceLastEvent = Duration.between(lastEventTime, now);
        return CompareTo.isGreaterThan(durationSinceLastEvent, degradationThreshold);
    }

    // TODO add other methods to query uptime data

    /**
     * Remove nodes that are no longer in the address book from the tracked uptime data.
     *
     * @param addressBook the address book
     * @param uptimeData  the uptime data
     */
    private static void pruneRemovedNodes(final AddressBook addressBook, final UptimeData uptimeData) {
        // TODO
    }

    /**
     * Scan all events in the round.
     *
     * @param round                      the round
     * @param lastEventsInRoundByCreator the last event in the round by creator, is updated by this method
     * @param judgesByCreator            the judges by creator, is updated by this method
     */
    private void scanRound(
            @NonNull final Round round,
            @NonNull final Map<Long, ConsensusEvent> lastEventsInRoundByCreator,
            Map<Long, ConsensusEvent> judgesByCreator) {
        // Scan all events in the round
        round.forEach(event -> {
            lastEventsInRoundByCreator.put(event.getCreatorId(), event);
            // TODO is it enough just to check fame?
            if (((EventImpl) event).isWitness() && ((EventImpl) event).isFamous()) {
                judgesByCreator.put(event.getCreatorId(), event);
            }
        });

        final ConsensusEvent lastSelfEvent = lastEventsInRoundByCreator.get(selfId);
        if (lastSelfEvent != null) {
            lastEventTime.set(lastSelfEvent.getConsensusTimestamp());
        }
    }

    /**
     * Update the uptime data based on the events in this round.
     *
     * @param uptimeData                 the uptime data to be updated
     * @param lastEventsInRoundByCreator the last event in the round by creator
     * @param judgesByCreator            the judges by creator
     */
    private void updateState(
            @NonNull final UptimeData uptimeData,
            @NonNull final Map<Long, ConsensusEvent> lastEventsInRoundByCreator,
            @NonNull final Map<Long, ConsensusEvent> judgesByCreator) {
        for (final Address address : addressBook) {
            final ConsensusEvent lastEvent = lastEventsInRoundByCreator.get(address.getId());
            if (lastEvent != null) {
                uptimeData.getLastConsensusEventTimes().put(address.getId(), lastEvent.getConsensusTimestamp());
            }

            final ConsensusEvent judge = judgesByCreator.get(address.getId());
            if (judge != null) {
                uptimeData.getLastJudgeTimes().put(address.getId(), judge.getConsensusTimestamp());
            }

            uptimeData
                    .getLastConsensusEventTimes()
                    .put(
                            address.getId(),
                            lastEventsInRoundByCreator.get(address.getId()).getConsensusTimestamp());
        }
    }

    /**
     * Report the uptime data.
     *
     * @param uptimeData the uptime data
     */
    private void reportUptime(@NonNull final UptimeData uptimeData, final Instant lastRoundEndTime) {
        for (final Address address : addressBook) {
            final long id = address.getId();

            long nonDegradedConsensusWeight = 0;

            final Instant lastConsensusEventTime =
                    uptimeData.getLastConsensusEventTimes().get(id);
            if (lastConsensusEventTime != null) {
                final Duration timeSinceLastConsensusEvent = Duration.between(lastConsensusEventTime, lastRoundEndTime);
                uptimeMetrics
                        .getTimeSinceLastConsensusEventMetric(id)
                        .update(UNIT_MILLISECONDS.convertTo(timeSinceLastConsensusEvent.toMillis(), UNIT_SECONDS));

                if (CompareTo.isLessThanOrEqualTo(timeSinceLastConsensusEvent, degradationThreshold)) {
                    nonDegradedConsensusWeight += addressBook.getAddress(id).getWeight();
                }
            }

            final double fractionOfNetworkAlive = (double) nonDegradedConsensusWeight / addressBook.getTotalWeight();
            uptimeMetrics.getFractionOfNetworkAliveMetric().update(fractionOfNetworkAlive);

            final Instant lastJudgeTime = uptimeData.getLastJudgeTimes().get(id);
            if (lastJudgeTime != null) {
                final Duration timeSinceLastJudge = Duration.between(lastJudgeTime, lastRoundEndTime);
                uptimeMetrics
                        .getTimeSinceLastJudgeMetric(id)
                        .update(UNIT_MILLISECONDS.convertTo(timeSinceLastJudge.toMillis(), UNIT_SECONDS));
            }
        }
    }
}
