/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getTimeStampFromFileName;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.parseStreamFile;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readFirstIntFromFile;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.stream.EventStreamManager;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.common.system.events.Event;
import com.swirlds.logging.payloads.StreamParseErrorPayload;
import com.swirlds.platform.internal.EventImpl;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is used for state recovery.
 * Parse event stream files and playback event on given SwirldState object
 *
 * Running a different thread, parsing event files from given directory.
 * Searching event whose consensus timestamp following in the range of
 * start timestamp (exclusive) and end timestamp (inclusive), i.e., (startTimestamp, endTimestamp]
 */
public class StreamEventParser extends Thread {
    /**
     * current event stream version
     */
    public static final int EVENT_STREAM_FILE_VERSION = 5;

    private static final Logger logger = LogManager.getLogger(StreamEventParser.class);
    private static final int POLL_WAIT = 5000;
    private final LinkedBlockingQueue<EventImpl> events = new LinkedBlockingQueue<>();
    private final String fileDir;
    private final Instant startTimestamp;
    private final Instant endTimestamp;
    private final EventStreamManager<EventImpl> eventStreamManager;
    private boolean isParsingDone = false;
    private long eventsCounter;
    private EventImpl prevParsedEvent;
    private boolean setInitRunningHashAlready = false;
    private Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

    StreamEventParser(
            final String fileDir,
            final Instant startTimestamp,
            final Instant endTimestamp,
            final EventStreamManager<EventImpl> eventStreamManager) {
        this.fileDir = fileDir;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.eventStreamManager = eventStreamManager;
        eventStreamManager.setInitialHash(initialHash);
    }

    /**
     * Parse event stream file (.evts) version 5
     * and put parsed event objects into eventHandler
     *
     * @param file
     * 		event stream file
     * @param eventHandler
     * 		call back function for handling parsed event object
     * @param populateSettingsCommon
     * 		should be true when this method is called from a utility program which may not read the settings.txt file
     * 		and
     * 		follow the normal initialization routines in the Browser class
     * @return return false if experienced any error otherwise return true
     */
    public static boolean parseEventStreamFile(
            final File file, final EventConsumer eventHandler, final boolean populateSettingsCommon) {
        if (populateSettingsCommon) {
            // Populate the SettingsCommon object with the defaults or configured values from the Settings class.
            // This is necessary because this method may be called from a utility program which may or may not
            // read the settings.txt file and follow the normal initialization routines in the Browser class.
            Settings.populateSettingsCommon();
        }
        try {
            final int fileVersion = readFirstIntFromFile(file);
            if (fileVersion == EVENT_STREAM_FILE_VERSION) {
                // should return false if any parsing error happened
                // so the whole parsing process can stop
                return parseEventStreamV5(file, eventHandler);
            } else {
                logger.info(
                        EVENT_PARSER.getMarker(),
                        "failed to parse file {} whose version is {}",
                        file::getName,
                        () -> fileVersion);

                return false;
            }
        } catch (IOException e) {
            logger.info(EXCEPTION.getMarker(), "Unexpected", e);
            return false;
        }
    }

    /**
     * Parse event stream file (.evts) version 5
     * and put parsed event objects into eventHandler
     *
     * @param file
     * 		event stream file
     * @param eventHandler
     * 		call back function for handling parsed event object
     */
    private static boolean parseEventStreamV5(final File file, final EventConsumer eventHandler) {
        Iterator<SelfSerializable> iterator = parseStreamFile(file, EventStreamType.getInstance());
        boolean isStartRunningHash = true;
        while (iterator.hasNext()) {
            SelfSerializable object = iterator.next();
            if (object == null) { // iterator.next() returns null if any error occurred
                return false;
            }
            if (isStartRunningHash) {
                logger.info(
                        EVENT_PARSER.getMarker(),
                        "From file {} read startRunningHash = {}",
                        file::getName,
                        () -> object);
                isStartRunningHash = false;
            } else if (object instanceof Hash) {
                logger.info(
                        EVENT_PARSER.getMarker(), "From file {} read endRunningHash = {}", file::getName, () -> object);
            } else {
                EventImpl event = new EventImpl((DetailedConsensusEvent) object);
                // set event's baseHash
                CryptographyHolder.get().digestSync(event.getBaseEventHashedData());
                eventHandler.consume(event);
            }
        }
        return true;
    }

    public EventImpl getNextEvent() {
        try {
            return events.poll(POLL_WAIT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.info(EXCEPTION.getMarker(), "Unexpected", e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public long getEventsCounter() {
        return eventsCounter;
    }

    /**
     * whether we got all events
     *
     * @return whether the parser has processed all events
     */
    public boolean noMoreEvents() {
        return isParsingDone && events.isEmpty();
    }

    /**
     * Parsing event stream files from a specific folder with a search timestamp,
     * then playback transactions inside those events
     */
    private void eventPlayback() {
        parseEventFolder(this.fileDir, this::handleEvent);
        handleEvent(null); // push the last prevParsedEvent to queue
        isParsingDone = true;
        logger.info(EVENT_PARSER.getMarker(), "Recovered {} event from stream file", () -> eventsCounter);
    }

    /**
     * Parsing event stream files from a specific folder with a search timestamp
     *
     * @param fileDir
     * 		directory where event files are stored
     * @param eventHandler
     * 		call back function for handling parsed event object
     */
    private void parseEventFolder(String fileDir, EventConsumer eventHandler) {
        if (fileDir != null) {
            logger.info(EVENT_PARSER.getMarker(), "Loading event file from path {} ", () -> fileDir);

            // Only get .evts files from the directory
            File folder = new File(fileDir);
            File[] files = folder.listFiles(
                    (dir, name) -> EventStreamType.getInstance().isStreamFile(name));
            logger.info(EVENT_PARSER.getMarker(), "Files before sorting {}", () -> Arrays.toString(files));
            // sort file by its name and timestamp order
            Arrays.sort(files);

            for (int i = 0; i < files.length; i++) {
                String fullPathName = files[i].getAbsolutePath();
                Instant currTimestamp = getTimeStampFromFileName(files[i].getName());

                if (currTimestamp.compareTo(endTimestamp) > 0) {
                    logger.info(
                            EVENT_PARSER.getMarker(),
                            "Search event file ended because file timestamp {} is after endTimestamp {}",
                            () -> currTimestamp,
                            () -> endTimestamp);
                    return;
                }

                if (!processEventFile(files, i, eventHandler)) {
                    logger.error(
                            EXCEPTION.getMarker(),
                            () -> new StreamParseErrorPayload("Experienced error during parsing file " + fullPathName));
                    return;
                }
            }
        }
    }

    /**
     * Processes a file in the file array:
     * for a file which is not the last file, we check whether we should skip it or not, and parse the file when needed;
     * for the last file, we always parse it.
     *
     * @param files
     * 		a file array
     * @param index
     * 		index of the file to be parsed
     * @param eventHandler
     * 		call back function for handling parsed event object
     * @return whether there is error when parsing the file
     */
    private boolean processEventFile(final File[] files, final int index, final EventConsumer eventHandler) {
        boolean result = true;
        if (index < files.length - 1) {
            // if this is not the last file, we can compare timestamp from the next file with startTimestamp
            Instant nextTimestamp = getTimeStampFromFileName(files[index + 1].getName());

            // if  startTimestamp < nextTimestamp, we should parse this file
            if (startTimestamp.compareTo(nextTimestamp) < 0) {
                result = parseEventFile(files[index], eventHandler);
            } else {
                // else we can skip this file
                logger.info(
                        EVENT_PARSER.getMarker(),
                        " Skip file {}: startTimestamp {} nextTimestamp {}",
                        files[index]::getName,
                        () -> startTimestamp,
                        () -> nextTimestamp);
                // keep tracking the endRunningHash of the previous event file, so it can be used as
                // the initial hash once we start generate recovered event files
                initialHash = readEndRunningHash(files[index]);
            }
        } else {
            // last file will always be opened and parsed since we could not know
            // what is the timestamp of the last event within the file
            result = parseEventFile(files[index], eventHandler);
        }
        return result;
    }

    /**
     * Parse event stream file (.evts) version 5
     * and put parsed event objects into eventHandler
     *
     * If startTimestamp is null then return all parsed events
     *
     * @param file
     * 		event stream file
     * @param eventHandler
     * 		call back function for handling parsed event object
     * @return return false if experienced any error otherwise return true
     */
    private boolean parseEventFile(final File file, final EventConsumer eventHandler) {
        if (!file.exists()) {
            logger.error(EXCEPTION.getMarker(), "File {} does not exist: ", file::getName);
            return false;
        }
        logger.info(EVENT_PARSER.getMarker(), "Processing file {}", file::getName);

        if (!EventStreamType.getInstance().isStreamFile(file)) {
            logger.error(
                    EXCEPTION.getMarker(), "parseEventFile fails :: {} is not an event stream file", file::getName);
            return false;
        }

        return parseEventStreamFile(file, eventHandler, false);
    }

    private void addToQueue(EventImpl event) {
        if (event != null) {
            events.offer(event);
            eventsCounter++;
        }
    }

    /**
     * @param event
     * 		Event to be handled
     * @return indicate whether should continue parse event from input stream
     */
    private boolean handleEvent(final Event event) {
        if (event == null) {
            logger.info(EVENT_PARSER.getMarker(), "Finished parsing events");
            if (prevParsedEvent != null) {
                logger.info(
                        EVENT_PARSER.getMarker(),
                        "Last recovered event consensus timestamp {}, round {}",
                        prevParsedEvent.getConsensusTimestamp(),
                        prevParsedEvent.getRoundReceived());
                addToQueue(prevParsedEvent);
            }
            return false;
        }

        final EventImpl eventImpl = (EventImpl) event;

        // events saved in stream file are consensus events
        // we need to setConsensus to be true, otherwise we will got `ConsensusRoundHandler queue has non consensus
        // event` error in ConsensusRoundHandler.applyConsensusEventToState() when handling this event during state
        // recovery
        eventImpl.setConsensus(true);

        Instant consensusTimestamp = eventImpl.getConsensusTimestamp();

        boolean shouldContinue;
        // Search criteria :
        // 		startTimestamp < consensusTimestamp <= endTimestamp
        //
        // startTimestamp < consensusTimestamp ->  startTimestamp isBefore consensusTimestamp
        // consensusTimestamp <= endTimestamp ->   consensusTimestamp is NOT after endTimestamp
        //
        // for event whose consensusTimestamp is before or equal to startTimestamp, we ignore such event,
        // because this event should not be played back in swirdsState
        // we cannot write such events to event stream files, because we only have eventsRunningHash loaded from signed
        // state. we must start to update eventsRunningHash for events whose consensus timestamp is after the
        // loaded signed state, and then start to write event stream file at the first complete window
        if (startTimestamp.isBefore(consensusTimestamp) && !consensusTimestamp.isAfter(endTimestamp)) {
            if (prevParsedEvent != null) {
                // this is not the first parsed event, push prevParsedEvent to queue
                addToQueue(prevParsedEvent);
            } else {
                // This is the first recovered event, also could be the first event within current file
                trySetInitialRunningHash();
            }
            prevParsedEvent = eventImpl;
            shouldContinue = true;
        } else if (consensusTimestamp.isAfter(endTimestamp)) {
            logger.info(EVENT_PARSER.getMarker(), "Search finished due to consensusTimestamp is after endTimestamp");
            shouldContinue = false;

        } else {
            // for event not playing back in swirldsState, insert to event stream manager to get
            // the same event stream file as the original one.
            // skip being played back by handleTransaction function
            logger.info(
                    EVENT_PARSER.getMarker(),
                    "Adding event {} to stream writer directly",
                    eventImpl.getConsensusTimestamp());
            shouldContinue = true;
            trySetInitialRunningHash();
            eventStreamManager.addEvent(eventImpl);
        }
        return shouldContinue;
    }

    /**
     * Set initial hash only once
     */
    private void trySetInitialRunningHash() {
        if (!setInitRunningHashAlready && initialHash != null) {
            logger.info(EVENT_PARSER.getMarker(), "Set init hash as {}", initialHash);
            eventStreamManager.setInitialHash(initialHash);
            setInitRunningHashAlready = true;
        }
    }

    /**
     * Iterate a stream file and extract its endRunningHash
     *
     * @param file
     * 		event stream file
     * @return return endRunning hash if found one or return null if could not
     */
    private Hash readEndRunningHash(final File file) {
        Iterator<SelfSerializable> iterator = parseStreamFile(file, EventStreamType.getInstance());
        boolean isStartRunningHash = true;
        while (iterator.hasNext()) {
            SelfSerializable object = iterator.next();
            if (object == null) { // iterator.next() returns null if any error occurred
                return null;
            }
            if (isStartRunningHash) {
                isStartRunningHash = false;
            } else if (object instanceof Hash) {
                logger.info(EVENT_PARSER.getMarker(), "Found file {} endRunningHash = {}", file::getName, () -> object);
                return (Hash) object;
            }
        }
        return null;
    }

    @Override
    public void run() {
        eventPlayback();
    }
}
