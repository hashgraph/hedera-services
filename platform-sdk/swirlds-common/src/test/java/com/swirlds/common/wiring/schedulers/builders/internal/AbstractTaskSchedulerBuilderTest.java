/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.schedulers.builders.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractTaskSchedulerBuilderTest {

    private final AbstractTaskSchedulerBuilder<Void> builder =
            new AbstractTaskSchedulerBuilder<Void>(
                    mock(PlatformContext.class),
                    mock(TraceableWiringModel.class),
                    "testScheduler",
                    new ForkJoinPool()) {
                @NonNull
                @Override
                public TaskScheduler<Void> build() {
                    return null;
                }
            };

    @Test
    @DisplayName("Test withType")
    void withType() {
        // when the type is DIRECT or DIRECT_THREADSAFE and has a unhandledTaskCapacity set
        builder.withUnhandledTaskCapacity(2);
        assertThrows(IllegalArgumentException.class, () -> builder.withType(TaskSchedulerType.DIRECT));
        assertThrows(IllegalArgumentException.class, () -> builder.withType(TaskSchedulerType.DIRECT_THREADSAFE));
        // when the type is DIRECT or DIRECT_THREADSAFE and doesn't have a unhandledTaskCapacity
        builder.withUnhandledTaskCapacity(1);
        assertDoesNotThrow(() -> builder.withType(TaskSchedulerType.DIRECT));
        assertDoesNotThrow(() -> builder.withType(TaskSchedulerType.DIRECT_THREADSAFE));
    }

    @Test
    @DisplayName("Test withUnhandledTaskCapacity")
    void withUnhandledTaskCapacity() {
        // TaskSchedulerType is set to DIRECT
        builder.withType(TaskSchedulerType.DIRECT);
        assertThrows(IllegalArgumentException.class, () -> builder.withUnhandledTaskCapacity(3));
        // TaskSchedulerType is set to DIRECT_THREADSAFE
        builder.withType(TaskSchedulerType.DIRECT_THREADSAFE);
        assertThrows(IllegalArgumentException.class, () -> builder.withUnhandledTaskCapacity(3));
        // Other TaskSchedulerType
        builder.withType(TaskSchedulerType.SEQUENTIAL);
        assertDoesNotThrow(() -> builder.withUnhandledTaskCapacity(2));
    }
}
