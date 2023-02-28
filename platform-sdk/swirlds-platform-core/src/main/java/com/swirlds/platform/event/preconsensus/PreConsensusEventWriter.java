package com.swirlds.platform.event.preconsensus;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.swirlds.common.threading.framework.config.MultiQueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.Time;
import com.swirlds.common.utility.LongRunningAverage;
import com.swirlds.common.utility.Startable;
import com.swirlds.common.utility.Stoppable;
import com.swirlds.common.utility.throttle.RateLimiter;
import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;

/**
 * An object capable of writing preconsensus events to disk.
 */
public interface PreConsensusEventWriter extends Startable, Stoppable {

    /**
     * Write an event to the stream.
     *
     * @param event
     * 		the event to be written
     * @throws InterruptedException
     * 		if interrupted while waiting on queue to drain
     */
    void addEvent(EventImpl event) throws InterruptedException;

    /**
     * Let the event writer know the minimum generation for non-ancient events. Ancient events will be
     * ignored if added to the event writer.
     *
     * @param minimumGenerationNonAncient
     * 		the minimum generation of a non-ancient event
     */
    void setMinimumGenerationNonAncient(long minimumGenerationNonAncient) throws InterruptedException;

    /**
     * Set the minimum generation needed to be kept on disk.
     *
     * @param minimumGenerationToStore
     * 		the minimum generation required to be stored on disk
     */
    void setMinimumGenerationToStore(long minimumGenerationToStore);

    /**
     * Check if an event is guaranteed to be durable, i.e. flushed to disk.
     * @param event the event in question
     * @return true if the event can is guaranteed to be durable
     */
    boolean isEventDurable(EventImpl event);

    /**
     * Wait until an event is guaranteed to be durable, i.e. flushed to disk.
     * @param event the event in question
     * @throws InterruptedException if interrupted while waiting
     */
    void waitUntilDurable(EventImpl event) throws InterruptedException;

    /**
     * Wait until an event is guaranteed to be durable, i.e. flushed to disk.
     * @param event the event in question
     * @param  timeToWait the maximum time to wait
     * @return true if the event is durable, false if the time to wait has elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    boolean waitUntilDurable(EventImpl event, final Duration timeToWait) throws InterruptedException;
}
