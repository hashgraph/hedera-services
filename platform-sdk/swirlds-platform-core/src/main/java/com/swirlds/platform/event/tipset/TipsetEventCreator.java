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

import static com.swirlds.platform.event.EventConstants.CREATOR_ID_UNDEFINED;
import static com.swirlds.platform.event.EventConstants.GENERATION_UNDEFINED;
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
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

// TODO:
//  - reduce lambda use
//  - start up frozen
//  - freeze
//  - reconnect (all gossip flavors)
//  - metrics
//  - convert all constants to settings
//  - tipset score calculator bug unit test
//  - unit tests
//  - resolve remaining TODOs
//  - figure out to handle event.getUnhashedData().getOtherId()...
//                possibly needs hip to fix (or perhaps we look up the data)
//  - extra credit: way to dynamically adjust max event creation rate
//  - hand test all gossip flavors

/**
 * Responsible for creating new events using the tipset algorithm.
 */
public class TipsetEventCreator { // TODO test

    private final Cryptography cryptography;
    private final Time time;
    private final Random random;
    private final Signer signer;
    private final AddressBook addressBook;
    private final NodeId selfId;
    private final TipsetBuilder tipsetBuilder;
    private final TipsetScoreCalculator tipsetScoreCalculator;
    private final ChildlessEventTracker childlessEventTracker;
    private final TransactionSupplier transactionSupplier;
    private final SoftwareVersion softwareVersion;
    private final double antiBullyingFactor;
    private final TipsetMetrics tipsetMetrics;

    /**
     * The last event created by this node. TODO we need to load this if we don't start from genesis
     */
    private EventDescriptor lastSelfEvent;

    /**
     * Create a new tipset event creator.
     *
     * @param platformContext     the platform context
     * @param cryptography        the cryptography instance
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
            @NonNull final Cryptography cryptography,
            @NonNull final Time time,
            @NonNull final Random random,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final TransactionSupplier transactionSupplier) {

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        this.cryptography = Objects.requireNonNull(cryptography);
        this.time = Objects.requireNonNull(time);
        this.random = Objects.requireNonNull(random);
        this.signer = Objects.requireNonNull(signer);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.transactionSupplier = Objects.requireNonNull(transactionSupplier);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
        this.antiBullyingFactor = Math.max(1.0, eventCreationConfig.antiBullyingFactor());
        this.tipsetMetrics = new TipsetMetrics(platformContext);

        // TODO reduce indirection in the lambdas
        // TODO use NodeID better
        tipsetBuilder = new TipsetBuilder(
                addressBook.getSize(),
                id -> addressBook.getIndexOfNodeId(new NodeId(id)),
                index -> addressBook.getAddress(addressBook.getNodeId(index)).getWeight());

        tipsetScoreCalculator = new TipsetScoreCalculator(
                selfId,
                tipsetBuilder,
                addressBook.getSize(),
                id -> addressBook.getIndexOfNodeId(new NodeId(id)),
                index -> addressBook.getAddress(addressBook.getNodeId(index)).getWeight(),
                addressBook.getTotalWeight());

        childlessEventTracker = new ChildlessEventTracker();
    }

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final EventImpl event) {
        if (event.getHashedData().getCreatorId().equals(selfId)) {
            // Self events are ingested immediately when they are created.
            // TODO what about when streaming from PCES?
            // TODO what about when we start with events in the state?
            return;
        }

        final EventDescriptor descriptor = buildDescriptor(event);
        final List<EventDescriptor> parentDescriptors = getParentDescriptors(event);

        tipsetBuilder.addEvent(descriptor, parentDescriptors);
        childlessEventTracker.addEvent(descriptor, parentDescriptors);
    }

    /**
     * Update the minimum generation non-ancient.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        tipsetBuilder.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
        childlessEventTracker.pruneOldEvents(minimumGenerationNonAncient);
    }

    /**
     * Create a new event if it is legal to do so.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    public GossipEvent createNewEvent() {
        final long bullyScore = tipsetScoreCalculator.getBullyScore();
        tipsetMetrics.getBullyScoreMetric().update(bullyScore);

        // Never bother with anti-bullying techniques if we have a bully score of 1. We are pretty much guaranteed
        // to bully ~1/3 of other nodes by a score of 1.
        final double beNiceToNerdChance = (bullyScore - 1) / antiBullyingFactor;

        if (random.nextDouble() < beNiceToNerdChance) {
            return createEventToReduceBullyScore();
        } else {
            return createEventByOptimizingTipsetScore();
        }
    }

    /**
     * Create an event using the other parent with the best tipset score.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventByOptimizingTipsetScore() {
        final List<EventDescriptor> possibleOtherParents = childlessEventTracker.getChildlessEvents();
        Collections.shuffle(possibleOtherParents, random);

        EventDescriptor bestOtherParent = null;
        long bestScore = 0;
        for (final EventDescriptor otherParent : possibleOtherParents) {
            final long parentScore = tipsetScoreCalculator.getTheoreticalAdvancementScore(List.of(otherParent));
            if (parentScore > bestScore) {
                bestOtherParent = otherParent;
                bestScore = parentScore;
            }
        }

        if (lastSelfEvent != null && bestOtherParent == null) {
            // There exist no parents that can advance consensus, and this is not our first event.
            return null;
        }

        return buildEvent(bestOtherParent);
    }

    /**
     * Create an event that reduces the bully score.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventToReduceBullyScore() {
        final List<EventDescriptor> possibleOtherParents = childlessEventTracker.getChildlessEvents();
        final List<EventDescriptor> nerds = new ArrayList<>(possibleOtherParents.size());

        int bullyScoreSum = 0;
        final List<Integer> bullyScores = new ArrayList<>(possibleOtherParents.size());
        for (final EventDescriptor nerd : possibleOtherParents) {
            final int nodeIndex = addressBook.getIndexOfNodeId(nerd.getCreator());
            final int bullyScore = tipsetScoreCalculator.getBullyScoreForNodeIndex(nodeIndex);

            final long tipsetScore = tipsetScoreCalculator.getTheoreticalAdvancementScore(List.of(nerd));

            if (bullyScore > 1 && tipsetScore > 0) {
                nerds.add(nerd);
                bullyScores.add(bullyScore);
                bullyScoreSum += bullyScore;
            }
        }

        if (nerds.isEmpty()) {
            // No eligible nerds, choose the event with the best tipset score
            return createEventByOptimizingTipsetScore();
        }

        final int choice = random.nextInt(bullyScoreSum);
        int runningSum = 0;
        for (int i = 0; i < nerds.size(); i++) {
            runningSum += bullyScores.get(i);
            if (choice < runningSum) {
                return buildEvent(nerds.get(i));
            }
        }

        // this should be impossible
        throw new IllegalStateException("Failed to find an other parent");
    }

    /**
     * Given an other parent, build the next self event.
     *
     * @param otherParent the other parent, or null if there is no other parent
     * @return the new event
     */
    private GossipEvent buildEvent(@Nullable final EventDescriptor otherParent) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);
        if (lastSelfEvent != null) {
            parentDescriptors.add(lastSelfEvent);
        }
        if (otherParent != null) {
            parentDescriptors.add(otherParent);
        }

        final GossipEvent event = buildEventFromParents(lastSelfEvent, otherParent);

        final EventDescriptor descriptor = buildDescriptor(event);
        tipsetBuilder.addEvent(descriptor, parentDescriptors);
        final long score = tipsetScoreCalculator.addEventAndGetAdvancementScore(descriptor);
        final double scoreRatio = score / (double) tipsetScoreCalculator.getMaximumPossibleScore();
        tipsetMetrics.getTipsetScoreMetric().update(scoreRatio);

        lastSelfEvent = descriptor;

        return event;
    }

    /**
     * Given the parents, build the event object.
     *
     * @param selfParent  the self parent
     * @param otherParent the other parent
     * @return the event
     */
    @NonNull
    private GossipEvent buildEventFromParents(
            @Nullable final EventDescriptor selfParent, @Nullable final EventDescriptor otherParent) {
        final long selfParentGeneration;
        if (lastSelfEvent == null) {
            selfParentGeneration = GENERATION_UNDEFINED;
        } else {
            selfParentGeneration = lastSelfEvent.getGeneration();
        }

        final Hash selfParentHash;
        if (lastSelfEvent == null) {
            selfParentHash = null;
        } else {
            selfParentHash = lastSelfEvent.getHash();
        }

        final NodeId otherParentId;
        if (selfParent == null) {
            otherParentId = CREATOR_ID_UNDEFINED;
        } else {
            otherParentId = selfParent.getCreator();
        }

        final long otherParentGeneration;
        if (otherParent == null) {
            // This is only permitted when creating the first event at genesis.
            otherParentGeneration = GENERATION_UNDEFINED;
        } else {
            otherParentGeneration = otherParent.getGeneration();
        }

        final Hash otherParentHash;
        if (otherParent == null) {
            otherParentHash = null;
        } else {
            otherParentHash = otherParent.getHash();
        }

        // TODO we need to emulate the logic in EventUtils.getChildTimeCreated()
        final Instant timeCreated = time.now();

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
        event.buildDescriptor(); // TODO ugh
        cryptography.digestSync(event); // TODO necessary?
        return event;
    }
}
