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

package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.utility.MultiHandler;
import com.swirlds.platform.componentframework.internal.ProcessorParts;
import com.swirlds.platform.componentframework.internal.QueueSubmitter;
import com.swirlds.platform.componentframework.internal.TaskProcessorUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A framework for setting up and running a set of {@link TaskProcessor}s. Each task processor will run in its own
 * thread that will continuously hand it tasks to process if available. The components that need to submit tasks should
 * do so via the submitter created by this framework. The steps to using this framework are:
 * <ol>
 *     <li>Create a {@link TaskProcessors} instance, declaring the processors in the constructor</li>
 *     <li>Request any submitter if other components have dependencies on task processors declared</li>
 *     <li>Add implementations for processors declared in the constructor</li>
 *     <li>Start the framework</li>
 *     <li>Stop the framework when done</li>
 * </ol>
 */
public class TaskProcessors {
    /** the different parts of task processors */
    private final Map<Class<? extends TaskProcessor>, ProcessorParts> parts = new HashMap<>();
    /** the default configuration for all task processors */
    private final QueueThreadConfiguration<Object> defaultConfiguration;
    /** has the framework been started */
    private boolean started = false;

    /**
     * Create a new TaskProcessors framework.
     *
     * @param defaultConfiguration
     * 		the default configuration for all task processors
     * @param taskProcessorDefs
     * 		the definitions of the task processors to create
     */
    public TaskProcessors(
            @NonNull final QueueThreadConfiguration<Object> defaultConfiguration,
            @NonNull final List<TaskProcessorConfig> taskProcessorDefs) {
        this.defaultConfiguration = Objects.requireNonNull(defaultConfiguration).copy();
        if (defaultConfiguration.getHandler() != null) {
            throw new IllegalArgumentException("Default configuration cannot have a handler set.");
        }
        if (defaultConfiguration.getQueue() != null) {
            throw new IllegalArgumentException("Default configuration cannot have a queue set.");
        }
        Objects.requireNonNull(taskProcessorDefs);
        if (taskProcessorDefs.isEmpty()) {
            throw new IllegalArgumentException("Must supply at least one TaskProcessor definition");
        }
        taskProcessorDefs.stream()
                .map(TaskProcessorConfig::definition)
                .forEach(TaskProcessorUtils::checkTaskProcessorDefinition);

        for (final TaskProcessorConfig config : taskProcessorDefs) {
            final BlockingQueue<Object> queue =
                    config.customQueue() == null ? new LinkedBlockingQueue<>() : config.customQueue();
            if (parts.containsKey(config.definition())) {
                throw new IllegalArgumentException(String.format(
                        "Duplicate TaskProcessor definition: %s",
                        config.definition().getName()));
            }
            parts.put(
                    config.definition(),
                    new ProcessorParts(
                            config.definition(), config, queue, QueueSubmitter.create(config.definition(), queue)));
        }
    }

    /**
     * Add an implementation for a task processor declared in the constructor.
     *
     * @param implementation
     * 		the implementation to add
     * @param <T>
     * 		the type of the implementation
     */
    public <T extends TaskProcessor> void addImplementation(@NonNull final T implementation) {
        throwIfStarted();
        Objects.requireNonNull(implementation);
        parts.values().stream()
                .filter(p -> p.getDefinition().isAssignableFrom(implementation.getClass()))
                .reduce((a, b) -> {
                    throw new IllegalArgumentException(String.format(
                            "Implementing class %s implements multiple TaskProcessor definitions: %s and %s",
                            implementation.getClass().getName(),
                            a.getDefinition().getName(),
                            b.getDefinition().getName()));
                })
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Implementing class %s does not implement any TaskProcessor definitions",
                        implementation.getClass().getName())))
                .setImplementation(implementation);
    }

    /**
     * Same as {@link #addImplementation(TaskProcessor)} but has a second parameter to explicitly specify the type. This
     * is useful for passing in lambdas because the type cannot be inferred.
     */
    public <T extends TaskProcessor> void addImplementation(
            @NonNull final T component, @NonNull final Class<T> componentClass) {
        throwIfStarted();
        Objects.requireNonNull(component);
        Objects.requireNonNull(componentClass);
        parts.get(componentClass).setImplementation(component);
    }

    /**
     * Returns the submitter for the given task processor class. The submitter is used to submit tasks to the task
     * processor to handle asynchronously.
     *
     * @param processorClass
     * 		the class of the task processor to get the submitter for
     * @return the submitter for the given task processor class
     */
    @SuppressWarnings("unchecked")
    public <T extends TaskProcessor> @NonNull T getSubmitter(@NonNull final Class<T> processorClass) {
        Objects.requireNonNull(processorClass);
        return (T) parts.get(processorClass).getSubmitter();
    }

    /**
     * Start the framework. This will start all the task processors in their own threads.
     */
    @SuppressWarnings("unchecked")
    public void start() {
        throwIfStarted();
        parts.values().stream().filter(p -> p.getImplementation() == null).forEach(p -> {
            throw new IllegalStateException(String.format(
                    "TaskProcessor %s has no implementation set",
                    p.getDefinition().getName()));
        });

        for (final ProcessorParts processorParts : parts.values()) {
            final Map<Class<?>, InterruptableConsumer<?>> processingMethods =
                    processorParts.getImplementation().getProcessingMethods();
            final InterruptableConsumer<?> handler;
            if (processingMethods.size() == 1) {
                handler = processingMethods.values().iterator().next();
            } else {
                handler = new MultiHandler(processingMethods)::handle;
            }

            final QueueThread<Object> queueThread = defaultConfiguration
                    .copy()
                    .setThreadName(processorParts.getConfig().name())
                    .setQueue(processorParts.getQueue())
                    .setHandler((InterruptableConsumer<Object>) handler)
                    .build(true);
            processorParts.setQueueThread(queueThread);
        }
        started = true;
    }

    /**
     * Get the queue thread for the given task processor class.
     *
     * @param processorClass
     * 		the class of the task processor to get the queue thread for
     * @return the queue thread for the given task processor class
     */
    public @NonNull QueueThread<?> getQueueThread(@NonNull final Class<? extends TaskProcessor> processorClass) {
        throwIfNotStarted();
        return parts.get(processorClass).getQueueThread();
    }

    /**
     * Stop the framework. This will stop all the task processors.
     */
    public void stop() {
        throwIfNotStarted();
        parts.values().forEach(p -> p.getQueueThread().stop());
    }

    private void throwIfStarted() {
        if (started) {
            throw new IllegalStateException("Cannot perform this operation after starting");
        }
    }

    private void throwIfNotStarted() {
        if (!started) {
            throw new IllegalStateException("Cannot perform this operation before starting");
        }
    }
}
