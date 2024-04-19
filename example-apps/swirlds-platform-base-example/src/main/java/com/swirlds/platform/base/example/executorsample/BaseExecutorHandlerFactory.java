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

package com.swirlds.platform.base.example.executorsample;

import com.swirlds.base.internal.BaseExecutorFactory;
import com.swirlds.common.metrics.extensions.BaseExecutorFactoryMetrics;
import com.swirlds.platform.base.example.ext.BaseContext;
import com.swirlds.platform.base.example.server.HttpHandlerDefinition;
import com.swirlds.platform.base.example.server.HttpHandlerRegistry;
import com.swirlds.platform.base.example.server.PostTriggerHandler;
import java.util.Set;
import java.util.function.Consumer;

public class BaseExecutorHandlerFactory implements HttpHandlerRegistry {

    record TaskDefinition(int count, long durationInMs, boolean fail) {}

    @Override
    public Set<HttpHandlerDefinition> handlers(BaseContext context) {
        final BaseExecutorFactoryMetrics metricsInstaller = new BaseExecutorFactoryMetrics(context.metrics());

        final Consumer<TaskDefinition> addTasksHandler = createTaskExecutionHandler();

        final Consumer<Void> resetHandler = v -> {
            metricsInstaller.reset();
        };

        return Set.of(
                new PostTriggerHandler<>("/executor/call", context, TaskDefinition.class, addTasksHandler),
                new PostTriggerHandler<>("/executor/reset", context, Void.class, resetHandler));
    }

    private static Consumer<TaskDefinition> createTaskExecutionHandler() {
        final BaseExecutorFactory baseExecutorFactory = BaseExecutorFactory.getInstance();

        final Consumer<TaskDefinition> addTasksHandler = d -> {
            for (int i = 0; i < d.count; i++) {
                baseExecutorFactory.submit(() -> {
                    try {
                        Thread.sleep(d.durationInMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (d.fail) {
                        throw new RuntimeException("Task failed");
                    }
                });
            }
        };
        return addTasksHandler;
    }
}
