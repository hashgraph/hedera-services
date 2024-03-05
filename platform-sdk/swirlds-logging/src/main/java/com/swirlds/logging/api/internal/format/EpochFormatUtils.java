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

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.time.ZoneId;
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
     * The formatter for the timestamp.
     */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

    private static final String BROKEN_TIMESTAMP = "BROKEN-TIMESTAMP          ";

    private EpochFormatUtils() {}

    /**
     * Returns the String representation matching {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}
     * for the epoc value {@code timestamp}
     */
    public static @NonNull String timestampAsString(final long timestamp) {
        try {
            final StringBuilder sb = new StringBuilder(26);
            sb.append(FORMATTER.format(Instant.ofEpochMilli(timestamp)));
            sb.append(getFiller(26 - sb.length()));
            return sb.toString();
        } catch (final Throwable e) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to format instant", e);
            return BROKEN_TIMESTAMP;
        }
    }

    private static @NonNull String getFiller(final int length) {
        if (length == 0) {
            return "";
        } else if (length == 1) {
            return " ";
        } else if (length == 2) {
            return "  ";
        } else if (length == 3) {
            return "   ";
        } else if (length == 4) {
            return "    ";
        } else if (length == 5) {
            return "     ";
        } else if (length == 6) {
            return "      ";
        } else if (length == 7) {
            return "       ";
        } else if (length == 8) {
            return "        ";
        } else if (length == 9) {
            return "         ";
        } else if (length == 10) {
            return "          ";
        } else if (length == 11) {
            return "           ";
        } else if (length == 12) {
            return "            ";
        } else if (length == 13) {
            return "             ";
        } else if (length == 14) {
            return "              ";
        } else if (length == 15) {
            return "               ";
        } else if (length == 16) {
            return "                ";
        } else if (length == 17) {
            return "                 ";
        } else if (length == 18) {
            return "                  ";
        } else if (length == 19) {
            return "                   ";
        } else if (length == 20) {
            return "                    ";
        } else if (length == 21) {
            return "                     ";
        } else if (length == 22) {
            return "                      ";
        } else if (length == 23) {
            return "                       ";
        } else if (length == 24) {
            return "                        ";
        } else if (length == 25) {
            return "                         ";
        } else if (length == 26) {
            return "                          ";
        } else {
            throw new IllegalArgumentException("Unsupported length: " + length);
        }
    }
}
