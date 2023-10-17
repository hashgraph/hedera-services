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

package com.swirlds.platform.event.orphan;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An asynchronous wrapper around an {@link OrphanBuffer}.
 */
public class AsyncOrphanBuffer implements Startable, Stoppable {
    /**
     * A synchronous orphan buffer being wrapped by this asynchronous object
     */
    private final OrphanBuffer orphanBuffer;

    /**
     * A multi-queue thread that accepts events, flush requests, and updates to the minimum generation of
     * non-ancient events
     * <p>
     * The elements added to this queue are handled in order by the wrapped orphan buffer.
     */
    private final MultiQueueThread queueThread;

    /**
     * An inserter for adding events to the queue thread, to be handled by the orphan buffer
     */
    private final BlockingQueueInserter<GossipEvent> eventInserter;

    /**
     * An inserter for setting the minimum generation of non-ancient events to keep in the orphan buffer
     */
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    /**
     * A request to flush the elements that have been added to the queue to the orphan buffer
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
     * @param platformContext    the platform context
     * @param threadManager      the thread manager
     * @param eventConsumer      non-ancient events are passed to this method in topological order
     * @param intakeEventCounter keeps track of the number of events in the intake pipeline from each peer
     */
    public AsyncOrphanBuffer(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Consumer<GossipEvent> eventConsumer,
            @NonNull final IntakeEventCounter intakeEventCounter) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(eventConsumer);
        Objects.requireNonNull(intakeEventCounter);

        orphanBuffer = new OrphanBuffer(platformContext, eventConsumer, intakeEventCounter);

        queueThread = new MultiQueueThreadConfiguration(threadManager)
                .setComponent("platform")
                .setThreadName("orphan-buffer")
                .addHandler(GossipEvent.class, this::handleEvent)
                .addHandler(Long.class, this::handleMinimumGenerationNonAncient)
                .addHandler(FlushRequest.class, this::handleFlushRequest)
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(platformContext.getMetrics())
                        .enableMaxSizeMetric()
                        .enableBusyTimeMetric())
                .build();

        eventInserter = queueThread.getInserter(GossipEvent.class);
        minimumGenerationNonAncientInserter = queueThread.getInserter(Long.class);
        flushInserter = queueThread.getInserter(FlushRequest.class);
    }

    /**
     * Add a new event to the queue thread.
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) throws InterruptedException {
        eventInserter.put(event);
    }

    /**
     * Set the minimum generation of non-ancient events to keep in the orphan buffer.
     *
     * @param minimumGenerationNonAncient the minimum generation of non-ancient events to keep in the orphan buffer
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * Flush the queue thread to the orphan buffer.
     */
    public void flush() throws InterruptedException {
        final StandardFuture<Void> future = new StandardFuture<>();
        flushInserter.put(new FlushRequest(future));
        future.getAndRethrow();
    }

    /**
     * Pass an event to the orphan buffer.
     * <p>
     * Called on the handle thread of the queue.
     */
    private void handleEvent(@NonNull final GossipEvent event) {
        orphanBuffer.handleEvent(event);
    }

    /**
     * Set the minimum generation of non-ancient events to keep in the orphan buffer.
     * <p>
     * Called on the handle thread of the queue.
     */
    private void handleMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        orphanBuffer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
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
