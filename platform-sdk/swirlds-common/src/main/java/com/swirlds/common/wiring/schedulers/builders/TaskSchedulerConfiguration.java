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

// TODO unit test

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
     * <p>
     * Each parameter is optional. The order of the parameters is not important. The parameters are separated by commas.
     * Each parameter is a key value pair separated by a space. The key is the name of the parameter, the value is the
     * value of the parameter. The key is case sensitive. The value is case sensitive.
     * <p>
     * Example: "type SEQUENTIAL, unhandledTaskCapacity 100, unhandledTaskMetricEnabled true"
     *
     * @param string the string to parse
     * @return the parsed configuration
     */
    @NonNull
    public static TaskSchedulerConfiguration parse(@NonNull final String string) {

        // TODO switch parser

        TaskSchedulerType type = null;
        Long unhandledTaskCapacity = null;
        Boolean unhandledTaskMetricEnabled = null;
        Boolean busyFractionMetricEnabled = null;
        Boolean flushingEnabled = null;
        Boolean squelchingEnabled = null;

        try {
            final String[] parts = string.split(",");
            for (final String part : parts) {
                final String[] keyValue = part.strip().split(" ");
                if (keyValue.length != 2) {
                    throw new IllegalArgumentException("Invalid configuration \"" + string + "\", part \"" + part
                            + "\" should be a space separated key value pair");
                }
                final String key = keyValue[0];
                final String value = keyValue[1];

                switch (key) {
                    case "type" -> type = TaskSchedulerType.valueOf(value);
                    case "unhandledTaskCapacity" -> unhandledTaskCapacity = Long.parseLong(value);
                    case "unhandledTaskMetricEnabled" -> unhandledTaskMetricEnabled = Boolean.parseBoolean(value);
                    case "busyFractionMetricEnabled" -> busyFractionMetricEnabled = Boolean.parseBoolean(value);
                    case "flushingEnabled" -> flushingEnabled = Boolean.parseBoolean(value);
                    case "squelchingEnabled" -> squelchingEnabled = Boolean.parseBoolean(value);
                    default -> throw new IllegalArgumentException(
                            "Invalid configuration \"" + string + "\", unknown key \"" + key + "\"");
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
