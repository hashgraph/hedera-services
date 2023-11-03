/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.stream;

import static com.swirlds.common.metrics.Metrics.INFO_CATEGORY;
import static com.swirlds.common.units.UnitConstants.SECONDS_TO_MILLISECONDS;
import static com.swirlds.logging.legacy.LogMarker.EVENT_STREAM;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.stream.internal.TimestampStreamFileWriter;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.manager.ThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is used for generating event stream files when enableEventStreaming is true, and for calculating
 * runningHash for consensus Events.
 */
public class EventStreamManager<T extends StreamAligned & Timestamped & RunningHashable & SerializableHashable> {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(EventStreamManager.class);

    /**
     * receives consensus events from ConsensusRoundHandler.addEvent(), then passes to hashQueueThread and
     * writeQueueThread
     */
    private final MultiStream<T> multiStream;
    /**
     * check whether this event is the last event before restart
     */
    private final Predicate<T> isLastEventInFreezeCheck;
    /** receives consensus events from multiStream, then passes to hashCalculator */
    private QueueThreadObjectStream<T> hashQueueThread;
    /**
     * receives consensus events from hashQueueThread, calculates this event's Hash, then passes to
     * runningHashCalculator
     */
    private HashCalculatorForStream<T> hashCalculator;
    /** receives consensus events from multiStream, then passes to streamFileWriter */
    private QueueThreadObjectStream<T> writeQueueThread;
    /** receives consensus events from writeQueueThread, serializes consensus events to event stream files */
    private TimestampStreamFileWriter<T> streamFileWriter;
    /** initialHash loaded from signed state */
    private Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);
    /**
     * When we freeze the platform, the last event to be written to EventStream file is the last event in the freeze
     * round. The freeze round is defined as the first round with a consensus timestamp after the start of the freeze
     * period.
     */
    private volatile boolean freezePeriodStarted = false;

    /**
     * @param platformContext          the platform context
     * @param threadManager            responsible for managing thread lifecycles
     * @param selfId                   the id of this node
     * @param signer                   an object that can sign things
     * @param nodeName                 name of this node
     * @param enableEventStreaming     whether write event stream files or not
     * @param eventsLogDir             eventStream files will be generated in this directory
     * @param eventsLogPeriod          period of generating eventStream file
     * @param isLastEventInFreezeCheck a predicate which checks whether this event is the last event before restart
     */
    public EventStreamManager(
            @NonNull final PlatformContext platformContext,
            final ThreadManager threadManager,
            final NodeId selfId,
            final Signer signer,
            final String nodeName,
            final boolean enableEventStreaming,
            final String eventsLogDir,
            final long eventsLogPeriod,
            final int eventStreamQueueCapacity,
            final Predicate<T> isLastEventInFreezeCheck) {

        if (enableEventStreaming) {
            // the directory to which event stream files are written
            final String eventStreamDir = eventsLogDir + "/events_" + nodeName;
            try {
                Files.createDirectories(Paths.get(eventStreamDir));
            } catch (final IOException e) {
                throw new IllegalStateException("Can not create directory for event stream", e);
            }

            streamFileWriter = new TimestampStreamFileWriter<>(
                    eventStreamDir,
                    eventsLogPeriod * SECONDS_TO_MILLISECONDS,
                    signer,
                    /** when event streaming is started after reconnect, or at state recovering,
                     * startWriteAtCompleteWindow should be set to be true; when event streaming is started after
                     * restart, it should be set to be false */
                    false,
                    EventStreamType.getInstance());

            writeQueueThread = new QueueThreadObjectStreamConfiguration<T>(threadManager)
                    .setNodeId(selfId)
                    .setComponent("event-stream")
                    .setThreadName("write-queue")
                    .setCapacity(eventStreamQueueCapacity)
                    .setForwardTo(streamFileWriter)
                    .build();
            writeQueueThread.start();
        }

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                INFO_CATEGORY, "eventStreamQueueSize", Integer.class, this::getEventStreamingQueueSize)
                        .withDescription("size of the queue from which we take events and write to EventStream file")
                        .withUnit("count"));

        platformContext
                .getMetrics()
                .getOrCreate(new FunctionGauge.Config<>(
                                INFO_CATEGORY, "hashQueueSize", Integer.class, this::getHashQueueSize)
                        .withDescription("size of the queue from which we take events, calculate Hash and RunningHash")
                        .withUnit("count"));

        // receives consensus events from hashCalculator, calculates and set runningHash for this event
        final RunningHashCalculatorForStream<T> runningHashCalculator = new RunningHashCalculatorForStream<>();
        hashCalculator = new HashCalculatorForStream<>(runningHashCalculator);
        hashQueueThread = new QueueThreadObjectStreamConfiguration<T>(threadManager)
                .setNodeId(selfId)
                .setComponent("event-stream")
                .setThreadName("hash-queue")
                .setCapacity(eventStreamQueueCapacity)
                .setForwardTo(hashCalculator)
                .build();
        hashQueueThread.start();

        multiStream = new MultiStream<>(
                enableEventStreaming ? List.of(hashQueueThread, writeQueueThread) : List.of(hashQueueThread));
        multiStream.setRunningHash(initialHash);

        this.isLastEventInFreezeCheck = isLastEventInFreezeCheck;
    }

    /**
     * @param multiStream              the instance which receives consensus events from ConsensusRoundHandler, then
     *                                 passes to nextStreams
     * @param isLastEventInFreezeCheck a predicate which checks whether this event is the last event before restart
     */
    public EventStreamManager(final MultiStream<T> multiStream, final Predicate<T> isLastEventInFreezeCheck) {
        this.multiStream = multiStream;
        multiStream.setRunningHash(initialHash);
        this.isLastEventInFreezeCheck = isLastEventInFreezeCheck;
    }

    /**
     * Closes the multistream.
     * <p>
     * IMPORTANT: For unit test purposes only.
     */
    public void stop() {
        writeQueueThread.stop();
        hashQueueThread.stop();

        streamFileWriter.close();
        multiStream.close();
    }

    public void addEvents(final List<T> events) {
        events.forEach(this::addEvent);
    }

    /**
     * receives a consensus event from ConsensusRoundHandler each time, sends it to multiStream which then sends to two
     * queueThread for calculating runningHash and writing to file
     *
     * @param event the consensus event to be added
     */
    public void addEvent(final T event) {
        if (!freezePeriodStarted) {
            multiStream.addObject(event);
            if (isLastEventInFreezeCheck.test(event)) {
                freezePeriodStarted = true;
                logger.info(
                        EVENT_STREAM.getMarker(),
                        "ConsensusTimestamp of the last Event to be written into file before restarting: " + "{}",
                        event::getTimestamp);
                multiStream.close();
            }
        } else {
            logger.warn(EVENT_STREAM.getMarker(), "Event {} dropped after freezePeriodStarted!", event.getTimestamp());
        }
    }

    /**
     * sets startWriteAtCompleteWindow: it should be set to be true after reconnect, or at state recovering; it should
     * be set to be false at restart
     *
     * @param startWriteAtCompleteWindow whether the writer should not write until the first complete window
     */
    public void setStartWriteAtCompleteWindow(final boolean startWriteAtCompleteWindow) {
        if (streamFileWriter != null) {
            streamFileWriter.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
        }
    }

    /**
     * returns current size of working queue for calculating hash and runningHash
     *
     * @return current size of working queue for calculating hash and runningHash
     */
    public int getHashQueueSize() {
        if (hashQueueThread == null) {
            return 0;
        }
        return hashQueueThread.getQueue().size();
    }

    /**
     * returns current size of working queue for writing to event stream files
     *
     * @return current size of working queue for writing to event stream files
     */
    public int getEventStreamingQueueSize() {
        return writeQueueThread == null ? 0 : writeQueueThread.getQueue().size();
    }

    /**
     * for unit testing
     *
     * @return current multiStream instance
     */
    public MultiStream<T> getMultiStream() {
        return multiStream;
    }

    /**
     * for unit testing
     *
     * @return current TimestampStreamFileWriter instance
     */
    public TimestampStreamFileWriter<T> getStreamFileWriter() {
        return streamFileWriter;
    }

    /**
     * for unit testing
     *
     * @return current HashCalculatorForStream instance
     */
    public HashCalculatorForStream<T> getHashCalculator() {
        return hashCalculator;
    }

    /**
     * for unit testing
     *
     * @return whether freeze period has started
     */
    public boolean getFreezePeriodStarted() {
        return freezePeriodStarted;
    }

    /**
     * for unit testing
     *
     * @return a copy of initialHash
     */
    public Hash getInitialHash() {
        return new Hash(initialHash);
    }

    /**
     * sets initialHash after loading from signed state
     *
     * @param initialHash current runningHash of all consensus events
     */
    public void setInitialHash(final Hash initialHash) {
        this.initialHash = initialHash;
        logger.info(EVENT_STREAM.getMarker(), "EventStreamManager::setInitialHash: {}", () -> initialHash);
        multiStream.setRunningHash(initialHash);
    }
}
