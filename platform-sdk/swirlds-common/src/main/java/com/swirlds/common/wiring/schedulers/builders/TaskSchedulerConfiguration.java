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

        try {
            final String[] parts = string.split(" ");
            for (final String part : parts) {
                String strippedPart = part.strip();

                try {
                    // if the part can be parsed as a TaskSchedulerType, then it is the type
                    type = TaskSchedulerType.valueOf(strippedPart);
                    continue;
                } catch (final IllegalArgumentException e) {
                    // ignore
                }

                if (strippedPart.startsWith(TaskSchedulerConfigOption.CAPACITY)) {
                    try {
                        // parse a string in the form "CAPACITY(1234)"
                        final int openParenIndex = strippedPart.indexOf('(');
                        final int closeParenIndex = strippedPart.indexOf(')');
                        if (openParenIndex == -1 || closeParenIndex == -1) {
                            throw new IllegalArgumentException("Invalid capacity \"" + strippedPart + "\"");
                        }
                        final String capacityString = strippedPart.substring(openParenIndex + 1, closeParenIndex);
                        unhandledTaskCapacity = Long.parseLong(capacityString);
                    } catch (final NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid capacity \"" + strippedPart + "\"", e);
                    }
                }

                try {
                    // All other values must be TaskSchedulerConfigOption values.
                    boolean value = true;
                    if (strippedPart.startsWith("!")) {
                        value = false;
                        strippedPart = strippedPart.substring(1);
                    }

                    final TaskSchedulerConfigOption option = TaskSchedulerConfigOption.valueOf(strippedPart);

                    switch (option) {
                        case UNHANDLED_TASK_METRIC -> unhandledTaskMetricEnabled = value;
                        case BUSY_FRACTION_METRIC -> busyFractionMetricEnabled = value;
                        case FLUSHABLE -> flushingEnabled = value;
                        case SQUELCHABLE -> squelchingEnabled = value;
                    }
                } catch (final IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid task scheduler configuration \"" + string + "\"", e);
                }
            }
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("Invalid configuration \"" + string + "\"", e);
        }

        return new TaskSchedulerConfiguration(
                type,
                unhandledTaskCapacity,
                unhandledTaskMetricEnabled,
                busyFractionMetricEnabled,
                flushingEnabled,
                squelchingEnabled);
    }
}
