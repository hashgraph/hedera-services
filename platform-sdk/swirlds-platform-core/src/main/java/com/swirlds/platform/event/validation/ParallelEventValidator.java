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
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates a stream of events on a thread pool while preserving ordering.
 */
public class ParallelEventValidator implements Startable {

    private final Logger logger = LogManager.getLogger(ParallelEventValidator.class);

    private final ExecutorService executorService;

    private final Cryptography cryptography;
    private final QueueThread<Future<GossipEvent>> hashFinalizer;
    private final Consumer<GossipEvent> hashedEventConsumer;

    public ParallelEventValidator(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Consumer<GossipEvent> hashedEventConsumer) {

        Objects.requireNonNull(threadManager);
        this.cryptography = platformContext.getCryptography();
        this.hashedEventConsumer = Objects.requireNonNull(hashedEventConsumer);

        // TODO settings
        executorService = new ThreadPoolExecutor(
                8,
                8,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(5000),
                threadManager.createThreadFactory("platform", "event-hasher"));

        hashFinalizer = new QueueThreadConfiguration<Future<GossipEvent>>(threadManager)
                .setComponent("platform")
                .setThreadName("event-hash-finalizer")
                .setHandler(this::waitForEventToBeHashed)
                .build();
    }

    /**
     * Add an event that needs to be hashed.
     *
     * @param event the event to be hashed
     */
    public void addUnhashedEvent(@NonNull final GossipEvent event) {
        final Future<GossipEvent> future = executorService.submit(buildHashTask(event));
        hashFinalizer.add(future);
    }

    /**
     * Build a task that will hash the event on the executor service.
     *
     * @param gossipEvent the event to be hashed
     */
    @NonNull
    private Callable<GossipEvent> buildHashTask(@NonNull final GossipEvent gossipEvent) {
        return () -> {
            cryptography.digestSync(gossipEvent.getHashedData());
            return gossipEvent;
        };
    }

    /**
     * Wait for the next event to be hashed. Once the event is hashed, pass it to the consumer.
     *
     * @param future the future that will contain the hashed event
     */
    private void waitForEventToBeHashed(@NonNull final Future<GossipEvent> future) {
        try {
            final GossipEvent event = future.get();
            hashedEventConsumer.accept(event);
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        hashFinalizer.start();
    }
}
