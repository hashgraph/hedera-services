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

import com.swirlds.base.internal.BaseTask;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * A task definition that contains the id and type of the task.
 *
 * @param id   the id
 * @param type the type
 */
public record BaseTaskDefinition(@NonNull UUID id, @NonNull String type) {

    /**
     * Constructs a new task definition.
     *
     * @param id   the id
     * @param type the type
     */
    public BaseTaskDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /**
     * Constructs a new task for the given runnable.
     *
     * @param runnable the runnable
     * @return the task definition
     */
    @NonNull
    public static BaseTaskDefinition of(@NonNull final Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        final UUID id = UUID.randomUUID();
        if (runnable instanceof BaseTask baseTask) {
            return new BaseTaskDefinition(id, baseTask.getType());
        }
        return new BaseTaskDefinition(id, BaseTask.DEFAULT_TYPE);
    }

    /**
     * Constructs a new task for the given callable.
     * @param callable the callable
     * @return the task definition
     * @param <V> the type of the callable
     */
    @NonNull
    public static <V> BaseTaskDefinition of(@NonNull final Callable<V> callable) {
        Objects.requireNonNull(callable, "callable must not be null");
        final UUID id = UUID.randomUUID();
        if (callable instanceof BaseTask baseTask) {
            return new BaseTaskDefinition(id, baseTask.getType());
        }
        return new BaseTaskDefinition(id, BaseTask.DEFAULT_TYPE);
    }
}
