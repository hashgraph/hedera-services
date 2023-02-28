package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.threading.framework.BlockingQueueInserter;
import com.swirlds.common.threading.framework.MultiQueueThread;
import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;

/**
 * An object capable of writing preconsensus events to disk. Work is done asynchronously on a background thread.
 */
public class AsyncPreConsensusEventWriter implements PreConsensusEventWriter {

    /**
     * The wrapped writer.
     */
    private final PreConsensusEventWriter writer;

    /**
     * Background work is performed on this thread.
     */
    private final MultiQueueThread handleThread;

    /**
     * Used to the minimum generation non-ancient onto the handle queue.
     */
    private final BlockingQueueInserter<Long> minimumGenerationNonAncientInserter;

    /**
     * Used to push events onto the handle queue.
     */
    private final BlockingQueueInserter<EventImpl> eventInserter;

    /**
     * Create a new AsyncPreConsensusEventWriter.
     * @param threadManager responsible for creating new threads
     * @param writer the writer to which events will be written, wrapped by this class
     */
    public AsyncPreConsensusEventWriter(
            final ThreadManager threadManager,
            final PreConsensusEventStreamConfig config,
            final PreConsensusEventWriter writer) {

        this.writer = writer;

        handleThread = new MultiQueueThreadConfiguration(threadManager)
                .setComponent("pre-consensus")
                .setThreadName("event-writer")
                .setCapacity(config.writeQueueCapacity())
                .setWaitForItemRunnable(this::waitForNextEvent)
                .addHandler(Long.class, this::minimumGenerationNonAncientHandler)
                .addHandler(EventImpl.class, this::eventHandler)
                .build();

        minimumGenerationNonAncientInserter = handleThread.getInserter(Long.class);
        eventInserter = handleThread.getInserter(EventImpl.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        writer.start();
        handleThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        writer.stop();
        handleThread.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEvent(final EventImpl event) throws InterruptedException {
        // TODO we should we update sequence number here?
        eventInserter.put(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) throws InterruptedException {
        minimumGenerationNonAncientInserter.put(minimumGenerationNonAncient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationToStore(final long minimumGenerationToStore) {
        writer.setMinimumGenerationToStore(minimumGenerationToStore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventDurable(final EventImpl event) {
        return writer.isEventDurable(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilDurable(final EventImpl event) throws InterruptedException {
        writer.waitUntilDurable(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitUntilDurable(final EventImpl event, final Duration timeToWait) throws InterruptedException {
        return writer.waitUntilDurable(event, timeToWait);
    }
}
