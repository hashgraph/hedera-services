/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.settings;

import static com.swirlds.common.utility.Units.DAYS_TO_HOURS;
import static com.swirlds.common.utility.Units.HOURS_TO_MINUTES;
import static com.swirlds.common.utility.Units.MICROSECONDS_TO_NANOSECONDS;
import static com.swirlds.common.utility.Units.MILLISECONDS_TO_NANOSECONDS;
import static com.swirlds.common.utility.Units.MINUTES_TO_SECONDS;
import static com.swirlds.common.utility.Units.NANOSECONDS_TO_SECONDS;
import static com.swirlds.common.utility.Units.SECONDS_TO_NANOSECONDS;
import static com.swirlds.common.utility.Units.WEEKS_TO_DAYS;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class contains utilities for parsing various types of settings.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public final class ParsingUtils {

    /**
     * Regular expression for parsing durations. Looks for a number (with our without a decimal) followed by a unit.
     */
    private static final Pattern durationRegex = Pattern.compile("^\\s*(\\d*\\.?\\d*)\\s*([a-zA-Z]+)\\s*$");

    private ParsingUtils() {}

    /**
     * Make an attempt to parse a duration using default deserialization.
     *
     * @param str
     * 		the string that is expected to contain a duration
     * @return a Duration object if one can be parsed, otherwise null;
     */
    private static Duration attemptDefaultDurationDeserialization(final String str) {
        try {
            return Duration.parse(str);
        } catch (final DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Parse a duration from a string.
     * <p>
     * For large durations (i.e. when the number of nanoseconds exceeds {@link Long#MAX_VALUE}), the duration
     * returned will be rounded unless the duration is written using {@link Duration#toString()}.
     * Rounding process is deterministic.
     * <p>
     * This parser currently utilizes a regex which may have superlinear time complexity
     * for arbitrary input. Until that is addressed, do not use this parser on untrusted strings.
     *
     * @param str
     * 		a string containing a duration
     * @return a Duration
     * @throws SettingsException
     * 		if there is a problem parsing the string
     */
    public static Duration parseDuration(final String str) {

        final Matcher matcher = durationRegex.matcher(str);

        if (matcher.find()) {

            final double magnitude = Double.parseDouble(matcher.group(1));
            final String unit = matcher.group(2).trim().toLowerCase();

            final long toNanoseconds;

            switch (unit) {
                case "ns":
                case "nano":
                case "nanos":
                case "nanosecond":
                case "nanoseconds":
                case "nanosec":
                case "nanosecs":
                    toNanoseconds = 1;
                    break;

                case "us":
                case "micro":
                case "micros":
                case "microsecond":
                case "microseconds":
                case "microsec":
                case "microsecs":
                    toNanoseconds = MICROSECONDS_TO_NANOSECONDS;
                    break;

                case "ms":
                case "milli":
                case "millis":
                case "millisecond":
                case "milliseconds":
                case "millisec":
                case "millisecs":
                    toNanoseconds = MILLISECONDS_TO_NANOSECONDS;
                    break;

                case "s":
                case "second":
                case "seconds":
                case "sec":
                case "secs":
                    toNanoseconds = SECONDS_TO_NANOSECONDS;
                    break;

                case "m":
                case "minute":
                case "minutes":
                case "min":
                case "mins":
                    toNanoseconds = (long) MINUTES_TO_SECONDS * SECONDS_TO_NANOSECONDS;
                    break;

                case "h":
                case "hour":
                case "hours":
                    toNanoseconds = (long) HOURS_TO_MINUTES * MINUTES_TO_SECONDS * SECONDS_TO_NANOSECONDS;
                    break;

                case "d":
                case "day":
                case "days":
                    toNanoseconds =
                            (long) DAYS_TO_HOURS * HOURS_TO_MINUTES * MINUTES_TO_SECONDS * SECONDS_TO_NANOSECONDS;
                    break;

                case "w":
                case "week":
                case "weeks":
                    toNanoseconds = (long) WEEKS_TO_DAYS
                            * DAYS_TO_HOURS
                            * HOURS_TO_MINUTES
                            * MINUTES_TO_SECONDS
                            * SECONDS_TO_NANOSECONDS;
                    break;

                default:
                    final Duration duration = attemptDefaultDurationDeserialization(str);
                    if (duration == null) {
                        throw new SettingsException("Invalid duration format, unrecognized unit \"" + unit + "\"");
                    }
                    return duration;
            }

            final double totalNanoseconds = magnitude * toNanoseconds;
            if (totalNanoseconds > Long.MAX_VALUE) {
                // If a long is unable to hold the required nanoseconds then lower returned resolution to seconds.
                final double toSeconds = toNanoseconds * NANOSECONDS_TO_SECONDS;
                final long seconds = (long) (magnitude * toSeconds);
                return Duration.ofSeconds(seconds);
            }

            return Duration.ofNanos((long) totalNanoseconds);

        } else {
            final Duration duration = attemptDefaultDurationDeserialization(str);
            if (duration == null) {
                throw new SettingsException("Invalid duration format, unable to parse \"" + str + "\"");
            }
            return duration;
        }
    }
}
