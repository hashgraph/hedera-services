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

package com.swirlds.platform.event.validation;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An asynchronous wrapper around a {@link StandaloneEventValidator}.
 */
public class AsyncEventValidator implements Startable, Stoppable {
    /**
     * A synchronous validator being wrapped by this asynchronous object
     */
    private final StandaloneEventValidator eventValidator;

    /**
     * A multi-queue thread that accepts events, flush requests, updates to the minimum generation of
     * non-ancient events, and updates to the address books.
     */
    private final MultiQueueThread queueThread;

    /**
     * An inserter for adding events to the queue thread
     */
    private final BlockingQueueInserter<EventImpl> eventInserter;

    /**
     * An inserter for setting the minimum generation of non-ancient events
     */
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    /**
     * An inserter for adding address book updates to the queue thread
     */
    private final BlockingQueueInserter<AddressBookUpdate> addressBookUpdateInserter;

    /**
     * A request to flush the elements that have been added to the queue
     *
     * @param future a future that will be completed when the flush is complete. used to signal the caller that the
     *               flush is complete.
     */
    private record FlushRequest(@NonNull StandardFuture<Void> future) {}

    /**
     * An inserter for adding a flush request to the queue thread
     */
    private final BlockingQueueInserter<FlushRequest> flushInserter;

    /**
     * Constructor
     *
     * @param platformContext        the platform context
     * @param threadManager          the thread manager
     * @param time                   a source of time
     * @param signatureVerifier      a verifier for checking event signatures
     * @param currentSoftwareVersion the current software version
     * @param previousAddressBook    the previous address book
     * @param currentAddressBook     the current address book
     * @param eventConsumer          deduplicated events are passed to this method
     * @param intakeEventCounter     keeps track of the number of events in the intake pipeline from each peer
     */
    public AsyncEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook,
            @NonNull final Consumer<GossipEvent> eventConsumer,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(time);
        Objects.requireNonNull(signatureVerifier);
        Objects.requireNonNull(currentSoftwareVersion);
        Objects.requireNonNull(previousAddressBook);
        Objects.requireNonNull(currentAddressBook);
        Objects.requireNonNull(eventConsumer);
        Objects.requireNonNull(intakeEventCounter);

        eventValidator = new StandaloneEventValidator(
                platformContext,
                time,
                signatureVerifier,
                currentSoftwareVersion,
                previousAddressBook,
                currentAddressBook,
                eventConsumer,
                intakeEventCounter);

        queueThread = new MultiQueueThreadConfiguration(threadManager)
                .setComponent("platform")
                .setThreadName("event-validator")
                .addHandler(EventImpl.class, this::handleEvent)
                .addHandler(Long.class, this::handleMinimumGenerationNonAncient)
                .addHandler(AddressBookUpdate.class, this::handleAddressBookUpdate)
                .addHandler(FlushRequest.class, this::handleFlushRequest)
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(platformContext.getMetrics())
                        .enableMaxSizeMetric()
                        .enableBusyTimeMetric())
                .build();

        eventInserter = queueThread.getInserter(EventImpl.class);
        minimumGenerationNonAncientInserter = queueThread.getInserter(Long.class);
        addressBookUpdateInserter = queueThread.getInserter(AddressBookUpdate.class);
        flushInserter = queueThread.getInserter(FlushRequest.class);
    }

    /**
     * Add a new event to the queue thread.
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final EventImpl event) throws InterruptedException {
        eventInserter.put(event);
    }

    /**
     * Set the minimum generation of non-ancient events.
     *
     * @param minimumGenerationNonAncient the minimum generation of non-ancient events
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * Flush the queue thread.
     */
    public void flush() throws InterruptedException {
        final StandardFuture<Void> future = new StandardFuture<>();
        flushInserter.put(new FlushRequest(future));
        future.getAndRethrow();
    }

    /**
     * Pass an event to the validator.
     * <p>
     * Called on the handle thread of the queue.
     */
    private void handleEvent(@NonNull final EventImpl event) {
        eventValidator.handleEvent(event);
    }

    /**
     * Set the minimum generation of non-ancient events.
     * <p>
     * Called on the handle thread of the queue.
     */
    private void handleMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        eventValidator.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
    }

    /**
     * Signal that the queue has been flushed.
     * <p>
     * Called on the handle thread of the queue.
     */
    private void handleFlushRequest(@NonNull final FlushRequest flushRequest) {
        flushRequest.future.complete(null);
    }

    /**
     * Pass an address book update to the validator.
     *
     * @param addressBookUpdate the address book update record
     */
    private void handleAddressBookUpdate(@NonNull final AddressBookUpdate addressBookUpdate) {
        eventValidator.updateAddressBooks(addressBookUpdate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        queueThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        queueThread.stop();
    }
}
