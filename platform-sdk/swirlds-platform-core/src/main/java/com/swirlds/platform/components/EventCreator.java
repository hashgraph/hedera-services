/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import static com.swirlds.logging.LogMarker.CREATE_EVENT;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.EventCreationRuleResponse;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.components.transaction.TransactionPool;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.SelfEventStorage;
import com.swirlds.platform.event.creation.AncientParentsRule;
import com.swirlds.platform.internal.EventImpl;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class encapsulates the workflow required to create new events.
 */
public class EventCreator {
    private static final Logger logger = LogManager.getLogger(EventCreator.class);

    /** This node's address book ID */
    private final NodeId selfId;

    /** An implementor of {@link Signer} */
    private final Signer signer;

    /** Checks for ancient parents */
    private final AncientParentsRule ancientParentsCheck;

    /** An implementor of {@link TransactionSupplier} */
    private final TransactionSupplier transactionSupplier;

    /** An implementor of {@link EventHandler} */
    private final EventHandler newEventHandler;

    /** This hashgraph's {@link EventMapper} */
    private final EventMapper eventMapper;

    /** Stores the most recent event created by me */
    private final SelfEventStorage selfEventStorage;

    /** An implementor of {@link TransactionPool} */
    private final TransactionPool transactionPool;

    /** Indicates if the system is currently in a freeze. */
    private final BooleanSupplier inFreeze;

    /** This object is used for checking whether this node should create an event or not */
    private final EventCreationRules eventCreationRules;

    /**
     * Construct a new EventCreator.
     *
     * @param selfId                   the ID of this node
     * @param signer                   responsible for signing new events
     * @param platformContext          the PlatformContext
     * @param graphGenerationsSupplier supplies the key generation number from the hashgraph
     * @param transactionSupplier      this method supplies transactions that should be inserted into newly created
     *                                 events
     * @param newEventHandler          this method is passed all newly created events
     * @param selfEventStorage         stores the most recent event created by me
     * @param eventMapper              the object that tracks the most recent events from each node
     * @param transactionPool          the TransactionPool
     * @param inFreeze                 indicates if the system is currently in a freeze
     * @param eventCreationRules       the object used for checking if we should create an event or not
     */
    public EventCreator(
            final NodeId selfId,
            final Signer signer,
            final PlatformContext platformContext,
            final Supplier<GraphGenerations> graphGenerationsSupplier,
            final TransactionSupplier transactionSupplier,
            final EventHandler newEventHandler,
            final EventMapper eventMapper,
            final SelfEventStorage selfEventStorage,
            final TransactionPool transactionPool,
            final BooleanSupplier inFreeze,
            final EventCreationRules eventCreationRules) {
        this.selfId = selfId;
        this.signer = signer;
        this.ancientParentsCheck = new AncientParentsRule(graphGenerationsSupplier);
        this.transactionSupplier = transactionSupplier;
        this.newEventHandler = newEventHandler;
        this.eventMapper = eventMapper;
        this.selfEventStorage = selfEventStorage;
        this.transactionPool = transactionPool;
        this.inFreeze = inFreeze;
        this.eventCreationRules = eventCreationRules;
    }

    /**
     * Create a new event and push it into the gossip/consensus pipeline.
     *
     * @param otherId the node ID that will supply the other parent for this event
     */
    public boolean createEvent(final long otherId) {
        if (eventCreationRules.shouldCreateEvent() == EventCreationRuleResponse.DONT_CREATE) {
            return false;
        }

        // We don't want to create multiple events with the same other parent, so we have to check if we
        // already created an event with this particular other parent.
        //
        // We still want to create an event if there are state signature transactions when we are frozen.
        if (hasOtherParentAlreadyBeenUsed(otherId) && !hasSignatureTransactionsWhileFrozen()) {
            return false;
        }

        final EventImpl otherParent = eventMapper.getMostRecentEvent(otherId);
        final EventImpl selfParent = selfEventStorage.getMostRecentSelfEvent();

        if (eventCreationRules.shouldCreateEvent(selfParent, otherParent) == EventCreationRuleResponse.DONT_CREATE) {
            return false;
        }

        // Don't create an event if both parents are old.
        if (ancientParentsCheck.areBothParentsAncient(selfParent, otherParent)) {
            logger.debug(
                    CREATE_EVENT.getMarker(),
                    "Both parents are ancient, selfParent: {}, otherParent: {}",
                    () -> EventUtils.toShortString(selfParent),
                    () -> EventUtils.toShortString(otherParent));
            return false;
        }

        handleNewEvent(buildEvent(selfParent, otherParent));
        return true;
    }

    private void handleNewEvent(final EventImpl event) {
        logEventCreation(event);
        selfEventStorage.setMostRecentSelfEvent(event);
        newEventHandler.handleEvent(event);
    }

    /**
     * Construct an event object.
     */
    protected EventImpl buildEvent(final EventImpl selfParent, final EventImpl otherParent) {

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                selfId.getId(),
                EventUtils.getEventGeneration(selfParent),
                EventUtils.getEventGeneration(otherParent),
                EventUtils.getEventHash(selfParent),
                EventUtils.getEventHash(otherParent),
                EventUtils.getChildTimeCreated(Instant.now(), selfParent),
                transactionSupplier.getTransactions());
        CryptographyHolder.get().digestSync(hashedData);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                EventUtils.getCreatorId(otherParent),
                signer.sign(hashedData.getHash().getValue()).getSignatureBytes());

        return new EventImpl(hashedData, unhashedData, selfParent, otherParent);
    }

    /**
     * Check if the most recent event from the given node has been used as an other parent by an event created by the
     * current node.
     *
     * @param otherId the ID of the node supplying the other parent
     */
    protected boolean hasOtherParentAlreadyBeenUsed(final long otherId) {
        return !selfId.equalsMain(otherId) && eventMapper.hasMostRecentEventBeenUsedAsOtherParent(otherId);
    }

    /**
     * Check if there are signature transactions waiting to be inserted into an event during a freeze
     */
    protected boolean hasSignatureTransactionsWhileFrozen() {
        return transactionPool.numSignatureTransEvent() > 0 && inFreeze.getAsBoolean();
    }

    /**
     * Write to the log (if configured) every time an event is created.
     *
     * @param event the created event to be logged
     */
    protected void logEventCreation(final EventImpl event) {
        logger.debug(CREATE_EVENT.getMarker(), "Creating {}", event::toMediumString);
    }
}
