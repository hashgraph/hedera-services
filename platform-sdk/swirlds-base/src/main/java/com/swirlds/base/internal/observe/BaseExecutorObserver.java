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

package com.swirlds.base.internal.observe;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * An observer for the base executor (see {@link com.swirlds.base.internal.BaseExecutorFactory}). The observer is
 * notified when a task is submitted, started, done, or failed.
 */
public interface BaseExecutorObserver {

    /**
     * Called when a task is submitted.
     *
     * @param taskDefinition the task definition
     */
    void onTaskSubmitted(@NonNull BaseTaskDefinition taskDefinition);

    /**
     * Called when a task is started.
     * @param taskDefinition the task definition
     */
    void onTaskStarted(@NonNull BaseTaskDefinition taskDefinition);

    /**
     * Called when a task is done.
     * @param taskDefinition the task definition
     * @param duration the duration of the task
     */
    void onTaskDone(@NonNull BaseTaskDefinition taskDefinition, @NonNull Duration duration);

    /**
     * Called when a task is failed.
     * @param taskDefinition the task definition
     * @param duration the duration of the task
     */
    void onTaskFailed(@NonNull BaseTaskDefinition taskDefinition, @NonNull Duration duration);
}
