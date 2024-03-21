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

package com.swirlds.common.wiring.model.internal;

import static com.swirlds.common.wiring.model.internal.ModelVertexMetaType.SCHEDULER;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT_THREADSAFE;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.SEQUENTIAL_THREAD;

import com.swirlds.base.time.Time;
import com.swirlds.common.wiring.model.ModelEdgeSubstitution;
import com.swirlds.common.wiring.model.ModelGroup;
import com.swirlds.common.wiring.model.ModelManualLink;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerMetricsBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.schedulers.internal.HeartbeatScheduler;
import com.swirlds.common.wiring.schedulers.internal.SequentialThreadTaskScheduler;
import com.swirlds.common.wiring.wires.SolderType;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * A wiring model is a collection of task schedulers and the wires connecting them. It can be used to analyze the wiring
 * of a system and to generate diagrams.
 */
public class StandardWiringModel implements WiringModel {

    private final Metrics metrics;
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
     * Input wires that have been created.
     */
    private final Set<InputWireDescriptor> inputWires = new HashSet<>();

    /**
     * Input wires that have been bound to a handler.
     */
    private final Set<InputWireDescriptor> boundInputWires = new HashSet<>();

    /**
     * Input wires with at least one thing soldered to them.
     */
    private final Set<InputWireDescriptor> solderedInputWires = new HashSet<>();

    /**
     * The default fork join pool, schedulers not explicitly assigned a pool will use this one.
     */
    private final ForkJoinPool defaultPool;

    /**
     * True if start() has been called.
     */
    private boolean started = false;

    /**
     * Constructor.
     *
     * @param metrics     provides metrics
     * @param time        provides wall clock time
     * @param defaultPool the default fork join pool, schedulers not explicitly assigned a pool will use this one
     */
    public StandardWiringModel(
            @NonNull final Metrics metrics, @NonNull final Time time, @NonNull final ForkJoinPool defaultPool) {

        this.metrics = Objects.requireNonNull(metrics);
        this.time = Objects.requireNonNull(time);
        this.defaultPool = Objects.requireNonNull(defaultPool);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public final <O> TaskSchedulerBuilder<O> schedulerBuilder(@NonNull final String name) {
        throwIfStarted();
        return new TaskSchedulerBuilder<>(this, name, defaultPool);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public final TaskSchedulerMetricsBuilder metricsBuilder() {
        throwIfStarted();
        return new TaskSchedulerMetricsBuilder(metrics, time);
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
     * @throws IllegalStateException if start() has already been called
     */
    @NonNull
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        throwIfStarted();
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
        throwIfStarted();
        return getHeartbeatScheduler().buildHeartbeatWire(frequency);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkForCyclicalBackpressure() {
        return CycleFinder.checkForCyclicalBackPressure(vertices.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkForIllegalDirectSchedulerUsage() {
        return DirectSchedulerChecks.checkForIllegalDirectSchedulerUse(vertices.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkForUnboundInputWires() {
        return InputWireChecks.checkForUnboundInputWires(inputWires, boundInputWires);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateWiringDiagram(
            @NonNull final List<ModelGroup> groups,
            @NonNull final List<ModelEdgeSubstitution> substitutions,
            @NonNull final List<ModelManualLink> manualLinks,
            final boolean moreMystery) {
        addVertexForUnsolderedInputWires(moreMystery);
        final WiringFlowchart flowchart = new WiringFlowchart(vertices, substitutions, groups, manualLinks);
        return flowchart.render();
    }

    /**
     * Add a special vertex for all unsoldered input wires.
     */
    private void addVertexForUnsolderedInputWires(final boolean moreMystery) {
        final Set<InputWireDescriptor> unsolderedInputWires = new HashSet<>(inputWires);
        unsolderedInputWires.removeAll(solderedInputWires);

        if (unsolderedInputWires.isEmpty()) {
            return;
        }

        final ModelVertex unsolderedDataSource =
                new StandardVertex("Mystery Input", DIRECT_THREADSAFE, SCHEDULER, null, true);
        vertices.put(unsolderedDataSource.getName(), unsolderedDataSource);

        for (final InputWireDescriptor unsolderedInputWire : unsolderedInputWires) {
            final ModelVertex destination = getVertex(unsolderedInputWire.taskSchedulerName());

            final String edgeDescription = moreMystery ? "mystery data" : unsolderedInputWire.name();
            final ModelEdge edge = new ModelEdge(unsolderedDataSource, destination, edgeDescription, true, true);
            unsolderedDataSource.getOutgoingEdges().add(edge);
        }
    }

    /**
     * Register a task scheduler with the wiring model.
     *
     * @param scheduler the task scheduler to register
     * @param hyperlink the hyperlink to the documentation for this vertex, or null if there is no documentation
     */
    public void registerScheduler(@NonNull final TaskScheduler<?> scheduler, @Nullable final String hyperlink) {
        throwIfStarted();
        registerVertex(scheduler.getName(), scheduler.getType(), hyperlink, scheduler.isInsertionBlocking());
        if (scheduler.getType() == SEQUENTIAL_THREAD) {
            threadSchedulers.add((SequentialThreadTaskScheduler<?>) scheduler);
        }
    }

    /**
     * Register a vertex in the wiring model. These are either task schedulers or wire transformers.
     *
     * @param vertexName          the name of the vertex
     * @param type                the type of task scheduler that corresponds to this vertex.
     * @param hyperlink           the hyperlink to the documentation for this vertex, or null if there is no
     *                            documentation
     * @param insertionIsBlocking if true then insertion may block until capacity is available
     */
    public void registerVertex(
            @NonNull final String vertexName,
            @NonNull final TaskSchedulerType type,
            @Nullable final String hyperlink,
            final boolean insertionIsBlocking) {
        throwIfStarted();
        Objects.requireNonNull(vertexName);
        Objects.requireNonNull(type);
        final boolean unique = vertices.put(
                        vertexName, new StandardVertex(vertexName, type, SCHEDULER, hyperlink, insertionIsBlocking))
                == null;
        if (!unique) {
            throw new IllegalArgumentException("Duplicate vertex name: " + vertexName);
        }
    }

    /**
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
        throwIfStarted();

        final boolean blockingEdge = solderType == SolderType.PUT;

        final ModelVertex origin = getVertex(originVertex);
        final ModelVertex destination = getVertex(destinationVertex);
        final boolean blocking = blockingEdge && destination.isInsertionIsBlocking();

        final ModelEdge edge = new ModelEdge(origin, destination, label, blocking, false);
        origin.getOutgoingEdges().add(edge);

        final boolean unique = edges.add(edge);
        if (!unique) {
            throw new IllegalArgumentException(
                    "Duplicate edge: " + originVertex + " -> " + destinationVertex + ", label = " + label);
        }

        solderedInputWires.add(new InputWireDescriptor(destinationVertex, label));
    }

    /**
     * Register an input wire with the wiring model. For every input wire registered via this method, the model expects
     * to see exactly one registration via {@link #registerInputWireBinding(String, String)}.
     *
     * @param taskSchedulerName the name of the task scheduler that the input wire is associated with
     * @param inputWireName     the name of the input wire
     */
    public void registerInputWireCreation(
            @NonNull final String taskSchedulerName, @NonNull final String inputWireName) {
        throwIfStarted();

        final boolean unique = inputWires.add(new InputWireDescriptor(taskSchedulerName, inputWireName));
        if (!unique) {
            throw new IllegalStateException(
                    "Duplicate input wire " + inputWireName + " for scheduler " + taskSchedulerName);
        }
    }

    /**
     * Register an input wire binding with the wiring model. For every input wire registered via
     * {@link #registerInputWireCreation(String, String)}, the model expects to see exactly one registration via this
     * method.
     *
     * @param taskSchedulerName the name of the task scheduler that the input wire is associated with
     * @param inputWireName     the name of the input wire
     */
    public void registerInputWireBinding(@NonNull final String taskSchedulerName, @NonNull final String inputWireName) {
        throwIfStarted();

        final InputWireDescriptor descriptor = new InputWireDescriptor(taskSchedulerName, inputWireName);

        final boolean registered = inputWires.contains(descriptor);
        if (!registered) {
            throw new IllegalStateException(
                    "Input wire " + inputWireName + " for scheduler " + taskSchedulerName + " was not registered");
        }

        final boolean unique = boundInputWires.add(descriptor);
        if (!unique) {
            throw new IllegalStateException("Input wire " + inputWireName + " for scheduler " + taskSchedulerName
                    + " should not be bound more than once");
        }
    }

    /**
     * Throw an exception if start() has already been called.
     */
    private void throwIfStarted() {
        if (started) {
            throw new IllegalStateException("start() has already been called, operation not permitted.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfStarted();
        started = true;

        // We don't have to do anything with the output of these sanity checks.
        // The methods below will log errors if they find problems.
        checkForCyclicalBackpressure();
        checkForIllegalDirectSchedulerUsage();
        checkForUnboundInputWires();

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
        if (!started) {
            throw new IllegalStateException("start() has not been called, operation not permitted.");
        }
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
        final StandardVertex adHocVertex = new StandardVertex(vertexName, DIRECT, SCHEDULER, null, true);

        vertices.put(vertexName, adHocVertex);
        return adHocVertex;
    }
}
