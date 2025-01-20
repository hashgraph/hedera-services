/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.schedulers.builders;

/**
 * Various configuration options for a task scheduler. Note that the task scheduler type uses values from
 * {@link TaskSchedulerType}, and that the unhandled task capacity is represented as an integer value.
 */
public enum TaskSchedulerConfigOption {
    /**
     * If present, a metric will be created to show the number of unhandled tasks.
     */
    UNHANDLED_TASK_METRIC,
    /**
     * If present, a metric will be created to show the fraction of time the scheduler is busy.
     */
    BUSY_FRACTION_METRIC,
    /**
     * If present, the scheduler will be capable of being flushed.
     */
    FLUSHABLE,
    /**
     * If present, the scheduler will be capable of squelching.
     */
    SQUELCHABLE;

    /**
     * This is not defined as an enum constant because it is used in a special way. To specify the capacity,
     * use a string in the form "CAPACITY(1234)" where 1234 is the desired capacity.
     */
    public static final String CAPACITY = "CAPACITY";
}
