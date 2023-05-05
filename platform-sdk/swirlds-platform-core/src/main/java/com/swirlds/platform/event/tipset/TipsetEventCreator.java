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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Responsible for creating new events using the tipset algorithm.
 */
public class TipsetEventCreator {

    private final long selfId;
    private final TipsetBuilder tipsetBuilder;
    private final TipsetScoreCalculator tipsetScoreCalculator;
    private final ChildlessEventTracker childlessEventTracker;
    private final TransactionSupplier transactionSupplier;

    // TODO add bully score and encapsulate metrics in a helper class
    private static final RunningAverageMetric.Config TIPSET_SCORE_CONFIG = new RunningAverageMetric.Config(
                    "platform", "tipsetScore")
            .withDescription("The score, based on tipset advancements, of each new event created by this "
                    + "node. A score of 0.0 means the event did not advance consensus at all, while a score "
                    + "of 1.0 means that the event advanced consensus as much as a single event can.");
    private final RunningAverageMetric tipsetScore;

    public TipsetEventCreator(
            @NonNull final PlatformContext platformContext,
            @NonNull final AddressBook addressBook,
            final long selfId,
            @NonNull final TransactionSupplier transactionSupplier) {

        this.selfId = selfId;

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

        tipsetScore = platformContext.getMetrics().getOrCreate(TIPSET_SCORE_CONFIG);

        this.transactionSupplier = Objects.requireNonNull(transactionSupplier);
    }

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final EventImpl event) {
        if (event.getCreatorId() == selfId) {
            // Self events are ingested immediately when they are created.
            // TODO what about when streaming from PCES?
            // TODO what about when we start with events in the state?
            return;
        }

        final EventFingerprint fingerprint = getEventFingerprint(event);
        final List<EventFingerprint> parentFingerprints = getParentFingerprints(event);

        tipsetBuilder.addEvent(fingerprint, parentFingerprints);
        childlessEventTracker.addEvent(fingerprint, parentFingerprints);
    }

    /**
     * Add a newly created self event.
     *
     * @param selfEvent the newly created event
     */
    private void addSelfEvent(@NonNull final EventImpl selfEvent) {
        final EventFingerprint fingerprint = getEventFingerprint(selfEvent);
        final List<EventFingerprint> parentFingerprints = getParentFingerprints(selfEvent);

        tipsetBuilder.addEvent(fingerprint, parentFingerprints);

        final long score = tipsetScoreCalculator.addEventAndGetAdvancementScore(fingerprint);
        final double scoreRatio = score / (double) tipsetScoreCalculator.getMaximumPossibleScore();

        tipsetScore.update(scoreRatio);
    }

    /**
     * Create a new event if it is legal to do so.
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    public EventImpl createNewEvent() {

        /*
        final BaseEventHashedData hashedData = new BaseEventHashedData(
                softwareVersion,
                selfId.getId(),
                EventUtils.getEventGeneration(selfParent),
                EventUtils.getEventGeneration(otherParent),
                EventUtils.getEventHash(selfParent),
                EventUtils.getEventHash(otherParent),
                EventUtils.getChildTimeCreated(time.now(), selfParent),
                transactionSupplier.getTransactions());
        hasher.digestSync(hashedData);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                EventUtils.getCreatorId(otherParent),
                signer.sign(hashedData.getHash().getValue()).getSignatureBytes());
        final GossipEvent gossipEvent = new GossipEvent(hashedData, unhashedData);

         */

        return null;
    }

    // TODO create utility function for creating these fingerprints

    /**
     * Get the fingerprint of an event.
     */
    @NonNull
    private EventFingerprint getEventFingerprint(@NonNull final EventImpl event) {
        return new EventFingerprint(event.getCreatorId(), event.getGeneration(), event.getHash());
    }

    /**
     * Get the fingerprints of an event's parents.
     */
    @NonNull
    private List<EventFingerprint> getParentFingerprints(@NonNull final EventImpl event) {
        final List<EventFingerprint> parentFingerprints = new ArrayList<>(2);
        if (event.getSelfParent() != null) {
            parentFingerprints.add(getEventFingerprint(event.getSelfParent()));
        }
        if (event.getOtherParent() != null) {
            parentFingerprints.add(getEventFingerprint(event.getOtherParent()));
        }
        return parentFingerprints;
    }
}
