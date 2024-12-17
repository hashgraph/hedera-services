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

package com.swirlds.base.internal;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface that is supported by {@link com.swirlds.base.internal.BaseExecutorFactory} to provide additional
 * information about the task. Can be combined with {@link Runnable} or {@link java.util.concurrent.Callable}.
 */
public interface BaseTask {

    /**
     * Default type
     */
    String DEFAULT_TYPE = "unknown";

    /**
     * Get the type of the task.
     *
     * @return the type of the task
     */
    @NonNull
    default String getType() {
        return DEFAULT_TYPE;
    }
}
