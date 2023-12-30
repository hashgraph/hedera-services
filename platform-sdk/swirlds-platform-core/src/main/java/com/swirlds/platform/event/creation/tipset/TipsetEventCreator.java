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

package com.swirlds.platform.event.creation.tipset;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.event.creation.tipset.TipsetUtils.getParentDescriptors;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.creation.EventCreator;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for creating new events using the tipset algorithm.
 */
public class TipsetEventCreator implements EventCreator {

    private static final Logger logger = LogManager.getLogger(TipsetEventCreator.class);

    private final Cryptography cryptography;
    private final Time time;
    private final Random random;
    private final Signer signer;
    private final NodeId selfId;
    private final TipsetTracker tipsetTracker;
    private final TipsetWeightCalculator tipsetWeightCalculator;
    private final ChildlessEventTracker childlessOtherEventTracker;
    private final TransactionSupplier transactionSupplier;
    private final SoftwareVersion softwareVersion;
    private NonAncientEventWindow nonAncientEventWindow = NonAncientEventWindow.INITIAL_EVENT_WINDOW;

    /**
     * The maximum number of other parents that an event is permitted to have.
     */
    private final int maximumOtherParentCount;

    /**
     * The address book for the current network.
     */
    private final AddressBook addressBook;

    /**
     * The size of the current network.
     */
    private final int networkSize;

    /**
     * The selfishness score is divided by this number to get the probability of creating an event that reduces the
     * selfishness score. The higher this number is, the lower the probability is that an event will be created that
     * reduces the selfishness score.
     */
    private final double antiSelfishnessFactor;

    /**
     * Metrics for the tipset algorithm.
     */
    private final TipsetMetrics tipsetMetrics;

    /**
     * The last event created by this node.
     */
    private EventDescriptor lastSelfEvent;

    /**
     * The timestamp of the last event created by this node.
     */
    private Instant lastSelfEventCreationTime;

    /**
     * The number of transactions in the last event created by this node.
     */
    private int lastSelfEventTransactionCount;

    private final RateLimitedLogger zeroAdvancementWeightLogger;
    private final RateLimitedLogger noParentFoundLogger;

    /**
     * Create a new tipset event creator.
     *
     * @param platformContext     the platform context
     * @param time                provides wall clock time
     * @param random              a source of randomness, does not need to be cryptographically secure
     * @param signer              used for signing things with this node's private key
     * @param addressBook         the current address book
     * @param selfId              this node's ID
     * @param softwareVersion     the current software version of the application
     * @param transactionSupplier provides transactions to be included in new events
     */
    public TipsetEventCreator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final Random random,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final TransactionSupplier transactionSupplier) {

        this.time = Objects.requireNonNull(time);
        this.random = Objects.requireNonNull(random);
        this.signer = Objects.requireNonNull(signer);
        this.selfId = Objects.requireNonNull(selfId);
        this.transactionSupplier = Objects.requireNonNull(transactionSupplier);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
        this.addressBook = Objects.requireNonNull(addressBook);

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        cryptography = platformContext.getCryptography();
        antiSelfishnessFactor = Math.max(1.0, eventCreationConfig.antiSelfishnessFactor());
        tipsetMetrics = new TipsetMetrics(platformContext, addressBook);
        tipsetTracker = new TipsetTracker(time, addressBook);
        childlessOtherEventTracker = new ChildlessEventTracker();
        tipsetWeightCalculator = new TipsetWeightCalculator(
                platformContext, time, addressBook, selfId, tipsetTracker, childlessOtherEventTracker);
        networkSize = addressBook.getSize();

        zeroAdvancementWeightLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
        noParentFoundLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
        maximumOtherParentCount = eventCreationConfig.maxOtherParentCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerEvent(@NonNull final GossipEvent event) {
        if (nonAncientEventWindow.isAncient(event)) {
            return;
        }

        final NodeId eventCreator = event.getHashedData().getCreatorId();
        if (!addressBook.contains(eventCreator)) {
            return;
        }
        final boolean selfEvent = eventCreator.equals(selfId);

        if (selfEvent) {
            if (lastSelfEvent == null || lastSelfEvent.getGeneration() < event.getGeneration()) {
                // Normally we will ingest self events before we get to this point, but it's possible
                // to learn of self events for the first time here if we are loading from a restart or reconnect.
                lastSelfEvent = event.getDescriptor();
                lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
                lastSelfEventTransactionCount = event.getHashedData().getTransactions() == null
                        ? 0
                        : event.getHashedData().getTransactions().length;
                childlessOtherEventTracker.registerSelfEventParents(
                        event.getHashedData().getOtherParents());
            } else {
                // We already ingested this self event (when it was created),
                return;
            }
        }

        final EventDescriptor descriptor = event.getDescriptor();
        final List<EventDescriptor> parentDescriptors = getParentDescriptors(event);

        tipsetTracker.addEvent(descriptor, parentDescriptors);

        if (!selfEvent) {
            childlessOtherEventTracker.addEvent(descriptor, parentDescriptors);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        this.nonAncientEventWindow = Objects.requireNonNull(nonAncientEventWindow);
        tipsetTracker.setNonAncientEventWindow(nonAncientEventWindow);
        childlessOtherEventTracker.pruneOldEvents(nonAncientEventWindow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public GossipEvent maybeCreateEvent() {
        if (networkSize == 1) {
            // Special case: network of size 1.
            // We can always create a new event, no need to run the tipset algorithm.
            return createEventForSizeOneNetwork();
        }

        final List<EventDescriptor> otherParents = chooseOtherParents();

        if (otherParents.isEmpty()) {
            // If there are no available other parents, it is only legal to create a new event if we are
            // creating a genesis event. In order to create a genesis event, we must have never created
            // an event before and the current non-ancient event window must have never been advanced.
            if (nonAncientEventWindow != NonAncientEventWindow.INITIAL_EVENT_WINDOW || lastSelfEvent != null) {
                // event creation isn't legal
                return null;
            }

            // we are creating a genesis event with no parents
            return buildAndProcessEvent(List.of(), List.of());
        }

        final List<EventDescriptor> allParents = new ArrayList<>(otherParents.size() + 1);
        if (lastSelfEvent != null) {
            allParents.add(lastSelfEvent);
        }
        allParents.addAll(otherParents);

        // TODO we can avoid this recomputation
        final TipsetAdvancementWeight advancementWeight =
                tipsetWeightCalculator.getTheoreticalAdvancementWeight(allParents);

        if (advancementWeight.isNonZero()) {
            return buildAndProcessEvent(allParents, otherParents);
        } else {
            return null;
        }
    }

    /**
     * Create the next event for a network of size 1 (i.e. where we are the only member). We don't use the tipset
     * algorithm like normal, since we will never have a real other parent.
     *
     * @return the new event
     */
    private GossipEvent createEventForSizeOneNetwork() {
        // There is a quirk in size 1 networks where we can only
        // reach consensus if the self parent is also the other parent.
        // Unexpected, but harmless. So just use the same event
        // as both parents until that issue is resolved.
        return buildAndProcessEvent(List.of(lastSelfEvent), List.of(lastSelfEvent));
    }

    /**
     * Choose the other parents for the next event.
     *
     * @return the other parents
     */
    @NonNull
    private List<EventDescriptor> chooseOtherParents() {
        // TODO make the base class return a set!
        final Set<EventDescriptor> possibleOtherParents =
                new HashSet<>(childlessOtherEventTracker.getChildlessEvents());

        if (maximumOtherParentCount <= 0 || possibleOtherParents.size() < maximumOtherParentCount) {
            // If there are fewer than the maximum number of other parents, we can use all of them.
            return new ArrayList<>(possibleOtherParents);
        }

        final List<EventDescriptor> chosenParents = new ArrayList<>();

        // Possibly choose a pity parent to reduce selfishness.
        final EventDescriptor pityParent = chooseParentToReduceSelfishness(possibleOtherParents);
        if (pityParent != null) {
            chosenParents.add(pityParent);
            possibleOtherParents.remove(pityParent);
        }

        // Choose events that best improve the tipset advancement score.
        while (chosenParents.size() < maximumOtherParentCount) {
            final EventDescriptor bestParent =
                    chooseParentWithBestAdvancementScore(chosenParents, possibleOtherParents);
            if (bestParent == null) {
                break;
            }
            chosenParents.add(bestParent);
            possibleOtherParents.remove(bestParent);
        }

        // If there is remaining capacity, fill it with whatever is available. These will be events that
        // do not improve the tipset advancement score. But in general, it's helpful to include as many
        // parents as possible.
        final int remainingCapacity = maximumOtherParentCount - chosenParents.size();
        if (remainingCapacity > 0) {
            final List<EventDescriptor> remainingChoices = new ArrayList<>(possibleOtherParents);
            Collections.shuffle(remainingChoices, random);
            for (int i = 0; i < remainingCapacity; i++) {
                chosenParents.add(remainingChoices.get(i));
            }
        }

        // TODO sort this so that we always return parents in the same order!
        return new ArrayList<>(chosenParents);
    }

    /**
     * If we are selfishly ignoring a node, once in a while we should create an event that reduces our selfishness. This
     * will prevent low weight nodes from being ignored by the network.
     *
     * @param possibleOtherParents the possible other parents
     * @return a pity parent to include as an other parent, or null if no pity parent should be included
     */
    @Nullable
    private EventDescriptor chooseParentToReduceSelfishness(@NonNull final Set<EventDescriptor> possibleOtherParents) {

        final long selfishness = tipsetWeightCalculator.getMaxSelfishnessScore();
        tipsetMetrics.getSelfishnessMetric().update(selfishness);

        // Never bother with anti-selfishness techniques if we have a selfishness score of 1.
        // We are pretty much guaranteed to be selfish to ~1/3 of other nodes by a score of 1.
        final double beNiceChance = (selfishness - 1) / antiSelfishnessFactor;

        if (beNiceChance <= 0 || random.nextDouble() >= beNiceChance) {
            // Now is not the time to be nice.
            return null;
        }

        // Choose a random ignored node, weighted by how much it is currently being ignored.

        // First, figure out who is an ignored node and sum up all selfishness scores.
        int selfishnessSum = 0;
        final List<Integer> selfishnessScores = new ArrayList<>(possibleOtherParents.size());
        final List<EventDescriptor> ignoredNodes = new ArrayList<>(possibleOtherParents.size());
        for (final EventDescriptor possibleIgnoredNode : possibleOtherParents) {
            final int selfishnessForNode =
                    tipsetWeightCalculator.getSelfishnessScoreForNode(possibleIgnoredNode.getCreator());

            final List<EventDescriptor> theoreticalParents = new ArrayList<>(2);
            theoreticalParents.add(possibleIgnoredNode);
            if (lastSelfEvent == null) {
                throw new IllegalStateException("lastSelfEvent is null");
            }
            theoreticalParents.add(lastSelfEvent);

            final TipsetAdvancementWeight advancementWeight =
                    tipsetWeightCalculator.getTheoreticalAdvancementWeight(theoreticalParents);

            if (selfishnessForNode > 1) {
                if (advancementWeight.isNonZero()) {
                    ignoredNodes.add(possibleIgnoredNode);
                    selfishnessScores.add(selfishnessForNode);
                    selfishnessSum += selfishness;
                } else {
                    // Note: if selfishness score is greater than 1, it is mathematically not possible
                    // for the advancement score to be zero. But in the interest in extreme caution,
                    // we check anyway, since it is very important never to create events with
                    // an advancement score of zero.
                    zeroAdvancementWeightLogger.error(
                            EXCEPTION.getMarker(),
                            "selfishness score is {} but advancement score is zero for {}.\n{}",
                            selfishness,
                            possibleIgnoredNode,
                            this);
                }
            }
        }

        if (ignoredNodes.isEmpty()) {
            // Note: this should be impossible, since we will not enter this method in the first
            // place if there are no ignored nodes. But better to be safe than sorry, and returning null
            // is an acceptable way of saying "I can't create an event right now".
            noParentFoundLogger.error(
                    EXCEPTION.getMarker(), "failed to locate eligible ignored node to use as a parent");
            return null;
        }

        // Choose a random ignored node.
        final int choice = random.nextInt(selfishnessSum);
        int runningSum = 0;
        for (int i = 0; i < ignoredNodes.size(); i++) {
            runningSum += selfishnessScores.get(i);
            if (choice < runningSum) {
                final EventDescriptor ignoredNode = ignoredNodes.get(i);
                tipsetMetrics.getPityParentMetric(ignoredNode.getCreator()).cycle();

                return ignoredNode;
            }
        }

        // This should be impossible.
        throw new IllegalStateException("Failed to find an other parent");
    }

    /**
     * Choose an event that should be used as an other parent. The event with the best advancement score is chosen. If
     * there are multiple events with the same advancement score, the first one is chosen. If there are no events with a
     * positive advancement score, null is returned.
     *
     * @param chosenParents        the current other parents that have been chosen
     * @param possibleOtherParents the possible other parents
     * @return the chosen other parent, or null if no other parent should be chosen
     */
    @Nullable
    private EventDescriptor chooseParentWithBestAdvancementScore(
            @NonNull final List<EventDescriptor> chosenParents,
            @NonNull final Set<EventDescriptor> possibleOtherParents) {
        EventDescriptor bestOtherParent = null;

        // TODO can we avoid recomputing this?

        final List<EventDescriptor> parents = new ArrayList<>(chosenParents.size() + 2);
        if (lastSelfEvent != null) {
            parents.add(lastSelfEvent);
        }
        parents.addAll(chosenParents);

        TipsetAdvancementWeight bestAdvancementWeight = tipsetWeightCalculator.getTheoreticalAdvancementWeight(parents);

        // Possible parents will swapped into the end of this list as we iterate.
        parents.add(null);
        final int newParentIndex = parents.size() - 1;

        for (final EventDescriptor possibleParent : possibleOtherParents) {
            parents.set(newParentIndex, possibleParent);

            // TODO there may be a way to do this more cheaply.
            final TipsetAdvancementWeight advancementWeight =
                    tipsetWeightCalculator.getTheoreticalAdvancementWeight(parents);

            // TODO find a way to break ties randomly... don't reward based on position in iteration order
            if (advancementWeight.isGreaterThan(bestAdvancementWeight)) {
                bestOtherParent = possibleParent;
                bestAdvancementWeight = advancementWeight;
            }
        }

        return bestOtherParent;
    }

    /**
     * Given an other parent, build the next self event and process it.
     *
     * @param allParents   all parents (including the self parent)
     * @param otherParents the other parents (excluding the self parent)
     * @return the new event
     */
    @NonNull
    private GossipEvent buildAndProcessEvent(
            @NonNull final List<EventDescriptor> allParents, @NonNull final List<EventDescriptor> otherParents) {

        tipsetMetrics.getAverageOtherParentCountMetric().update(otherParents.size());

        final GossipEvent event = assembleEventObject(otherParents);

        final EventDescriptor descriptor = event.getDescriptor();
        tipsetTracker.addEvent(descriptor, allParents);
        final TipsetAdvancementWeight advancementWeight =
                tipsetWeightCalculator.addEventAndGetAdvancementWeight(descriptor);
        final double weightRatio = advancementWeight.advancementWeight()
                / (double) tipsetWeightCalculator.getMaximumPossibleAdvancementWeight();
        tipsetMetrics.getTipsetAdvancementMetric().update(weightRatio);

        childlessOtherEventTracker.registerSelfEventParents(otherParents);

        lastSelfEvent = descriptor;
        lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
        lastSelfEventTransactionCount = event.getHashedData().getTransactions().length;

        return event;
    }

    /**
     * Given the parents, assemble the event object.
     *
     * @param otherParents the other parents
     * @return the event
     */
    @NonNull
    private GossipEvent assembleEventObject(@NonNull final List<EventDescriptor> otherParents) {

        final Instant now = time.now();
        final Instant timeCreated;
        if (lastSelfEvent == null) {
            timeCreated = now;
        } else {
            timeCreated = EventUtils.calculateNewEventCreationTime(
                    now, lastSelfEventCreationTime, lastSelfEventTransactionCount);
        }

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                softwareVersion,
                selfId,
                lastSelfEvent,
                otherParents,
                addressBook.getRound(),
                timeCreated,
                transactionSupplier.getTransactions());
        cryptography.digestSync(hashedData);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                null /* intentional */,
                signer.sign(hashedData.getHash().getValue()).getSignatureBytes());

        final GossipEvent event = new GossipEvent(hashedData, unhashedData);
        cryptography.digestSync(event);
        event.buildDescriptor();
        return event;
    }

    @NonNull
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Minimum generation non-ancient: ")
                .append(tipsetTracker.getNonAncientEventWindow())
                .append("\n");
        sb.append("Latest self event: ").append(lastSelfEvent).append("\n");
        sb.append(tipsetWeightCalculator);

        sb.append("Childless events:");
        final List<EventDescriptor> childlessEvents = childlessOtherEventTracker.getChildlessEvents();
        if (childlessEvents.isEmpty()) {
            sb.append(" none\n");
        } else {
            sb.append("\n");
            for (final EventDescriptor event : childlessEvents) {
                final Tipset tipset = tipsetTracker.getTipset(event);
                sb.append("  - ").append(event).append(" ").append(tipset).append("\n");
            }
        }

        return sb.toString();
    }
}
