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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.base.state.Startable;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
 * Hashes, deduplicates, validates, and calls prehandle for transactions in incoming events.
 */
public class EventPreprocessor implements Clearable, Startable {

    private final Logger logger = LogManager.getLogger(EventPreprocessor.class);

    private final Cryptography cryptography;
    private final Time time;

    private final BlockingQueue<Runnable> workQueue;
    private final ExecutorService executorService;
    private final EventDeduplicator deduplicator;
    private final GossipEventValidators validators;
    private final Consumer<GossipEvent> transactionPrehandler;
    private final InterruptableConsumer<GossipEvent> validEventConsumer;
    private final EventPreprocessorMetrics metrics;
    private final int threadPoolSize;

    private final QueueThread<Future<GossipEvent>> deduplicationQueue;
    private final QueueThread<Future<GossipEvent>> finalizerQueue;

    // TODO document and organize this class

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

        // TODO
        //        workQueue = new ReallyBlockingQueueImSeriousThisNeedsToBlockQueue<>(
        //                new LinkedBlockingQueue<>(config.eventIntakeQueueSize()));
        workQueue = new LinkedBlockingQueue<>();

        metrics = new EventPreprocessorMetrics(platformContext, workQueue::size);
        threadPoolSize = config.eventIntakeQueueThrottleSize();

        executorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                workQueue,
                threadManager.createThreadFactory("platform", "event-processor"));

        deduplicationQueue = new QueueThreadConfiguration<Future<GossipEvent>>(threadManager)
                .setComponent("platform")
                .setThreadName("event-deduplication")
                .setCapacity(config.eventIntakeQueueSize())
                .setHandler(this::handleDeduplication)
                .build();

        // TODO this queue could go away if we make the event intake queue take futures
        finalizerQueue = new QueueThreadConfiguration<Future<GossipEvent>>(threadManager)
                .setComponent("platform")
                .setThreadName("event-finalizer")
                .setHandler(this::handleFinalization)
                .setCapacity(config.eventIntakeQueueSize())
                .build();
    }

    /**
     * Add an event to be preprocessed.
     *
     * @param event the event to be hashed
     */
    public void ingestEvent(@NonNull final GossipEvent event) throws InterruptedException {
        //        executorService.submit(buildProcessingTask(event));

        deduplicationQueue.put(executorService.submit(buildHashingTask(event)));
    }

    @NonNull
    private Callable<GossipEvent> buildHashingTask(@NonNull final GossipEvent event) {
        return () -> {
            try {
                final Instant start = time.now();

                if (event.getHashedData().getHash() == null) {
                    cryptography.digestSync(event.getHashedData());
                }

                final Instant doneHashing = time.now();
                metrics.reportEventHashTime(Duration.between(start, doneHashing)); // TODO metrics documentation

                return event;
            } catch (final Throwable t) {
                logger.error(EXCEPTION.getMarker(), "exception while hashing event", t);
                throw t;
            }
        };
    }

    private void handleDeduplication(@NonNull final Future<GossipEvent> future) {
        try {
            final GossipEvent event = future.get();

            if (deduplicator.addAndCheckIfDuplicated(event)) {
                metrics.registerDuplicateEvent();
                return;
            }
            metrics.registerUniqueEvent();

            finalizerQueue.put(executorService.submit(buildValidationAndPrehandleTask(event)));

        } catch (@NonNull InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e); // TODO
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            logger.error(EXCEPTION.getMarker(), "exception while deduplicating event", t);
            throw t;
        }
    }

    private Callable<GossipEvent> buildValidationAndPrehandleTask(@NonNull final GossipEvent event) {
        return () -> {
            try {
                final Instant start = time.now();

                // FUTURE WORK some validation can move before hashing
                if (!validators.isEventValid(event)) {
                    metrics.registerInvalidEvent();
                    return null;
                }
                metrics.registerValidEvent();
                event.buildDescriptor();

                final Instant doneValidating = time.now();
                metrics.reportEventValidationTime(Duration.between(start, doneValidating));

                transactionPrehandler.accept(event);

                final Instant donePrehandling = time.now();
                metrics.reportEventPrehandleTime(Duration.between(doneValidating, donePrehandling));
                metrics.reportEventPreprocessTime(Duration.between(start, donePrehandling));

                return event;
            } catch (final Throwable t) {
                logger.error(EXCEPTION.getMarker(), "exception while validating event", t);
                throw t;
            }
        };
    }

    private void handleFinalization(@NonNull final Future<GossipEvent> future) {
        try {
            final GossipEvent event = future.get();

            if (event == null) {
                // event was invalid
                return;
            }

            validEventConsumer.accept(event);

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            logger.error(EXCEPTION.getMarker(), "exception while finalizing event", t);
            throw t;
        }
    }

    /**
     * Flush the pipeline. When this method returns, all events previously passed to {@link #ingestEvent(GossipEvent)}
     * will have been processed.
     *
     * @throws InterruptedException if interrupted while waiting for flush to complete
     */
    public void flush() throws InterruptedException {
        flushExecutor(); // Flush hash jobs
        deduplicationQueue.waitUntilNotBusy();
        flushExecutor(); // Flush validation and prehandle jobs
        finalizerQueue.waitUntilNotBusy();
    }

    /**
     * Flush jobs in the executor service.
     */
    private void flushExecutor() throws InterruptedException { // TODO this needs to be more nuanced

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

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        try {
            flush();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while flushing", e);
        }
    }

    /**
     * Get the size of the queue of events waiting to be processed.
     */
    public int getQueueSize() {
        return workQueue.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        deduplicationQueue.start();
        finalizerQueue.start();
    }
}
