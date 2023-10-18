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

package com.swirlds.platform.event.creation;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.rules.AggregateEventCreationRules;
import com.swirlds.platform.event.creation.rules.BackpressureRule;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import com.swirlds.platform.event.creation.rules.MaximumRateRule;
import com.swirlds.platform.event.creation.rules.PlatformStatusRule;
import com.swirlds.platform.event.creation.tipset.TipsetEventCreator;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * A factory for creating {@link AsyncEventCreationManager} instances.
 */
public final class EventCreationManagerFactory {

    private EventCreationManagerFactory() {}

    /**
     * Create a new event creation manager.
     *
     * @param platformContext           the platform's context
     * @param threadManager             manages the creation of new threads
     * @param time                      provides the wall clock time
     * @param signer                    can sign with this node's key
     * @param addressBook               the current address book
     * @param selfId                    the ID of this node
     * @param appVersion                the current application version
     * @param transactionPool           provides transactions to be added to new events
     * @param eventIntakeQueue          the queue to which new events should be added
     * @param eventObserverDispatcher   wires together event intake logic
     * @param platformStatusSupplier    provides the current platform status
     * @param latestReconnectRound      provides the latest reconnect round
     * @param latestSavedStateRound     provides the latest saved state round
     * @return a new event creation manager
     */
    @NonNull
    public static AsyncEventCreationManager buildEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final TransactionPool transactionPool,
            @NonNull final QueueThread<GossipEvent> eventIntakeQueue,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final Supplier<Long> latestReconnectRound,
            @NonNull final Supplier<Long> latestSavedStateRound) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(time);
        Objects.requireNonNull(signer);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(appVersion);
        Objects.requireNonNull(transactionPool);
        Objects.requireNonNull(eventIntakeQueue);
        Objects.requireNonNull(eventObserverDispatcher);
        Objects.requireNonNull(platformStatusSupplier);
        Objects.requireNonNull(latestReconnectRound);
        Objects.requireNonNull(latestSavedStateRound);

        final EventCreator eventCreator = new TipsetEventCreator(
                platformContext,
                time,
                new Random() /* does not need to be cryptographically secure */,
                signer,
                addressBook,
                selfId,
                appVersion,
                transactionPool);

        final EventCreationRule eventCreationRules = AggregateEventCreationRules.of(
                new MaximumRateRule(platformContext, time),
                new BackpressureRule(platformContext, eventIntakeQueue::size),
                new PlatformStatusRule(platformStatusSupplier, transactionPool));

        final SyncEventCreationManager syncEventCreationManager = new SyncEventCreationManager(
                platformContext, time, eventCreator, eventCreationRules, eventIntakeQueue::offer);

        final AsyncEventCreationManager manager =
                new AsyncEventCreationManager(platformContext, threadManager, syncEventCreationManager);

        eventObserverDispatcher.addObserver((PreConsensusEventObserver) event -> abortAndThrowIfInterrupted(
                manager::registerEvent,
                event,
                "Interrupted while attempting to register event with tipset event creator"));

        eventObserverDispatcher.addObserver((ConsensusRoundObserver) round -> abortAndThrowIfInterrupted(
                manager::setMinimumGenerationNonAncient,
                round.getGenerations().getMinGenerationNonAncient(),
                "Interrupted while attempting to register minimum generation "
                        + "non-ancient with tipset event creator"));

        return manager;
    }
}
