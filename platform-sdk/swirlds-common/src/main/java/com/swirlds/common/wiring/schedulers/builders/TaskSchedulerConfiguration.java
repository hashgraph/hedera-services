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
 */
public record TaskSchedulerConfiguration(
        @Nullable TaskSchedulerType type,
        @Nullable Long unhandledTaskCapacity,
        @Nullable Boolean unhandledTaskMetricEnabled,
        @Nullable Boolean busyFractionMetricEnabled) {

    /**
     * Parse a string representation of a task scheduler configuration.
     * <p>
     * TODO describe syntax
     *
     * @param string the string to parse
     * @return the parsed configuration
     */
    @NonNull
    public static TaskSchedulerConfiguration parse(@NonNull final String string) {
        return new TaskSchedulerConfiguration(null, null, null, null); // TODO
    }
}
