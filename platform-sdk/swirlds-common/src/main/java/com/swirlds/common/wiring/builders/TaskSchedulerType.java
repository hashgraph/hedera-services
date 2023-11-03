/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.builders;

/**
 * Various types of task schedulers.
 */
public enum TaskSchedulerType {
    /**
     * Tasks are executed on a fork join pool. Ordering is not guaranteed.
     */
    CONCURRENT,
    /**
     * Tasks are executed in a fork join pool one at a time in the order they were enqueued. There is a happens before
     * relationship between each task.
     */
    SEQUENTIAL,
    /**
     * Tasks are executed on a dedicated thread one at a time in the order they were enqueued. There is a happens before
     * relationship between each task. This scheduler type has very similar semantics as {@link #SEQUENTIAL}, although
     * the implementation and performance characteristics are not identical.
     */
    SEQUENTIAL_QUEUE,
    /**
     * Tasks are executed immediately on the callers thread. There is no queue for tasks waiting to be handled (logical
     * or otherwise). Useful for scenarios where tasks are extremely small and not worth the scheduling overhead.
     * <p>
     * Only a single task scheduler is permitted to send data to a direct task scheduler. {@link #CONCURRENT} task
     * schedulers are forbidden from sending data to a direct task scheduler. These constraints are enforced by the
     * framework.
     */
    DIRECT
}
