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

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// TODO rename

/**
 * Hashes, deduplicates, and validates events on a thread pool.
 */
public class IncomingEventProcessor {

    private final ExecutorService executorService;

    private final Cryptography cryptography;
    private final EventDeduplicator deduplicator;
    private final GossipEventValidators validators;
    private final InterruptableConsumer<GossipEvent> validEventConsumer;

    public IncomingEventProcessor(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final EventDeduplicator deduplicator,
            @NonNull final GossipEventValidators validators,
            @NonNull final InterruptableConsumer<GossipEvent> validEventConsumer) {

        Objects.requireNonNull(threadManager);
        this.cryptography = platformContext.getCryptography();
        this.deduplicator = Objects.requireNonNull(deduplicator);
        this.validators = Objects.requireNonNull(validators);
        this.validEventConsumer = Objects.requireNonNull(validEventConsumer);

        // TODO add a metric for this queue
        // TODO settings

        final BlockingQueue<Runnable> workQueue =
                new ReallyBlockingQueueImSeriousThisNeedsToBlockQueue<>(new LinkedBlockingQueue<>(1024));

        executorService = new ThreadPoolExecutor(
                8,
                8,
                0L,
                TimeUnit.MILLISECONDS,
                workQueue,
                threadManager.createThreadFactory("platform", "event-processor"));
    }

    /**
     * Add an event that needs hashed, deduplicated, and validated.
     *
     * @param event the event to be hashed
     */
    public void ingestEvent(@NonNull final GossipEvent event) {
        executorService.submit(buildProcessingTask(event));
    }

    /**
     * Build a task that will hash the event on the executor service. The callable returns null if the event should not
     * be ingested, and returns the event if it passes all checks.
     *
     * @param event the event to be hashed
     */
    @NonNull
    private Runnable buildProcessingTask(@NonNull final GossipEvent event) {
        return () -> {
            if (event.getHashedData().getHash() == null) {
                cryptography.digestSync(event.getHashedData());
            }

            final boolean isDuplicate = deduplicator.isDuplicate(event);
            if (isDuplicate) {
                return;
            }

            final boolean isValid = validators.isEventValid(event);
            if (!isValid) {
                return;
            }

            event.buildDescriptor();

            try {
                validEventConsumer.accept(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("unable to pass event to next stage in pipeline", e);
                // TODO fix how we do interrupts in all places in this changeset
            }
        };
    }
}
