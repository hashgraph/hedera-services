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

import com.swirlds.base.time.Time;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Hashes, deduplicates, validates, and calls prehandle for transactions in incoming events.
 */
public class EventPreprocessor {

    private final ExecutorService executorService;

    private final Cryptography cryptography;
    private final Time time;
    private final EventDeduplicator deduplicator;
    private final GossipEventValidators validators;
    private final Consumer<GossipEvent> transactionPrehandler;
    private final InterruptableConsumer<GossipEvent> validEventConsumer;
    private final EventPreprocessorMetrics metrics;
    private final int threadPoolSize;

    /**
     * Create a new event preprocessor.
     *
     * @param platformContext       the platform context
     * @param threadManager         manages creation of threading resources
     * @param time                  provides wall clock time
     * @param deduplicator          responsible for deduplicating events
     * @param validators            performs validation on events
     * @param transactionPrehandler prehandles transactions in events
     * @param validEventConsumer    events that pass all checks are passed here
     */
    public EventPreprocessor(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final EventDeduplicator deduplicator,
            @NonNull final GossipEventValidators validators,
            @NonNull final Consumer<GossipEvent> transactionPrehandler,
            @NonNull final InterruptableConsumer<GossipEvent> validEventConsumer) {

        Objects.requireNonNull(threadManager);
        this.cryptography = platformContext.getCryptography();
        this.time = Objects.requireNonNull(time);
        this.deduplicator = Objects.requireNonNull(deduplicator);
        this.validators = Objects.requireNonNull(validators);
        this.transactionPrehandler = Objects.requireNonNull(transactionPrehandler);
        this.validEventConsumer = Objects.requireNonNull(validEventConsumer);

        final EventConfig config = platformContext.getConfiguration().getConfigData(EventConfig.class);

        final BlockingQueue<Runnable> workQueue = new ReallyBlockingQueueImSeriousThisNeedsToBlockQueue<>(
                new LinkedBlockingQueue<>(config.eventIntakeQueueSize()));

        metrics = new EventPreprocessorMetrics(platformContext, workQueue::size);
        threadPoolSize = config.eventIntakeQueueThrottleSize();

        executorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                workQueue,
                threadManager.createThreadFactory("platform", "event-processor"));
    }

    /**
     * Add an event to be preprocessed.
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
            final Instant start = time.now();

            if (event.getHashedData().getHash() == null) {
                cryptography.digestSync(event.getHashedData());
            }

            if (deduplicator.isDuplicate(event)) {
                metrics.registerDuplicateEvent();
                return;
            }
            metrics.registerUniqueEvent();

            final Instant doneHashingAndDeduplicating = time.now();
            metrics.reportEventHashTime(Duration.between(start, doneHashingAndDeduplicating));

            // FUTURE WORK some validation can move before hashing
            if (!validators.isEventValid(event)) {
                metrics.registerInvalidEvent();
                return;
            }
            metrics.registerValidEvent();
            event.buildDescriptor();

            final Instant doneValidating = time.now();
            metrics.reportEventValidationTime(Duration.between(doneHashingAndDeduplicating, doneValidating));

            transactionPrehandler.accept(event);

            final Instant donePrehandling = time.now();
            metrics.reportEventPrehandleTime(Duration.between(doneValidating, donePrehandling));
            metrics.reportEventPreprocessTime(Duration.between(start, donePrehandling));

            try {
                validEventConsumer.accept(event);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("unable to pass event to next stage in pipeline", e);
                // TODO evaluate how we do interrupts in all places in this changeset
            }
        };
    }

    /**
     * Flush the pipeline. When this method returns, all events previously passed to {@link #ingestEvent(GossipEvent)}
     * will have been processed.
     *
     * @throws InterruptedException if interrupted while waiting for flush to complete
     */
    public void flush() throws InterruptedException {

        // Submit tasks that will block a thread until all threads are guaranteed to be blocked.
        // Since threads process elements from the queue in order, when all threads are blocked
        // by these new tasks we are guaranteed that all prior work will have been handled.

        final CountDownLatch latch = new CountDownLatch(threadPoolSize);
        for (int i = 0; i < threadPoolSize; i++) {
            executorService.submit(() -> {
                latch.countDown();
                try {
                    latch.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("interrupted while waiting for flush to complete", e);
                }
            });
        }

        latch.await();
    }
}
