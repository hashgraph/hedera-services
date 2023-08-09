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

import static com.swirlds.base.state.LifecyclePhase.NOT_STARTED;
import static com.swirlds.base.state.LifecyclePhase.STARTED;
import static com.swirlds.base.state.LifecyclePhase.STOPPED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.StartUpEventFrozenManager;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.tipset.rules.AggregateTipsetEventCreationRules;
import com.swirlds.platform.event.tipset.rules.ReconnectStateSavedRule;
import com.swirlds.platform.event.tipset.rules.TipsetBackpressureRule;
import com.swirlds.platform.event.tipset.rules.TipsetEventCreationRule;
import com.swirlds.platform.event.tipset.rules.TipsetMaximumRateRule;
import com.swirlds.platform.event.tipset.rules.TipsetPlatformStatusRule;
import com.swirlds.platform.eventhandling.EventTransactionPool;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// TODO unit tests

/**
 * Manages the creation of events.
 */
public class TipsetEventCreationManager implements Lifecycle {

    /**
     * Tracks the lifecycle of this object.
     */
    private LifecyclePhase lifecyclePhase = NOT_STARTED;

    /**
     * The core logic for creating events.
     */
    private final TipsetEventCreator eventCreator;

    /**
     * Contains tasks that need to be run on the processing thread for this component.
     */
    private final MultiQueueThread workQueue;

    /**
     * The object used to enqueue new events onto the work queue.
     */
    private final BlockingQueueInserter<EventImpl> eventInserter;

    /**
     * If not null, contains an event that was created but was unable to be submitted right away.
     */
    private GossipEvent mostRecentlyCreatedEvent;

    /**
     * The object used to enqueue updates to the minimum generation non-ancient onto the work queue.
     */
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    /**
     * The object used to enqueue updates to the pause status on the work queue.
     */
    private final BlockingQueueInserter<Boolean> setPauseStatusInserter;

    /**
     * When the event creator makes a new event, pass it to this lambda. If this method returns true then the event was
     * accepted, if this method returns false then the event was rejected and needs to be submitted again later.
     */
    private final Function<GossipEvent, Boolean> newEventHandler;

    /**
     * The rules that determine whether or not a new event should be created.
     */
    private final TipsetEventCreationRule eventCreationRules;

    /**
     * Whether or not event creation is paused. If unpaused, event creation may be blocked by the event creation rules
     * or by the tipset algorithm's requirements. If paused, event creation is blocked regardless of all other factors.
     */
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param platformContext           the platform's context
     * @param threadManager             manages the creation of new threads
     * @param time                      provides the wall clock time
     * @param random                    a source of randomness, does not need to be cryptographically secure
     * @param signer                    can sign with this node's key
     * @param addressBook               the current address book
     * @param selfId                    the ID of this node
     * @param softwareVersion           the current software version
     * @param transactionPool           provides transactions to be added to new events
     * @param newEventHandler           when the event creator makes a new event, pass it to this lambda. If this method
     *                                  returns true then the event was accepted, if this method returns false then the
     *                                  event was rejected and needs to be submitted again later.
     * @param eventIntakeQueueSize      provides the current size of the event intake queue
     * @param platformStatusSupplier    provides the current platform status
     * @param startUpEventFrozenManager prevents event creation when the platform has just started up
     * @param latestReconnectRound      provides the latest reconnect round
     * @param latestSavedStateRound     provides the latest saved state round
     */
    public TipsetEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final Random random,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final EventTransactionPool transactionPool,
            @NonNull final Function<GossipEvent, Boolean> newEventHandler,
            @NonNull final IntSupplier eventIntakeQueueSize,
            @NonNull final Supplier<PlatformStatus> platformStatusSupplier,
            @NonNull final StartUpEventFrozenManager startUpEventFrozenManager,
            @NonNull final Supplier<Long> latestReconnectRound,
            @NonNull final Supplier<Long> latestSavedStateRound) {

        this.newEventHandler = Objects.requireNonNull(newEventHandler);

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(time);
        Objects.requireNonNull(random);
        Objects.requireNonNull(signer);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(softwareVersion);
        Objects.requireNonNull(transactionPool);
        Objects.requireNonNull(eventIntakeQueueSize);
        Objects.requireNonNull(platformStatusSupplier);
        Objects.requireNonNull(startUpEventFrozenManager);
        Objects.requireNonNull(latestReconnectRound);
        Objects.requireNonNull(latestSavedStateRound);

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        final List<TipsetEventCreationRule> rules = new ArrayList<>();
        rules.add(new TipsetMaximumRateRule(platformContext, time));
        rules.add(new TipsetBackpressureRule(platformContext, eventIntakeQueueSize));
        rules.add(new TipsetPlatformStatusRule(platformStatusSupplier, transactionPool, startUpEventFrozenManager));
        rules.add(new ReconnectStateSavedRule(latestReconnectRound, latestSavedStateRound));

        eventCreationRules = AggregateTipsetEventCreationRules.of(rules);

        eventCreator = new TipsetEventCreatorImpl(
                platformContext,
                time,
                random /* does not need to be cryptographically secure */,
                signer,
                addressBook,
                selfId,
                softwareVersion,
                transactionPool);

        workQueue = new MultiQueueThreadConfiguration(threadManager)
                .setThreadName("event-creator")
                .setCapacity(eventCreationConfig.creationQueueSize())
                .setMaxBufferSize(eventCreationConfig.creationQueueBufferSize())
                .addHandler(EventImpl.class, this::handleEvent)
                .addHandler(Long.class, this::handleMinimumGenerationNonAncient)
                .addHandler(Boolean.class, this::handlePauseStatusChange)
                .setIdleCallback(this::maybeCreateEvent)
                .setBatchHandledCallback(this::maybeCreateEvent)
                .setWaitForWorkDuration(eventCreationConfig.creationQueueWaitForWorkPeriod())
                .build();

        eventInserter = workQueue.getInserter(EventImpl.class);
        minimumGenerationNonAncientInserter = workQueue.getInserter(Long.class);
        setPauseStatusInserter = workQueue.getInserter(Boolean.class);
    }

    /**
     * Add an event from the event intake to the work queue. A background thread will eventually pass this event to the
     * event creator on the processing thread.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final EventImpl event) throws InterruptedException {
        eventInserter.put(event);
    }

    /**
     * Update the minimum generation non-ancient
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * Take an event from the work queue and pass it into the event creator.
     *
     * @param event the event to pass
     */
    private void handleEvent(@NonNull final EventImpl event) {
        eventCreator.registerEvent(event);
    }

    /**
     * Pass a new minimum generation non-ancient into the event creator.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    private void handleMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        eventCreator.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
    }

    /**
     * Update the pause status.
     *
     * @param shouldBePaused true if event creation should be paused, false if it should be unpaused
     */
    private void handlePauseStatusChange(final boolean shouldBePaused) {
        if (shouldBePaused && !this.isPaused.get()) {
            // We are not currently paused and we want to be paused. Before we can pause, we must
            // make sure that the most recently created event has been accepted.

            while (mostRecentlyCreatedEvent != null) {
                tryToSubmitMostRecentEvent();
            }
        }

        this.isPaused.set(shouldBePaused);
    }

    /**
     * If there is an unsubmitted self event then attempt to submit it.
     */
    private void tryToSubmitMostRecentEvent() {
        if (mostRecentlyCreatedEvent != null) {
            final boolean accepted = newEventHandler.apply(mostRecentlyCreatedEvent);
            if (accepted) {
                mostRecentlyCreatedEvent = null;
            }
        }
    }

    /**
     * Create a new event if it is legal to do so.
     */
    private void maybeCreateEvent() {
        if (!eventCreationRules.isEventCreationPermitted() || isPaused.get()) {
            return;
        }

        tryToSubmitMostRecentEvent();
        if (mostRecentlyCreatedEvent != null) {
            // Don't create a new event until the previous one has been accepted.
            return;
        }

        mostRecentlyCreatedEvent = eventCreator.maybeCreateEvent();
        if (mostRecentlyCreatedEvent != null) {
            eventCreationRules.eventWasCreated();
            tryToSubmitMostRecentEvent();
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public LifecyclePhase getLifecyclePhase() {
        return lifecyclePhase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfNotInPhase(NOT_STARTED);
        lifecyclePhase = STARTED;
        workQueue.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotInPhase(STARTED);
        lifecyclePhase = STOPPED;
        workQueue.stop();
    }

    /**
     * Pause event creation. This method blocks until it is guaranteed that no new events will be created and until all
     * created events have been submitted. Calling this method while event creation is already paused has no effect.
     */
    public void pauseEventCreation() {
        try {
            setPauseStatusInserter.put(true);
            while (!isPaused.get()) {
                // Busy waits are ugly and inefficient. But pausing is very rare (only during reconnects),
                // and so the impact of this busy wait is negligible.
                MILLISECONDS.sleep(1);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while attempting to pause event creation", e);
        }
    }

    /**
     * Resume event creation. Calling this method while event creation is already unpaused has no effect.
     */
    public void resumeEventCreation() {
        try {
            setPauseStatusInserter.put(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while attempting to unpause event creation", e);
        }
    }
}
