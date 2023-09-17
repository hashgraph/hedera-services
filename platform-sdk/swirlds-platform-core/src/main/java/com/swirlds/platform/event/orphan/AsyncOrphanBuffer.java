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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * An asynchronous version of {@link OrphanBuffer}.
 */
public class AsyncOrphanBuffer implements Startable, Stoppable {

    private final OrphanBuffer orphanBuffer;

    private final MultiQueueThread thread;

    private final BlockingQueueInserter<GossipEvent> eventInserter;

    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    private record FlushRequest(@NonNull StandardFuture<Void> future) {}

    private final BlockingQueueInserter<FlushRequest> flushInserter;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param threadManager   the thread manager
     * @param eventConsumer   non-ancient events are passed to this method in topological order
     */
    public AsyncOrphanBuffer(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Consumer<GossipEvent> eventConsumer) {

        orphanBuffer = new OrphanBuffer(platformContext, eventConsumer);

        thread = new MultiQueueThreadConfiguration(threadManager)
                .setComponent("platform")
                .setThreadName("orphan-buffer")
                .addHandler(GossipEvent.class, this::handleEvent)
                .addHandler(Long.class, this::handleMinimumGenerationNonAncient)
                .addHandler(FlushRequest.class, this::handleFlushRequest)
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(platformContext.getMetrics())
                        .enableMaxSizeMetric()
                        .enableBusyTimeMetric())
                .build();

        eventInserter = thread.getInserter(GossipEvent.class);
        minimumGenerationNonAncientInserter = thread.getInserter(Long.class);
        flushInserter = thread.getInserter(FlushRequest.class);
    }

    /**
     * Add a new event.
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) throws InterruptedException {
        eventInserter.put(event);
    }

    /**
     * Set the minimum generation of non-ancient events to keep in the buffer.
     *
     * @param minimumGenerationNonAncient the minimum generation of non-ancient events to keep in the buffer
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * Flush the buffer.
     */
    public void flush() throws InterruptedException {
        final StandardFuture<Void> future = new StandardFuture<>();
        flushInserter.put(new FlushRequest(future));
        future.getAndRethrow();
    }

    /**
     * Pass an event to the buffer. Called on the handle thread.
     */
    private void handleEvent(@NonNull final GossipEvent event) {
        orphanBuffer.addEvent(event);
    }

    /**
     * Set the minimum generation of non-ancient events to keep in the buffer. Called on the handle thread.
     */
    private void handleMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        orphanBuffer.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
    }

    /**
     * Signal that the queue has been flushed. Called on the handle thread.
     */
    private void handleFlushRequest(@NonNull final FlushRequest flushRequest) {
        flushRequest.future.complete(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        thread.stop();
    }
}
