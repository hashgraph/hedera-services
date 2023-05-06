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
import static com.swirlds.platform.event.tipset.EventFingerprint.getParentFingerprints;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.time.Time;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Responsible for creating new events using the tipset algorithm.
 */
public class TipsetEventCreator { // TODO test

    private final Cryptography cryptography;
    private final Time time;
    private final Signer signer;
    private final long selfId;
    private final TipsetBuilder tipsetBuilder;
    private final TipsetScoreCalculator tipsetScoreCalculator;
    private final ChildlessEventTracker childlessEventTracker;
    private final TransactionSupplier transactionSupplier;
    private final SoftwareVersion softwareVersion;

    // TODO add bully score and encapsulate metrics in a helper class
    private static final RunningAverageMetric.Config TIPSET_SCORE_CONFIG = new RunningAverageMetric.Config(
                    "platform", "tipsetScore")
            .withDescription("The score, based on tipset advancements, of each new event created by this "
                    + "node. A score of 0.0 means the event did not advance consensus at all, while a score "
                    + "of 1.0 means that the event advanced consensus as much as a single event can.");
    private final RunningAverageMetric tipsetScoreMetric;

    /**
     * The last event created by this node. TODO we need to load this if we don't start from genesis
     */
    private EventFingerprint lastSelfEvent;

    public TipsetEventCreator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Cryptography cryptography,
            @NonNull final Time time,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            final long selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final TransactionSupplier transactionSupplier) {

        this.cryptography = Objects.requireNonNull(cryptography);
        this.time = Objects.requireNonNull(time);
        this.signer = Objects.requireNonNull(signer);
        this.selfId = selfId;
        this.transactionSupplier = Objects.requireNonNull(transactionSupplier);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);

        // TODO reduce indirection in the lambdas
        tipsetBuilder = new TipsetBuilder(addressBook.getSize(), addressBook::getIndex, index -> addressBook
                .getAddress(addressBook.getId(index))
                .getWeight());

        tipsetScoreCalculator = new TipsetScoreCalculator(
                selfId,
                tipsetBuilder,
                addressBook.getSize(),
                addressBook::getIndex,
                index -> addressBook.getAddress(addressBook.getId(index)).getWeight(),
                addressBook.getTotalWeight());

        childlessEventTracker = new ChildlessEventTracker();

        tipsetScoreMetric = platformContext.getMetrics().getOrCreate(TIPSET_SCORE_CONFIG);
    }

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final GossipEvent event) {
        if (event.getHashedData().getCreatorId() == selfId) {
            // Self events are ingested immediately when they are created.
            // TODO what about when streaming from PCES?
            // TODO what about when we start with events in the state?
            return;
        }

        final EventFingerprint fingerprint = EventFingerprint.of(event);
        final List<EventFingerprint> parentFingerprints = getParentFingerprints(event);

        tipsetBuilder.addEvent(fingerprint, parentFingerprints);
        childlessEventTracker.addEvent(fingerprint, parentFingerprints);
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

        final List<EventFingerprint> possibleOtherParents = childlessEventTracker.getChildlessEvents();
        // TODO should we shuffle in case of ties?

        EventFingerprint bestOtherParent = null;
        long bestScore = 0;
        for (final EventFingerprint otherParent : possibleOtherParents) {
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

        final List<EventFingerprint> parentFingerprints = new ArrayList<>(2);
        if (lastSelfEvent != null) {
            parentFingerprints.add(lastSelfEvent);
        }
        if (bestOtherParent != null) {
            parentFingerprints.add(bestOtherParent);
        }

        final GossipEvent event = buildEventFromParents(lastSelfEvent, bestOtherParent);

        final EventFingerprint fingerprint = EventFingerprint.of(event);
        tipsetBuilder.addEvent(fingerprint, parentFingerprints);
        final long score = tipsetScoreCalculator.addEventAndGetAdvancementScore(fingerprint);
        final double scoreRatio = score / (double) tipsetScoreCalculator.getMaximumPossibleScore();
        tipsetScoreMetric.update(scoreRatio);

        lastSelfEvent = fingerprint;

        return event;
    }

    @NonNull
    private GossipEvent buildEventFromParents(
            @Nullable final EventFingerprint selfParent, @Nullable final EventFingerprint otherParent) {
        final long selfParentGeneration;
        if (lastSelfEvent == null) {
            selfParentGeneration = GENERATION_UNDEFINED;
        } else {
            selfParentGeneration = lastSelfEvent.generation();
        }

        final Hash selfParentHash;
        if (lastSelfEvent == null) {
            selfParentHash = null;
        } else {
            selfParentHash = lastSelfEvent.hash();
        }

        final long otherParentId;
        if (selfParent == null) {
            otherParentId = CREATOR_ID_UNDEFINED;
        } else {
            otherParentId = selfParent.creator();
        }

        final long otherParentGeneration;
        if (otherParent == null) {
            // This is only permitted when creating the first event at genesis.
            otherParentGeneration = GENERATION_UNDEFINED;
        } else {
            otherParentGeneration = otherParent.generation();
        }

        final Hash otherParentHash;
        if (otherParent == null) {
            otherParentHash = null;
        } else {
            otherParentHash = otherParent.hash();
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

        return new GossipEvent(hashedData, unhashedData);
    }
}
