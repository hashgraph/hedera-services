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

package com.swirlds.common.wiring.model.internal;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.List;

/**
 * A vertex in a wiring model.
 */
public interface ModelVertex extends Comparable<ModelVertex> {

    /**
     * Get the name of the vertex.
     *
     * @return the name
     */
    @NonNull
    String getName();

    /**
     * Get the type of task scheduler that corresponds to this vertex, or null if this vertex does not correspond to a
     * task scheduler.
     *
     * @return the type of task scheduler that corresponds to this vertex, or null if this vertex does not correspond to
     * a task scheduler
     */
    @NonNull
    TaskSchedulerType getType();

    /**
     * Get the meta-type of this vertex. Used to generate the wiring diagram, ignored by other use cases.
     *
     * @return the meta-type of this vertex
     */
    @NonNull
    ModelVertexMetaType getMetaType();

    /**
     * Get whether the insertion of this vertex may block until capacity is available.
     *
     * @return true if the insertion of this vertex may block until capacity is available
     */
    boolean isInsertionIsBlocking();

    /**
     * Add an outgoing edge to this vertex.
     *
     * @param edge the edge to connect to
     */
    void connectToEdge(@NonNull final ModelEdge edge); // TODO redundant?

    /**
     * Get the outgoing edges of this vertex.
     *
     * @return the outgoing edges of this vertex
     */
    @NonNull
    List<ModelEdge> getOutgoingEdges();

    /**
     * Add an input that has been substituted during diagram generation.
     *
     * @param input the input that has been substituted
     */
    void addSubstitutedInput(@NonNull final String input);

    /**
     * Render this vertex in mermaid format. Used when generating a wiring diagram.
     *
     * @param sb the string builder to render to
     */
    void render(@NonNull final StringBuilder sb);

    /**
     * Sorts by name.
     */
    default int compareTo(@NonNull final ModelVertex that) {
        return this.getName().compareTo(that.getName());
    }
}
