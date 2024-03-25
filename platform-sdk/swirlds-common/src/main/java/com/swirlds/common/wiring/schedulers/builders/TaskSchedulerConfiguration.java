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

package com.swirlds.common.wiring.schedulers.builders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Configures a task scheduler.
 *
 * @param type                       the type of task scheduler, if null then {@link TaskSchedulerType#SEQUENTIAL} is
 *                                   used
 * @param unhandledTaskCapacity      the maximum number of unhandled tasks, or 0 if unbounded, if null then 0 is used
 * @param unhandledTaskMetricEnabled whether the unhandled task count metric should be enabled, if null than false is
 *                                   used
 * @param busyFractionMetricEnabled  whether the busy fraction metric should be enabled, if null then false is used
 * @param flushingEnabled            whether flushing is enabled, if null then false is used
 * @param squelchingEnabled          whether squelching is enabled, if null then false is used
 */
public record TaskSchedulerConfiguration(
        @Nullable TaskSchedulerType type,
        @Nullable Long unhandledTaskCapacity,
        @Nullable Boolean unhandledTaskMetricEnabled,
        @Nullable Boolean busyFractionMetricEnabled,
        @Nullable Boolean flushingEnabled,
        @Nullable Boolean squelchingEnabled) {

    /**
     * Parse a string representation of a task scheduler configuration.
     * <p>
     * Syntax is as follows:
     * <ul>
     *     <li>
     *         Zero or one values from the {@link TaskSchedulerType} enum, specifies the type of the task scheduler.
     *         If not present then the default is used.
     *     </li>
     *     <li>
     *         Zero or one string of the form "CAPACITY(1234)", specifies the maximum number of unhandled tasks.
     *     </li>
     *     <li>
     *         Zero or more values from the {@link TaskSchedulerConfigOption} enum, specifies the configuration options.
     *         Sets a boolean configuration option to true if the value is present, and false if the value is prefixed
     *         with a "!". If not present then the default is used.
     *     </li>
     * </ul>
     * Example: "SEQUENTIAL CAPACITY(500) !FLUSHABLE UNHANDLED_TASK_METRIC"
     * <p>
     * Note that default values are not specified within this class. Default values are the responsibility of the
     * {@link TaskSchedulerBuilder} class.
     *
     * @param string the string to parse
     * @return the parsed configuration
     */
    @NonNull
    public static TaskSchedulerConfiguration parse(@NonNull final String string) {
        TaskSchedulerType type = null;
        Long unhandledTaskCapacity = null;
        Boolean unhandledTaskMetricEnabled = null;
        Boolean busyFractionMetricEnabled = null;
        Boolean flushingEnabled = null;
        Boolean squelchingEnabled = null;

        final String[] parts = string.split(" ");
        for (final String part : parts) {
            final String strippedPart = part.strip();
            if (strippedPart.isEmpty()) {
                continue;
            }

            final TaskSchedulerType parsedType = tryToParseTaskSchedulerType(strippedPart);
            if (parsedType != null) {
                if (type != null) {
                    throw new IllegalStateException("Multiple task scheduler types specified: " + string);
                }
                type = parsedType;
                continue;
            }

            final Long parsedCapacity = tryToParseCapacity(strippedPart);
            if (parsedCapacity != null) {
                if (unhandledTaskCapacity != null) {
                    throw new IllegalStateException("Multiple capacities specified: " + string);
                }
                unhandledTaskCapacity = parsedCapacity;
                continue;
            }

            final Boolean parsedUnhandledTaskMetric = tryToParseUnhandledTaskMetric(strippedPart);
            if (parsedUnhandledTaskMetric != null) {
                if (unhandledTaskMetricEnabled != null) {
                    throw new IllegalStateException(
                            "Multiple unhandled task metric configurations specified: " + string);
                }
                unhandledTaskMetricEnabled = parsedUnhandledTaskMetric;
                continue;
            }

            final Boolean parsedBusyFractionMetric = tryToParseBusyFractionMetric(strippedPart);
            if (parsedBusyFractionMetric != null) {
                if (busyFractionMetricEnabled != null) {
                    throw new IllegalStateException(
                            "Multiple busy fraction metric configurations specified: " + string);
                }
                busyFractionMetricEnabled = parsedBusyFractionMetric;
                continue;
            }

            final Boolean parsedFlushing = tryToParseFlushing(strippedPart);
            if (parsedFlushing != null) {
                if (flushingEnabled != null) {
                    throw new IllegalStateException("Multiple flushing configurations specified: " + string);
                }
                flushingEnabled = parsedFlushing;
                continue;
            }

            final Boolean parsedSquelching = tryToParseSquelching(strippedPart);
            if (parsedSquelching != null) {
                if (squelchingEnabled != null) {
                    throw new IllegalStateException("Multiple squelching configurations specified: " + string);
                }
                squelchingEnabled = parsedSquelching;
            }
        }

        return new TaskSchedulerConfiguration(
                type,
                unhandledTaskCapacity,
                unhandledTaskMetricEnabled,
                busyFractionMetricEnabled,
                flushingEnabled,
                squelchingEnabled);
    }

    /**
     * Try to parse a string as a {@link TaskSchedulerType}.
     *
     * @param string the string to parse
     * @return the parsed type, or null if the string is not a valid type
     */
    @Nullable
    private static TaskSchedulerType tryToParseTaskSchedulerType(@NonNull final String string) {
        try {
            return TaskSchedulerType.valueOf(string);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Try to parse a string as a capacity.
     *
     * @param string the string to parse
     * @return the parsed capacity, or null if the string is not a valid capacity
     */
    @Nullable
    private static Long tryToParseCapacity(@NonNull final String string) {
        if (string.startsWith(TaskSchedulerConfigOption.CAPACITY)) {

            try {
                // parse a string in the form "CAPACITY(1234)"
                final int openParenIndex = string.indexOf('(');
                final int closeParenIndex = string.indexOf(')');
                if (openParenIndex == -1 || closeParenIndex == -1) {
                    throw new IllegalArgumentException("Invalid capacity \"" + string + "\"");
                }
                final String capacityString = string.substring(openParenIndex + 1, closeParenIndex);
                return Long.parseLong(capacityString);
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("Invalid capacity \"" + string + "\"", e);
            }
        }
        return null;
    }

    /**
     * Try to parse a string as an unhandled task metric configuration.
     *
     * @param string the string to parse
     * @return the parsed configuration, or null if the string is not a valid configuration
     */
    private static Boolean tryToParseUnhandledTaskMetric(@NonNull final String string) {
        if (string.equals(TaskSchedulerConfigOption.UNHANDLED_TASK_METRIC.toString())) {
            return true;
        } else if (string.equals("!" + TaskSchedulerConfigOption.UNHANDLED_TASK_METRIC)) {
            return false;
        }
        return null;
    }

    /**
     * Try to parse a string as a busy fraction metric configuration.
     *
     * @param string the string to parse
     * @return the parsed configuration, or null if the string is not a valid configuration
     */
    private static Boolean tryToParseBusyFractionMetric(@NonNull final String string) {
        if (string.equals(TaskSchedulerConfigOption.BUSY_FRACTION_METRIC.toString())) {
            return true;
        } else if (string.equals("!" + TaskSchedulerConfigOption.BUSY_FRACTION_METRIC)) {
            return false;
        }
        return null;
    }

    /**
     * Try to parse a string as a flushing configuration.
     *
     * @param string the string to parse
     * @return the parsed configuration, or null if the string is not a valid configuration
     */
    private static Boolean tryToParseFlushing(@NonNull final String string) {
        if (string.equals(TaskSchedulerConfigOption.FLUSHABLE.toString())) {
            return true;
        } else if (string.equals("!" + TaskSchedulerConfigOption.FLUSHABLE)) {
            return false;
        }
        return null;
    }

    /**
     * Try to parse a string as a squelching configuration.
     *
     * @param string the string to parse
     * @return the parsed configuration, or null if the string is not a valid configuration
     */
    private static Boolean tryToParseSquelching(@NonNull final String string) {
        if (string.equals(TaskSchedulerConfigOption.SQUELCHABLE.toString())) {
            return true;
        } else if (string.equals("!" + TaskSchedulerConfigOption.SQUELCHABLE)) {
            return false;
        }
        return null;
    }
}
