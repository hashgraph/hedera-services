package com.swirlds.platform.event.validation;

import com.swirlds.base.state.Startable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// TODO rename

/**
 * Hashes, deduplicates, and validates events on a thread pool.
 */
public class IncomingEventProcessor implements Startable {

    private final ExecutorService executorService;

    private final Cryptography cryptography;
    private final QueueThread<Future<GossipEvent>> finalizer;
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

        // TODO settings
        executorService = Executors.newFixedThreadPool(
                8,
                threadManager.createThreadFactory("platform", "event-intake-processing"));

        finalizer = new QueueThreadConfiguration<Future<GossipEvent>>(threadManager)
                .setComponent("platform")
                .setThreadName("event-intake-processing-finalizer")
                .setHandler(this::waitForEventToBeProcessed)
                .build();


    }

    /**
     * Add an event that needs hashed, deduplicated, and validated.
     *
     * @param event the event to be hashed
     */
    public void ingestEvent(@NonNull final GossipEvent event) throws InterruptedException {
        final Future<GossipEvent> future = executorService.submit(buildProcessingTask(event));
        finalizer.put(future);
    }

    /**
     * Build a task that will hash the event on the executor service. The callable returns null if the event should not
     * be ingested, and returns the event if it passes all checks.
     *
     * @param gossipEvent the event to be hashed
     */
    @NonNull
    private Callable<GossipEvent> buildProcessingTask(@NonNull final GossipEvent gossipEvent) {
        return () -> {
            if (gossipEvent.getHashedData().getHash() == null) {
                cryptography.digestSync(gossipEvent.getHashedData());
            }

            final boolean isDuplicate = deduplicator.isDuplicate(gossipEvent);
            if (isDuplicate) {
                return null;
            }

            final boolean isValid = validators.isEventValid(gossipEvent);
            if (!isValid) {
                return null;
            }

            gossipEvent.buildDescriptor();
            return gossipEvent;

            // TODO metrics?
        };
    }

    /**
     * Wait for the next event to be hashed, deduplicated, and validated. If an event passes all of these checks, it is
     * passed to the {@link #validEventConsumer}.
     *
     * @param future the future that will contain the hashed event
     */
    private void waitForEventToBeProcessed(@NonNull final Future<GossipEvent> future) {
        try {
            final GossipEvent event = future.get();
            if (event != null) {
                validEventConsumer.accept(event);
            }
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            // TODO log error
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        finalizer.start();
    }
}
