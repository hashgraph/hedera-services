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

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.parseStreamFile;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.readFirstIntFromFile;
import static com.swirlds.logging.LogMarker.EVENT_PARSER;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.platform.EventConsumer;
import com.swirlds.platform.Settings;
import com.swirlds.platform.internal.EventImpl;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parses event stream files and passes the events to a consumer.
 */
public final class EventFileParser {

    /**
     * current event stream version
     */
    public static final int EVENT_STREAM_FILE_VERSION = 5;

    private static final Logger logger = LogManager.getLogger(EventFileParser.class);

    private EventFileParser() {}

    /**
     * Parse event stream file (.evts) version 5
     * and put parsed event objects into eventHandler
     *
     * @param path
     * 		event stream file
     * @param eventConsumer
     * 		a consumer of parsed events
     * @param populateSettingsCommon
     * 		should be true when this method is called from a utility program which may not read the settings.txt file
     * 		and follow the normal initialization routines in the Browser class
     * @return return false if experienced any error otherwise return true
     */
    public static boolean parseEventStreamFile(
            final Path path, final EventConsumer eventConsumer, final boolean populateSettingsCommon) {
        if (populateSettingsCommon) {
            // Populate the SettingsCommon object with the defaults or configured values from the Settings class.
            // This is necessary because this method may be called from a utility program which may or may not
            // read the settings.txt file and follow the normal initialization routines in the Browser class.
            Settings.populateSettingsCommon();
        }
        try {
            final int fileVersion = readFirstIntFromFile(path.toFile());
            if (fileVersion == EVENT_STREAM_FILE_VERSION) {
                // should return false if any parsing error happened
                // so the whole parsing process can stop
                return parseEventStreamV5(path, eventConsumer);
            } else {
                logger.info(
                        EVENT_PARSER.getMarker(),
                        "failed to parse file {} whose version is {}",
                        path.getFileName().toString(),
                        fileVersion);

                return false;
            }
        } catch (final IOException e) {
            logger.info(EXCEPTION.getMarker(), "Unexpected", e);
            return false;
        }
    }

    /**
     * Parse event stream file (.evts) version 5
     * and put parsed event objects into eventHandler
     *
     * @param path
     * 		event stream file
     * @param eventConsumer
     * 		a consumer of parsed events
     */
    private static boolean parseEventStreamV5(final Path path, final EventConsumer eventConsumer) {
        final Iterator<SelfSerializable> iterator = parseStreamFile(path.toFile(), EventStreamType.getInstance());
        boolean isStartRunningHash = true;
        while (iterator.hasNext()) {
            final SelfSerializable object = iterator.next();
            if (object == null) { // iterator.next() returns null if any error occurred
                return false;
            }
            if (isStartRunningHash) {
                logger.info(
                        EVENT_PARSER.getMarker(),
                        "From file {} read startRunningHash = {}",
                        path.getFileName().toString(),
                        object);
                isStartRunningHash = false;
            } else if (object instanceof Hash) {
                logger.info(
                        EVENT_PARSER.getMarker(),
                        "From file {} read endRunningHash = {}",
                        path.getFileName().toString(),
                        object);
            } else {
                final EventImpl event = new EventImpl((DetailedConsensusEvent) object);
                // set event's baseHash
                CryptographyHolder.get().digestSync(event.getBaseEventHashedData());
                eventConsumer.consume(event);
            }
        }
        return true;
    }
}
