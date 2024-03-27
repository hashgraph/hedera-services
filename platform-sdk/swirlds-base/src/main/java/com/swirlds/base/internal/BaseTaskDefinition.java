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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;

public record BaseTaskDefinition(@NonNull UUID id, @NonNull String type) {

    public BaseTaskDefinition {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    @NonNull
    public static BaseTaskDefinition of(@NonNull Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        final UUID id = UUID.randomUUID();
        if (task instanceof BaseTask baseTask) {
            return new BaseTaskDefinition(id, baseTask.getType());
        }
        return new BaseTaskDefinition(id, BaseTask.DEFAULT_TYPE);
    }

    @NonNull
    public static <V> BaseTaskDefinition of(@NonNull Callable<V> task) {
        Objects.requireNonNull(task, "task must not be null");
        final UUID id = UUID.randomUUID();
        if (task instanceof BaseTask baseTask) {
            return new BaseTaskDefinition(id, baseTask.getType());
        }
        return new BaseTaskDefinition(id, BaseTask.DEFAULT_TYPE);
    }
}
