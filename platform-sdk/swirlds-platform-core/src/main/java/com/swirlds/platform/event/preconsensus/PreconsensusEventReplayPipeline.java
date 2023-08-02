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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reads events from the preconsensus event stream, hashes them, and passes them to event intake. Reading from disk,
 * hashing, and intake are done on different threads.
 */
public class PreconsensusEventReplayPipeline {

    private static final Logger logger = LogManager.getLogger(PreconsensusEventReplayPipeline.class);

    private int eventCount = 0;
    private int transactionCount = 0;

    private final AtomicBoolean error = new AtomicBoolean(false);

    /**
     * An event that is being hashed.
     *
     * @param event      the event
     * @param hashFuture becomes complete when the event has been hashed
     */
    private record EventBeingHashed(@NonNull EventImpl event, @NonNull Future<Void> hashFuture) {}

    private final QueueThread<EventBeingHashed> intakeQueue;
    private final ExecutorService hashPool;

    private final PlatformContext platformContext;
    private final IOIterator<EventImpl> unhashedEventIterator;
    private final Consumer<GossipEvent> hashedEventConsumer;

    /**
     * Create a new event replay pipeline.
     *
     * @param platformContext       the platform context
     * @param threadManager         manages background threads
     * @param unhashedEventIterator iterates over events from the preconsensus event stream, events are unhashed
     * @param hashedEventConsumer   events should be passed to this method, in order, after being hashed
     */
    public PreconsensusEventReplayPipeline(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final IOIterator<EventImpl> unhashedEventIterator,
            @NonNull final Consumer<GossipEvent> hashedEventConsumer) {

        this.platformContext = Objects.requireNonNull(platformContext);
        Objects.requireNonNull(threadManager);
        this.unhashedEventIterator = Objects.requireNonNull(unhashedEventIterator);
        this.hashedEventConsumer = Objects.requireNonNull(hashedEventConsumer);

        final PreconsensusEventStreamConfig config =
                platformContext.getConfiguration().getConfigData(PreconsensusEventStreamConfig.class);

        intakeQueue = new QueueThreadConfiguration<EventBeingHashed>(threadManager)
                .setThreadName("EventReplayPipeline-Ingest")
                .setCapacity(config.replayQueueSize())
                .setHandler(this::handleEvent)
                .build();

        hashPool = Executors.newFixedThreadPool(
                config.replayHashPoolSize(),
                new ThreadConfiguration(threadManager)
                        .setThreadName("EventReplayPipeline-HashPool")
                        .buildFactory());
    }

    /**
     * Handle an event. Events passed to this method will have been scheduled for hashing, but the hash may not yet be
     * computed.
     *
     * @param eventBeingHashed the event
     */
    private void handleEvent(@NonNull final EventBeingHashed eventBeingHashed) {
        if (error.get()) {
            // Don't handle anything once an error is observed.
            return;
        }
        try {
            eventBeingHashed.hashFuture().get();
            final EventImpl event = eventBeingHashed.event();
            final GossipEvent gossipEvent = new GossipEvent(event.getHashedData(), event.getUnhashedData());
            gossipEvent.buildDescriptor();
            hashedEventConsumer.accept(gossipEvent);
        } catch (final InterruptedException e) {
            logger.error("Interrupted while handling event from PCES", e);
            Thread.currentThread().interrupt();
            error.set(true);
        } catch (final Exception e) {
            logger.error("Error while handling event from PCES", e);
            error.set(true);
        }
    }

    /**
     * Replays events from the preconsensus event stream, hashing them and passing them to event intake.
     */
    public void replayEvents() {
        intakeQueue.start();

        final Cryptography cryptography = platformContext.getCryptography();

        try {
            while (unhashedEventIterator.hasNext() && !error.get()) {
                final EventImpl event = unhashedEventIterator.next();

                eventCount++;
                transactionCount += event.getTransactions().length;

                final Future<Void> hashFuture = hashPool.submit(() -> {
                    cryptography.digestSync(event.getBaseEventHashedData());
                    return null;
                });

                intakeQueue.put(new EventBeingHashed(event, hashFuture));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("error encountered while reading from the PCES", e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while attempting to replay the PCES", e);
        } finally {
            intakeQueue.stop();
            hashPool.shutdown();
        }

        if (error.get()) {
            throw new IllegalStateException("error encountered while replaying events from the PCES");
        }
    }

    /**
     * Get the number of events that have been replayed.
     *
     * @return the number of events that have been replayed
     */
    public int getEventCount() {
        return eventCount;
    }

    /**
     * Get the number of transactions that have been replayed.
     *
     * @return the number of transactions that have been replayed
     */
    public int getTransactionCount() {
        return transactionCount;
    }
}
