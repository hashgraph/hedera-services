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

package com.swirlds.common.wiring.model;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.diagram.ModelEdgeSubstitution;
import com.swirlds.common.wiring.model.diagram.ModelGroup;
import com.swirlds.common.wiring.model.diagram.ModelManualLink;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerMetricsBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.List;

/**
 * A wiring model is a collection of task schedulers and the wires connecting them. It can be used to analyze the wiring
 * of a system and to generate diagrams.
 */
public interface WiringModel extends Startable, Stoppable {

    /**
     * Get a new wiring model builder.
     *
     * @param platformContext the platform context
     * @return a new wiring model builder
     */
    static WiringModelBuilder builder(@NonNull final PlatformContext platformContext) {
        return new WiringModelBuilder(platformContext);
    }

    /**
     * Get a new task scheduler builder.
     *
     * @param name the name of the task scheduler. Used for metrics and debugging. Must be unique. Must only contain
     *             alphanumeric characters and underscores.
     * @param <O>  the data type of the scheduler's primary output wire
     * @return a new task scheduler builder
     */
    @NonNull
    <O> TaskSchedulerBuilder<O> schedulerBuilder(@NonNull final String name);

    /**
     * Get a new task scheduler metrics builder. Can be passed to
     * {@link TaskSchedulerBuilder#withMetricsBuilder(TaskSchedulerMetricsBuilder)} to add metrics to the task
     * scheduler.
     *
     * @return a new task scheduler metrics builder
     */
    @NonNull
    TaskSchedulerMetricsBuilder metricsBuilder();

    /**
     * Check to see if there is cyclic backpressure in the wiring model. Cyclical back pressure can lead to deadlocks,
     * and so it should be avoided at all costs.
     *
     * <p>
     * If this method finds cyclical backpressure, it will log a message that will fail standard platform tests.
     *
     * @return true if there is cyclical backpressure, false otherwise
     */
    boolean checkForCyclicalBackpressure();

    /**
     * Task schedulers using the {@link TaskSchedulerType#DIRECT} strategy have very strict rules about how data can be
     * added to input wires. This method checks to see if these rules are being followed.
     *
     * <p>
     * If this method finds illegal direct scheduler usage, it will log a message that will fail standard platform
     * tests.
     *
     * @return true if there is illegal direct scheduler usage, false otherwise
     */
    boolean checkForIllegalDirectSchedulerUsage();

    /**
     * Check to see if there are any input wires that are unbound.
     *
     * <p>
     * If this method detects unbound input wires in the model, it will log a message that will fail standard platform
     * tests.
     *
     * @return true if there are unbound input wires, false otherwise
     */
    boolean checkForUnboundInputWires();

    /**
     * Generate a mermaid style wiring diagram.
     *
     * @param groups        optional groupings of vertices
     * @param substitutions edges to substitute
     * @param manualLinks   manual links to add to the diagram
     * @return a mermaid style wiring diagram
     */
    @NonNull
    String generateWiringDiagram(
            @NonNull final List<ModelGroup> groups,
            @NonNull final List<ModelEdgeSubstitution> substitutions,
            @NonNull final List<ModelManualLink> manualLinks);

    /**
     * Start everything in the model that needs to be started. Performs static analysis of the wiring topology and
     * writes errors to the logs if problems are detected.
     */
    @Override
    void start();

    /**
     * Stops everything in the model that needs to be stopped.
     */
    @Override
    void stop();

    /**
     * Check if the system is stressed. A system is considered to be stressed if any of the monitored schedulers are
     * stressed.
     *
     * @return true if the system is stressed
     */
    boolean isStressed();

    /**
     * Get the duration that the system has been stressed. Returns null if the system is not stressed.
     *
     * @return the duration that the system has been stressed, or null if the system is not stressed
     */
    @Nullable
    Duration stressedDuration();
}
