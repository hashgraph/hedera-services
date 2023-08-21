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
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.tipset.rules.AggregateTipsetEventCreationRules;
import com.swirlds.platform.event.tipset.rules.ReconnectStateSavedRule;
import com.swirlds.platform.event.tipset.rules.TipsetBackpressureRule;
import com.swirlds.platform.event.tipset.rules.TipsetEventCreationRule;
import com.swirlds.platform.event.tipset.rules.TipsetMaximumRateRule;
import com.swirlds.platform.event.tipset.rules.TipsetPlatformStatusRule;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.observers.ConsensusRoundObserver;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.observers.PreConsensusEventObserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * A factory for creating {@link AsyncTipsetEventCreationManager} instances.
 */
public final class TipsetEventCreationManagerFactory {

    private TipsetEventCreationManagerFactory() {}

    /**
     * Create a new tipset event creation manager.
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
     * @param startUpEventFrozenManager manages the start-up event frozen state
     * @param latestReconnectRound      provides the latest reconnect round
     * @param latestSavedStateRound     provides the latest saved state round
     * @return a new tipset event creation manager, or null if tipset event creation is disabled
     */
    @Nullable
    public static AsyncTipsetEventCreationManager buildTipsetEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final TransactionPool transactionPool,
            @NonNull final QueueThread<EventIntakeTask> eventIntakeQueue,
            @NonNull final EventObserverDispatcher eventObserverDispatcher,
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
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
        Objects.requireNonNull(startUpEventFrozenManager);
        Objects.requireNonNull(latestReconnectRound);
        Objects.requireNonNull(latestSavedStateRound);

        final boolean useTipsetAlgorithm = platformContext
                .getConfiguration()
                .getConfigData(EventCreationConfig.class)
                .useTipsetAlgorithm();

        if (!useTipsetAlgorithm) {
            return null;
        }

        final TipsetEventCreator eventCreator = new TipsetEventCreatorImpl(
                platformContext,
                time,
                new Random() /* does not need to be cryptographically secure */,
                signer,
                addressBook,
                selfId,
                appVersion,
                transactionPool);

        final TipsetEventCreationRule eventCreationRules = AggregateTipsetEventCreationRules.of(
                new TipsetMaximumRateRule(platformContext, time),
                new TipsetBackpressureRule(platformContext, eventIntakeQueue::size),
                new TipsetPlatformStatusRule(platformStatusSupplier, transactionPool, startUpEventFrozenManager),
                new ReconnectStateSavedRule(latestReconnectRound, latestSavedStateRound));

        final SyncTipsetEventCreationManager syncEventCreationManager =
                new SyncTipsetEventCreationManager(eventCreator, eventCreationRules, eventIntakeQueue::offer);

        final AsyncTipsetEventCreationManager manager =
                new AsyncTipsetEventCreationManager(platformContext, threadManager, syncEventCreationManager);

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
