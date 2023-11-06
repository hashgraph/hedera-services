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

package com.swirlds.common.wiring;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.StandardWiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/**
 * A wiring model is a collection of task schedulers and the wires connecting them. It can be used to analyze the wiring
 * of a system and to generate diagrams.
 */
public abstract class WiringModel {

    private final PlatformContext platformContext;
    private final Time time;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    protected WiringModel(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        this.platformContext = Objects.requireNonNull(platformContext);
        this.time = Objects.requireNonNull(time);
    }

    /**
     * Build a new wiring model instance.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     * @return a new wiring model instance
     */
    @NonNull
    public static WiringModel create(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        return new StandardWiringModel(platformContext, time);
    }

    /**
     * Get a new task scheduler builder.
     *
     * @param name the name of the task scheduler. Used for metrics and debugging. Must be unique. Must only contain
     *             alphanumeric characters and underscores.
     * @return a new wire builder
     */
    @NonNull
    public final <O> TaskSchedulerBuilder<O> schedulerBuilder(@NonNull final String name) {
        return new TaskSchedulerBuilder<>(this, name);
    }

    /**
     * Get a new task scheduler metrics builder. Can be passed to
     * {@link TaskSchedulerBuilder#withMetricsBuilder(TaskSchedulerMetricsBuilder)} to add metrics to the task
     * scheduler.
     *
     * @return a new task scheduler metrics builder
     */
    @NonNull
    public final TaskSchedulerMetricsBuilder metricsBuilder() {
        return new TaskSchedulerMetricsBuilder(platformContext.getMetrics(), time);
    }

    /**
     * Check to see if there is cyclic backpressure in the wiring model. Cyclical back pressure can lead to deadlocks,
     * and so it should be avoided at all costs.
     *
     * <p>
     * If this method finds cyclical backpressure, it will log a message that will fail standard platform tests.
     *
     * @return true if there is cyclical backpressure, false otherwise
     */
    public abstract boolean checkForCyclicalBackpressure();

    /**
     * Generate a mermaid style wiring diagram.
     *
     * @param groups optional groupings of vertices
     * @return a mermaid style wiring diagram
     */
    @NonNull
    public abstract String generateWiringDiagram(@NonNull final Set<ModelGroup> groups);

    /**
     * Reserved for internal framework use. Do not call this method directly.
     * <p>
     * Register a vertex in the wiring model. These are either task schedulers or wire transformers. Vertices
     * always have a single Java object output type, although there may be many consumers of that output. Vertices may
     * have many input types.
     *
     * @param vertexName          the name of the vertex
     * @param insertionIsBlocking if true then insertion may block until capacity is available
     */
    public abstract void registerVertex(@NonNull String vertexName, final boolean insertionIsBlocking);

    /**
     * Reserved for internal framework use. Do not call this method directly.
     * <p>
     * Register an edge between two vertices.
     *
     * @param originVertex      the origin vertex
     * @param destinationVertex the destination vertex
     * @param label             the label of the edge
     * @param injection         true if this edge is an injection edge, false otherwise
     */
    public abstract void registerEdge(
            @NonNull String originVertex,
            @NonNull String destinationVertex,
            @NonNull String label,
            final boolean injection);
}
