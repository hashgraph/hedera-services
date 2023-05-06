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

import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.Time;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Manages the creation of events.
 */
public class TipsetEventCreationManager implements Lifecycle { // TODO test

    private LifecyclePhase lifecyclePhase = NOT_STARTED;
    private final long selfId;
    private final TipsetEventCreator eventCreator;

    private final MultiQueueThread workQueue;
    private final BlockingQueueInserter<EventImpl> eventInserter;
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;
    private final Consumer<GossipEvent> newEventHandler;

    public TipsetEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Cryptography cryptography,
            @NonNull final Time time,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            final long selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final TransactionSupplier transactionSupplier,
            @NonNull final Consumer<GossipEvent> newEventHandler) {

        this.selfId = selfId;
        this.newEventHandler = Objects.requireNonNull(newEventHandler);

        eventCreator = new TipsetEventCreator(
                platformContext, cryptography, time, signer, addressBook, selfId, softwareVersion, transactionSupplier);

        workQueue = new MultiQueueThreadConfiguration(threadManager)
                .setThreadName("event-creator")
                .setCapacity(1024) // TODO setting for capacity
                .addHandler(EventImpl.class, this::handleEvent)
                .addHandler(Long.class, this::handleMinimumGenerationNonAncient)
                .setIdleCallback(this::maybeCreateEvent)
                .build();

        eventInserter = workQueue.getInserter(EventImpl.class);
        minimumGenerationNonAncientInserter = workQueue.getInserter(Long.class);
    }

    /**
     * Add an event from the event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final EventImpl event) throws InterruptedException {
        if (event.getHashedData().getCreatorId() == selfId) {
            // TODO this behavior needs to be different at startup time if not at genesis
            return;
        }
        eventInserter.put(event);
    }

    /**
     * Upgate the minimum generation non-ancient
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * Pass an event into the event creator.
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
     * Create a new event if it is legal to do so.
     */
    private void maybeCreateEvent() {
        // TODO API for event creation rules, e.g. stop creating events if falling behind, etc.

        final GossipEvent event = eventCreator.createNewEvent();
        if (event != null) {
            newEventHandler.accept(event);
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
}
