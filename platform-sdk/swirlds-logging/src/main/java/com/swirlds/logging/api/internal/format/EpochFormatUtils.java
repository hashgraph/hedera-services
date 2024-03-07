/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.api.internal.format;

import static java.time.ZoneOffset.UTC;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to help formatting epoc milliseconds like those coming from ({@link System#currentTimeMillis()}) to a
 * String representation matching  {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}
 */
public class EpochFormatUtils {

    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * Space filler values, so we can return a fixed size string
     */
    private static final String[] FILLERS = prepareFillers();

    /**
     * The formatter for the timestamp.
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(UTC);

    private static final String BROKEN_TIMESTAMP = "BROKEN-TIMESTAMP          ";
    public static final int DATE_FIELD_MAX_SIZE = 26;

    private EpochFormatUtils() {}

    /**
     * Returns the String representation matching {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME} for the epoc value
     * {@code timestamp}
     */
    public static @NonNull String timestampAsString(final long timestamp) {
        try {
            final StringBuilder sb = new StringBuilder(DATE_FIELD_MAX_SIZE);
            sb.append(FORMATTER.format(Instant.ofEpochMilli(timestamp)));
            sb.append(getFiller(DATE_FIELD_MAX_SIZE - sb.length()));
            return sb.toString();
        } catch (final Throwable e) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format instant", e);
            return BROKEN_TIMESTAMP;
        }
    }

    private static @NonNull String getFiller(final int length) {
        if (length < 0 || length > DATE_FIELD_MAX_SIZE) {
            throw new IllegalArgumentException("Unsupported length: " + length);
        }
        return FILLERS[length];
    }

    private static String[] prepareFillers() {
        final String[] fillers = new String[DATE_FIELD_MAX_SIZE + 1];
        fillers[0] = "";
        for (int i = 1; i <= DATE_FIELD_MAX_SIZE; i++) {
            fillers[i] = " ".repeat(i);
        }
        return fillers;
    }
}
