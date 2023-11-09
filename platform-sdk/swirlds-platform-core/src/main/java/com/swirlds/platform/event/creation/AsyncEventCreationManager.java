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

import static com.swirlds.base.state.LifecyclePhase.NOT_STARTED;
import static com.swirlds.base.state.LifecyclePhase.STARTED;
import static com.swirlds.base.state.LifecyclePhase.STOPPED;

import com.swirlds.base.state.Lifecycle;
import com.swirlds.base.state.LifecyclePhase;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Manages the creation of events. Wraps a {@link SyncEventCreationManager} and provides an asynchronous
 * interface.
 */
public class AsyncEventCreationManager implements Lifecycle {

    /**
     * Tracks the lifecycle of this object.
     */
    private LifecyclePhase lifecyclePhase = NOT_STARTED;

    /**
     * The core logic for creating events.
     */
    private final SyncEventCreationManager eventCreator;

    /**
     * Contains tasks that need to be run on the processing thread for this component.
     */
    private final MultiQueueThread workQueue;

    /**
     * The object used to enqueue new events onto the work queue.
     */
    private final BlockingQueueInserter<EventImpl> eventInserter;

    /**
     * The object used to enqueue updates to the minimum generation non-ancient onto the work queue.
     */
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    /**
     * Used to signal a desired pause.
     *
     * @param shouldBePaused     true if event creation should be paused, false if it should be unpaused
     * @param pauseStatusAdopted a future that will be completed when the requested pause status has been adopted
     */
    private record PauseRequest(boolean shouldBePaused, @NonNull StandardFuture<Void> pauseStatusAdopted) {}

    /**
     * The object used to enqueue updates to the pause status on the work queue.
     */
    private final BlockingQueueInserter<PauseRequest> setPauseStatusInserter;

    /**
     * Constructor.
     *
     * @param platformContext the platform's context
     * @param threadManager   manages the creation of new threads
     * @param eventCreator    creates new events
     */
    public AsyncEventCreationManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final SyncEventCreationManager eventCreator) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        this.eventCreator = Objects.requireNonNull(eventCreator);

        workQueue = new MultiQueueThreadConfiguration(threadManager)
                .setThreadName("event-creator")
                .setCapacity(eventCreationConfig.creationQueueSize())
                .setMaxBufferSize(eventCreationConfig.creationQueueBufferSize())
                .addHandler(EventImpl.class, this::handleEvent)
                .addHandler(Long.class, this::handleMinimumGenerationNonAncient)
                .addHandler(PauseRequest.class, this::handlePauseStatusChange)
                .setIdleCallback(eventCreator::maybeCreateEvent)
                .setBatchHandledCallback(eventCreator::maybeCreateEvent)
                .setWaitForWorkDuration(eventCreationConfig.creationQueueWaitForWorkPeriod())
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(platformContext.getMetrics())
                        .enableMaxSizeMetric()
                        .enableBusyTimeMetric())
                .build();

        eventInserter = workQueue.getInserter(EventImpl.class);
        minimumGenerationNonAncientInserter = workQueue.getInserter(Long.class);
        setPauseStatusInserter = workQueue.getInserter(PauseRequest.class);
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
     * @param pauseRequest describes the desired pause status
     */
    private void handlePauseStatusChange(@NonNull final PauseRequest pauseRequest) {
        if (pauseRequest.shouldBePaused()) {
            eventCreator.pauseEventCreation();
        } else {
            eventCreator.resumeEventCreation();
        }
        pauseRequest.pauseStatusAdopted.complete(null);
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
     * created events have been submitted.
     */
    public void pauseEventCreation() {
        try {
            final StandardFuture<Void> future = new StandardFuture<>();
            setPauseStatusInserter.put(new PauseRequest(true, future));
            future.getAndRethrow();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while attempting to pause event creation", e);
        }
    }

    /**
     * Resume event creation. Unlike {@link #pauseEventCreation()}, does not block until event creation resumes.
     */
    public void resumeEventCreation() {
        try {
            setPauseStatusInserter.put(new PauseRequest(false, new StandardFuture<>()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while attempting to unpause event creation", e);
        }
    }
}
