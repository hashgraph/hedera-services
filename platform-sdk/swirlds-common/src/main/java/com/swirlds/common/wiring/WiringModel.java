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

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.builders.TaskSchedulerMetricsBuilder;
import com.swirlds.common.wiring.builders.TaskSchedulerType;
import com.swirlds.common.wiring.model.CycleFinder;
import com.swirlds.common.wiring.model.DirectSchedulerChecks;
import com.swirlds.common.wiring.model.ModelEdge;
import com.swirlds.common.wiring.model.ModelVertex;
import com.swirlds.common.wiring.model.WiringFlowchart;
import com.swirlds.common.wiring.schedulers.HeartbeatScheduler;
import com.swirlds.common.wiring.schedulers.SequentialThreadTaskScheduler;
import com.swirlds.common.wiring.utility.ModelGroup;
import com.swirlds.common.wiring.wires.SolderType;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A wiring model is a collection of task schedulers and the wires connecting them. It can be used to analyze the wiring
 * of a system and to generate diagrams.
 */
public class WiringModel implements Startable, Stoppable {

    private final PlatformContext platformContext;
    private final Time time;

    /**
     * A map of vertex names to vertices.
     */
    private final Map<String, ModelVertex> vertices = new HashMap<>();

    /**
     * A set of all edges in the model.
     */
    private final Set<ModelEdge> edges = new HashSet<>();

    /**
     * Schedules heartbeats. Not created unless needed.
     */
    private HeartbeatScheduler heartbeatScheduler = null;

    /**
     * Thread schedulers need to have their threads started/stopped.
     */
    private final List<SequentialThreadTaskScheduler<?>> threadSchedulers = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    private WiringModel(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
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
        return new WiringModel(platformContext, time);
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
     * Build a wire that produces an instant (reflecting current time) at the specified rate. Note that the exact rate
     * of heartbeats may vary. This is a best effort algorithm, and actual rates may vary depending on a variety of
     * factors.
     *
     * @param period the period of the heartbeat. For example, setting a period of 100ms will cause the heartbeat to be
     *               sent at 10 hertz. Note that time is measured at millisecond precision, and so periods less than 1ms
     *               are not supported.
     * @return the output wire
     * @throws IllegalStateException if the heartbeat has already started
     */
    @NonNull
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        return getHeartbeatScheduler().buildHeartbeatWire(period);
    }

    /**
     * Build a wire that produces an instant (reflecting current time) at the specified rate. Note that the exact rate
     * of heartbeats may vary. This is a best effort algorithm, and actual rates may vary depending on a variety of
     * factors.
     *
     * @param frequency the frequency of the heartbeat in hertz. Note that time is measured at millisecond precision,
     *                  and so frequencies greater than 1000hz are not supported.
     * @return the output wire
     */
    public OutputWire<Instant> buildHeartbeatWire(final double frequency) {
        return getHeartbeatScheduler().buildHeartbeatWire(frequency);
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
    public boolean checkForCyclicalBackpressure() {
        return CycleFinder.checkForCyclicalBackPressure(vertices.values());
    }

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
    public boolean checkForIllegalDirectSchedulerUsage() {
        return DirectSchedulerChecks.checkForIllegalDirectSchedulerUse(vertices.values());
    }

    /**
     * Generate a mermaid style wiring diagram.
     *
     * @param groups optional groupings of vertices
     * @return a mermaid style wiring diagram
     */
    @NonNull
    public String generateWiringDiagram(@NonNull final Set<ModelGroup> groups) {
        return WiringFlowchart.generateWiringDiagram(vertices, edges, groups);
    }

    /**
     * Reserved for framework use. Do not call this method directly.
     * <p>
     * Register a task scheduler with the wiring model.
     * </p>
     *
     * @param scheduler the task scheduler to register
     */
    public void registerScheduler(@NonNull final TaskScheduler<?> scheduler) {
        registerVertex(scheduler.getName(), scheduler.getType(), scheduler.isInsertionBlocking());
        if (scheduler.getType() == TaskSchedulerType.SEQUENTIAL_THREAD) {
            threadSchedulers.add((SequentialThreadTaskScheduler<?>) scheduler);
        }
    }

    /**
     * Reserved for internal framework use. Do not call this method directly.
     * <p>
     * Register a vertex in the wiring model. These are either task schedulers or wire transformers.
     *
     * @param vertexName          the name of the vertex
     * @param type                the type of task scheduler that corresponds to this vertex.
     * @param insertionIsBlocking if true then insertion may block until capacity is available
     */
    public void registerVertex(
            @NonNull final String vertexName,
            @NonNull final TaskSchedulerType type,
            final boolean insertionIsBlocking) {
        Objects.requireNonNull(vertexName);
        Objects.requireNonNull(type);
        final boolean unique = vertices.put(vertexName, new ModelVertex(vertexName, type, insertionIsBlocking)) == null;
        if (!unique) {
            throw new IllegalArgumentException("Duplicate vertex name: " + vertexName);
        }
    }

    /**
     * Reserved for internal framework use. Do not call this method directly.
     * <p>
     * Register an edge between two vertices.
     *
     * @param originVertex      the origin vertex
     * @param destinationVertex the destination vertex
     * @param label             the label of the edge
     * @param solderType        the type of solder connection
     */
    public void registerEdge(
            @NonNull final String originVertex,
            @NonNull final String destinationVertex,
            @NonNull final String label,
            @NonNull final SolderType solderType) {

        final boolean blockingEdge = solderType == SolderType.PUT;

        final ModelVertex origin = getVertex(originVertex);
        final ModelVertex destination = getVertex(destinationVertex);
        final boolean blocking = blockingEdge && destination.isInsertionIsBlocking();

        final ModelEdge edge = new ModelEdge(origin, destination, label, blocking);
        origin.connectToEdge(edge);

        final boolean unique = edges.add(edge);
        if (!unique) {
            throw new IllegalArgumentException(
                    "Duplicate edge: " + originVertex + " -> " + destinationVertex + ", label = " + label);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.start();
        }

        for (final SequentialThreadTaskScheduler<?> threadScheduler : threadSchedulers) {
            threadScheduler.start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.stop();
        }

        for (final SequentialThreadTaskScheduler<?> threadScheduler : threadSchedulers) {
            threadScheduler.stop();
        }
    }

    /**
     * Get the heartbeat scheduler, creating it if necessary.
     *
     * @return the heartbeat scheduler
     */
    @NonNull
    private HeartbeatScheduler getHeartbeatScheduler() {
        if (heartbeatScheduler == null) {
            heartbeatScheduler = new HeartbeatScheduler(this, time, "heartbeat");
        }
        return heartbeatScheduler;
    }

    /**
     * Find an existing vertex
     *
     * @param vertexName the name of the vertex
     * @return the vertex
     */
    @NonNull
    private ModelVertex getVertex(@NonNull final String vertexName) {
        final ModelVertex vertex = vertices.get(vertexName);
        if (vertex != null) {
            return vertex;
        }

        // Create an ad hoc vertex.
        final ModelVertex adHocVertex = new ModelVertex(vertexName, TaskSchedulerType.DIRECT, true);

        vertices.put(vertexName, adHocVertex);
        return adHocVertex;
    }
}
