/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.legacy;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getTimeStampFromFileName;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readStartRunningHashFromStreamFile;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.recovery.legacy.EventFileParser.parseEventStreamFile;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.Settings;
import com.swirlds.platform.internal.EventImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parses event stream file and buffers the events for retrieval. Entire files are parsed, so events before the start
 * time may be put into the queue. Events after the stop time are not put into the queue.
 */
public class EventStreamParser {

    private static final Logger logger = LogManager.getLogger(EventStreamParser.class);
    private static final int EVENTS_QUEUE_CAPACITY = 100_000;
    private final BlockingQueue<EventImpl> events = new LinkedBlockingQueue<>(EVENTS_QUEUE_CAPACITY);
    private final Path eventStreamDir;
    private final Instant startTimestamp;
    private final Instant endTimestamp;
    private final AtomicInteger eventCounter = new AtomicInteger();
    private final Consumer<Hash> hashConsumer;
    private final boolean populateSettingsCommon;
    private boolean initialHashSent = false;
    private volatile boolean doneParsing = false;
    private final ThreadManager threadManager;

    /**
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param eventStreamDir
     * 		the directory containing event stream files
     * @param startTimestamp
     * 		parse event files that contain an event at or after this timestamp
     * @param endTimestamp
     * 		parse events that are before or at this timestamp
     * @param hashConsumer
     * 		a consumer of the starting hash of the first file parsed
     */
    public EventStreamParser(
            final ThreadManager threadManager,
            final Path eventStreamDir,
            final Instant startTimestamp,
            final Instant endTimestamp,
            final Consumer<Hash> hashConsumer) {
        this(threadManager, eventStreamDir, startTimestamp, endTimestamp, hashConsumer, false);
    }

    /**
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param eventStreamDir
     * 		the directory containing event stream files
     * @param startTimestamp
     * 		parse event files that contain an event at or after this timestamp
     * @param endTimestamp
     * 		parse events that are before or at this timestamp
     * @param hashConsumer
     * 		a consumer of the starting hash of the first file parsed
     * @param populateSettingsCommon
     * 		if true, populate the values in {@link SettingsCommon} from the values in {@link Settings}. Should be set to
     *        {@code true} when this method is invoked from a utility class or test.
     */
    public EventStreamParser(
            final ThreadManager threadManager,
            final Path eventStreamDir,
            final Instant startTimestamp,
            final Instant endTimestamp,
            final Consumer<Hash> hashConsumer,
            final boolean populateSettingsCommon) {

        throwIfDirInvalid(eventStreamDir);
        this.threadManager = threadManager;
        this.eventStreamDir = eventStreamDir;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.hashConsumer = hashConsumer;
        this.populateSettingsCommon = populateSettingsCommon;
    }

    private static void throwIfDirInvalid(final Path eventStreamDir) {
        throwArgNull(eventStreamDir, "eventStreamDir");
        if (Files.notExists(eventStreamDir)) {
            throw new IllegalArgumentException("Provided event stream dir does not exist: " + eventStreamDir);
        }
        if (!Files.isDirectory(eventStreamDir)) {
            throw new IllegalArgumentException("Provided event stream dir is not a directory: " + eventStreamDir);
        }
    }

    private static int compare(final Path p1, final Path p2) {
        return filename(p1).compareTo(filename(p2));
    }

    private static Instant timestamp(final Path path) {
        return getTimeStampFromFileName(filename(path));
    }

    private static String filename(final Path path) {
        return path.getFileName().toString();
    }

    /**
     * Starts a thread that reads the event stream files. Each call starts a new thread that stops once all the
     * appropriate files have been parsed. It is intended to be called once during recovery.
     */
    public void start() {
        new ThreadConfiguration(threadManager)
                .setThreadName("event-stream-parser")
                .setExceptionHandler(this::handleException)
                .setRunnable(this::eventPlayback)
                .build(true);
    }

    /**
     * Return the next event in the queue of events parsed from the event file(s). This method will block until an event
     * is available or up to the wait time, whichever comes first. If no event is available for after the specified wait
     * time, null is returned. Events are stored in the same order they are read from the parsed file.
     *
     * @param timeout
     * 		the timeout
     * @param timeoutUnit
     * 		the timeout unit
     * @return the next event, or null if none is available in the timeout period.
     */
    public EventImpl getNextEvent(final int timeout, final TimeUnit timeoutUnit) {
        try {
            return events.poll(timeout, timeoutUnit);
        } catch (final InterruptedException e) {
            logger.info(EXCEPTION.getMarker(), "Interrupted while polling event stream queue: ", e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Returns true if the parser is not done parsing all event files or if there are still events in the queue.
     *
     * @return true if there are more events, either now or in the future
     */
    public boolean hasMoreEvents() {
        return !doneParsing || !events.isEmpty();
    }

    /**
     * Parsing event stream files from a specific folder with a search timestamp,
     * then playback transactions inside those events
     */
    private void eventPlayback() {
        parseEventFolder();
        doneParsing = true;
        logger.info(EVENT_PARSER.getMarker(), "Recovered {} events from stream file", eventCounter::get);
    }

    private void parseEventFolder() {
        logger.info(EVENT_PARSER.getMarker(), "Loading event file from {} ", () -> eventStreamDir);

        final LinkedList<Path> filesBeforeEndTime = getFilesBeforeEndTime();

        for (final ListIterator<Path> it = filesBeforeEndTime.listIterator(); it.hasNext(); ) {
            final Path currentFile = it.next();

            if (it.hasNext()) {
                // If the next file is after the start time, we must include this file
                // to make sure we parse all files that could have events in the time range
                final Path nextFile = filesBeforeEndTime.get(it.nextIndex());
                if (timestamp(nextFile).isAfter(startTimestamp)) {
                    readEventsFromFile(currentFile);
                }
            } else {
                // Always parse the last file. There is no way to know the
                // timestamp of the last event without reading the events in the file
                readEventsFromFile(currentFile);
            }
        }
    }

    private void sendInitialHash(final Path path) {
        if (!initialHashSent) {
            final Hash initialHash = readStartRunningHashFromStreamFile(path.toFile(), EventStreamType.getInstance());
            logger.info(EVENT_PARSER.getMarker(), "Set init hash as {}", initialHash);
            hashConsumer.accept(initialHash);
            initialHashSent = true;
        }
    }

    private void readEventsFromFile(final Path path) {
        logger.info(EVENT_PARSER.getMarker(), "Parsing events in file {}", () -> filename(path));
        sendInitialHash(path);
        parseEventStreamFile(path, this::handleParsedEvent, populateSettingsCommon);
    }

    private boolean handleParsedEvent(final Event e) {
        final EventImpl event = (EventImpl) e;
        final Instant consensusTimestamp = event.getConsensusTimestamp();

        if (consensusTimestamp.isAfter(endTimestamp)) {
            logger.info(
                    EVENT_PARSER.getMarker(),
                    "Parsing complete. Found event with consensusTimestamp after endTimestamp");
            return false;
        }

        event.setConsensus(true);

        addToQueue(event);
        eventCounter.getAndIncrement();
        return true;
    }

    private void addToQueue(final EventImpl event) {
        try {
            events.put(event);
        } catch (final InterruptedException ex) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Interrupted while adding a recovered event to the queue of parsed events",
                    ex);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @return the current number of events parsed from files
     */
    public int getEventCounter() {
        return eventCounter.get();
    }

    private LinkedList<Path> getFilesBeforeEndTime() {
        final LinkedList<Path> filesBeforeEnd = new LinkedList<>();
        try (final Stream<Path> paths = Files.walk(eventStreamDir)) {
            paths.sorted(EventStreamParser::compare)
                    .filter(p -> EventStreamType.getInstance().isStreamFile(filename(p)))
                    .filter(this::isFileBeforeEndTimestamp)
                    .forEachOrdered(filesBeforeEnd::add);
        } catch (final IOException e) {
            logger.error("Unable to access {} for event playback", eventStreamDir, e);
        }
        return filesBeforeEnd;
    }

    private boolean isFileBeforeEndTimestamp(final Path path) {
        final Instant fileTimestamp = timestamp(path);
        if (fileTimestamp == null) {
            logger.warn("Not parsing file {} due to invalid timestamp format.", () -> filename(path));
            return false;
        }
        return fileTimestamp.isBefore(endTimestamp);
    }

    private void handleException(final Thread t, final Throwable e) {
        logger.error(EXCEPTION.getMarker(), "Error while parsing event files in {}", eventStreamDir, e);
    }
}
