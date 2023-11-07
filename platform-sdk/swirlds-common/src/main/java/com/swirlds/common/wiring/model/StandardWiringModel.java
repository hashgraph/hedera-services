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

package com.swirlds.common.wiring.model;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.SolderType;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.common.wiring.schedulers.HeartbeatScheduler;
import com.swirlds.common.wiring.utility.ModelGroup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A standard implementation of a wiring model.
 */
public class StandardWiringModel extends WiringModel {

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
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    public StandardWiringModel(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        super(platformContext, time);
        this.time = Objects.requireNonNull(time);
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
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        return getHeartbeatScheduler().buildHeartbeatWire(period);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputWire<Instant> buildHeartbeatWire(final double frequency) {
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
    @NonNull
    @Override
    public String generateWiringDiagram(@NonNull final Set<ModelGroup> groups) {
        return WiringFlowchart.generateWiringDiagram(vertices, edges, groups);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerVertex(@NonNull final String vertexName, final boolean insertionIsBlocking) {
        Objects.requireNonNull(vertexName);
        final boolean unique = vertices.put(vertexName, new ModelVertex(vertexName, insertionIsBlocking)) == null;
        if (!unique) {
            throw new IllegalArgumentException("Duplicate vertex name: " + vertexName);
        }
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

        // Create an ad hoc vertex. This is needed when wires are soldered to lambdas.
        final ModelVertex adHocVertex = new ModelVertex(vertexName, true);
        vertices.put(vertexName, adHocVertex);
        return adHocVertex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        if (heartbeatScheduler != null) {
            heartbeatScheduler.stop();
        }
    }
}
