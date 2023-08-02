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

package com.swirlds.platform.event.tipset;

import static com.swirlds.common.system.NodeId.UNDEFINED_NODE_ID;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.event.EventConstants.CREATOR_ID_UNDEFINED;
import static com.swirlds.platform.event.EventConstants.GENERATION_UNDEFINED;
import static com.swirlds.platform.event.tipset.TipsetAdvancementWeight.ZERO_ADVANCEMENT_WEIGHT;
import static com.swirlds.platform.event.tipset.TipsetUtils.buildDescriptor;
import static com.swirlds.platform.event.tipset.TipsetUtils.getParentDescriptors;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for creating new events using the tipset algorithm.
 */
public class TipsetEventCreatorImpl implements TipsetEventCreator {

    private static final Logger logger = LogManager.getLogger(TipsetEventCreatorImpl.class);

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
    private final int networkSize;

    /**
     * The bully score is divided by this number to get the probability of creating an event that reduces the bully
     * score. The higher this number is, the lower the probability is that an event will be created that reduces the
     * bully score.
     */
    private final double antiBullyingFactor;

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
    public TipsetEventCreatorImpl(
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

        Objects.requireNonNull(addressBook);

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        cryptography = platformContext.getCryptography();
        antiBullyingFactor = Math.max(1.0, eventCreationConfig.antiBullyingFactor());
        tipsetMetrics = new TipsetMetrics(platformContext, addressBook);
        tipsetTracker = new TipsetTracker(time, addressBook);
        childlessOtherEventTracker = new ChildlessEventTracker();
        tipsetWeightCalculator = new TipsetWeightCalculator(
                platformContext, time, addressBook, selfId, tipsetTracker, childlessOtherEventTracker);
        networkSize = addressBook.getSize();

        zeroAdvancementWeightLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
        noParentFoundLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerEvent(@NonNull final EventImpl event) {

        final NodeId eventCreator = event.getHashedData().getCreatorId();
        final boolean selfEvent = eventCreator.equals(selfId);

        if (selfEvent) {
            if (lastSelfEvent == null || lastSelfEvent.getGeneration() < event.getGeneration()) {
                // Normally we will ingest self events before we get to this point, but it's possible
                // to learn of self events for the first time here if we are loading from a restart or reconnect.
                lastSelfEvent = buildDescriptor(event);
                lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
                lastSelfEventTransactionCount = event.getTransactions() == null ? 0 : event.getTransactions().length;

                if (event.getBaseEventUnhashedData().getOtherId() != UNDEFINED_NODE_ID) {
                    final EventDescriptor parentDescriptor = new EventDescriptor(
                            event.getBaseEventHashedData().getOtherParentHash(),
                            event.getBaseEventUnhashedData().getOtherId(),
                            event.getBaseEventHashedData().getOtherParentGen());

                    childlessOtherEventTracker.registerSelfEventParents(List.of(parentDescriptor));
                }
            } else {
                // We already ingested this self event (when it was created),
                return;
            }
        }

        final EventDescriptor descriptor = buildDescriptor(event);
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
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        tipsetTracker.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
        childlessOtherEventTracker.pruneOldEvents(minimumGenerationNonAncient);
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

        final long bullyScore = tipsetWeightCalculator.getMaxBullyScore();
        tipsetMetrics.getBullyScoreMetric().update(bullyScore);

        // Never bother with anti-bullying techniques if we have a bully score of 1. We are pretty much guaranteed
        // to bully ~1/3 of other nodes by a score of 1.
        final double beNiceToNerdChance = (bullyScore - 1) / antiBullyingFactor;

        if (beNiceToNerdChance > 0 && random.nextDouble() < beNiceToNerdChance) {
            return createEventToReduceBullyScore();
        } else {
            return createEventByOptimizingAdvancementWeight();
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
        return buildAndProcessEvent(lastSelfEvent);
    }

    /**
     * Create an event using the other parent with the best tipset advancement weight.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventByOptimizingAdvancementWeight() {
        final List<EventDescriptor> possibleOtherParents = childlessOtherEventTracker.getChildlessEvents();
        Collections.shuffle(possibleOtherParents, random);

        EventDescriptor bestOtherParent = null;
        TipsetAdvancementWeight bestAdvancementWeight = ZERO_ADVANCEMENT_WEIGHT;
        for (final EventDescriptor otherParent : possibleOtherParents) {
            final TipsetAdvancementWeight advancementWeight =
                    tipsetWeightCalculator.getTheoreticalAdvancementWeight(List.of(otherParent));
            if (advancementWeight.isGreaterThan(bestAdvancementWeight)) {
                bestOtherParent = otherParent;
                bestAdvancementWeight = advancementWeight;
            }
        }

        if (lastSelfEvent != null && bestOtherParent == null) {
            // There exist no parents that can advance consensus, and this is not our first event.
            // The only time it's ok to create an event with no other parent is when we are creating
            // our first event.
            return null;
        }

        if (bestOtherParent != null) {
            tipsetMetrics.getTipsetParentMetric(bestOtherParent.getCreator()).cycle();
        }
        return buildAndProcessEvent(bestOtherParent);
    }

    /**
     * Create an event that reduces the bully score.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventToReduceBullyScore() {
        final List<EventDescriptor> possibleOtherParents = childlessOtherEventTracker.getChildlessEvents();
        final List<EventDescriptor> nerds = new ArrayList<>(possibleOtherParents.size());

        // Choose a random nerd, weighted by how much it is currently being bullied.

        // First, figure out who is a nerd and sum up all bully scores.
        int bullyScoreSum = 0;
        final List<Integer> bullyScores = new ArrayList<>(possibleOtherParents.size());
        for (final EventDescriptor possibleNerd : possibleOtherParents) {
            final int bullyScore = tipsetWeightCalculator.getBullyScoreForNode(possibleNerd.getCreator());

            final List<EventDescriptor> theoreticalParents = new ArrayList<>(2);
            theoreticalParents.add(possibleNerd);
            if (lastSelfEvent == null) {
                throw new IllegalStateException("lastSelfEvent is null");
            }
            theoreticalParents.add(lastSelfEvent);

            final TipsetAdvancementWeight advancementWeight =
                    tipsetWeightCalculator.getTheoreticalAdvancementWeight(theoreticalParents);

            if (bullyScore > 1) {
                if (advancementWeight.isNonZero()) {
                    nerds.add(possibleNerd);
                    bullyScores.add(bullyScore);
                    bullyScoreSum += bullyScore;
                } else {
                    // Note: if bully score is greater than 1, it is mathematically not possible
                    // for the advancement score to be zero. But in the interest in extreme caution,
                    // we check anyway, since it is very important never to create events with
                    // an advancement score of zero.
                    zeroAdvancementWeightLogger.error(
                            EXCEPTION.getMarker(),
                            "bully score is {} but advancement score is zero for {}.\n{}",
                            bullyScore,
                            possibleNerd,
                            this);
                }
            }
        }

        if (nerds.isEmpty()) {
            // Note: this should be impossible, since we will not enter this method in the first
            // place if there are no nerds. But better to be safe than sorry, and returning null
            // is an acceptable way of saying "I can't create an event right now".
            noParentFoundLogger.error(EXCEPTION.getMarker(), "failed to locate eligible nerd to use as a parent");
            return null;
        }

        // Choose a random nerd.
        final int choice = random.nextInt(bullyScoreSum);
        int runningSum = 0;
        for (int i = 0; i < nerds.size(); i++) {
            runningSum += bullyScores.get(i);
            if (choice < runningSum) {
                final EventDescriptor nerd = nerds.get(i);
                tipsetMetrics.getPityParentMetric(nerd.getCreator()).cycle();
                return buildAndProcessEvent(nerd);
            }
        }

        // This should be impossible.
        throw new IllegalStateException("Failed to find an other parent");
    }

    /**
     * Given an other parent, build the next self event and process it.
     *
     * @param otherParent the other parent, or null if there is no other parent
     * @return the new event
     */
    private GossipEvent buildAndProcessEvent(@Nullable final EventDescriptor otherParent) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);
        if (lastSelfEvent != null) {
            parentDescriptors.add(lastSelfEvent);
        }
        if (otherParent != null) {
            parentDescriptors.add(otherParent);
        }

        final GossipEvent event = assembleEventObject(otherParent);

        final EventDescriptor descriptor = buildDescriptor(event);
        tipsetTracker.addEvent(descriptor, parentDescriptors);
        final TipsetAdvancementWeight advancementWeight =
                tipsetWeightCalculator.addEventAndGetAdvancementWeight(descriptor);
        final double weightRatio = advancementWeight.advancementWeight()
                / (double) tipsetWeightCalculator.getMaximumPossibleAdvancementWeight();
        tipsetMetrics.getTipsetAdvancementMetric().update(weightRatio);

        if (otherParent != null) {
            childlessOtherEventTracker.registerSelfEventParents(List.of(otherParent));
        }

        lastSelfEvent = descriptor;
        lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
        lastSelfEventTransactionCount = event.getHashedData().getTransactions().length;

        return event;
    }

    /**
     * Given the parents, assemble the event object.
     *
     * @param otherParent the other parent
     * @return the event
     */
    @NonNull
    private GossipEvent assembleEventObject(@Nullable final EventDescriptor otherParent) {

        final long selfParentGeneration = getGeneration(lastSelfEvent);
        final Hash selfParentHash = getHash(lastSelfEvent);

        final long otherParentGeneration = getGeneration(otherParent);
        final Hash otherParentHash = getHash(otherParent);
        final NodeId otherParentId = getCreator(otherParent);

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
                selfParentGeneration,
                otherParentGeneration,
                selfParentHash,
                otherParentHash,
                timeCreated,
                transactionSupplier.getTransactions());
        cryptography.digestSync(hashedData);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                otherParentId, signer.sign(hashedData.getHash().getValue()).getSignatureBytes());

        final GossipEvent event = new GossipEvent(hashedData, unhashedData);
        cryptography.digestSync(event);
        event.buildDescriptor();
        return event;
    }

    /**
     * Get the generation of a descriptor, handle null appropriately.
     */
    private static long getGeneration(@Nullable final EventDescriptor descriptor) {
        if (descriptor == null) {
            return GENERATION_UNDEFINED;
        } else {
            return descriptor.getGeneration();
        }
    }

    /**
     * Get the hash of a descriptor, handle null appropriately.
     */
    @Nullable
    private static Hash getHash(@Nullable final EventDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        } else {
            return descriptor.getHash();
        }
    }

    /**
     * Get the creator of a descriptor, handle null appropriately.
     */
    @Nullable
    private static NodeId getCreator(@Nullable final EventDescriptor descriptor) {
        if (descriptor == null) {
            return CREATOR_ID_UNDEFINED;
        } else {
            return descriptor.getCreator();
        }
    }

    @NonNull
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Minimum generation non-ancient: ")
                .append(tipsetTracker.getMinimumGenerationNonAncient())
                .append("\n");
        sb.append(childlessOtherEventTracker);
        sb.append(tipsetWeightCalculator);
        return sb.toString();
    }
}
