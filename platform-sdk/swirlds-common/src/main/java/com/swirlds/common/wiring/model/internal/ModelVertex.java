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

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

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
     * Get the hyperlink to the documentation for this vertex, or null if there is no documentation.
     *
     * @return the hyperlink to the documentation for this vertex, or null if there is no documentation
     */
    @Nullable
    String getHyperlink();

    /**
     * Get whether the insertion of this vertex may block until capacity is available.
     *
     * @return true if the insertion of this vertex may block until capacity is available
     */
    boolean isInsertionIsBlocking();

    /**
     * Get the outgoing edges of this vertex.
     *
     * @return the outgoing edges of this vertex
     */
    @NonNull
    Set<ModelEdge> getOutgoingEdges();

    /**
     * Get substituted inputs for this vertex.
     */
    @NonNull
    Set<String> getSubstitutedInputs();

    /**
     * Render this vertex in mermaid format. Used when generating a wiring diagram.
     *
     * @param sb           the string builder to render to
     * @param nameProvider provides short names for vertices
     * @param styleManager manages the styles of vertices
     */
    void render(
            @NonNull final StringBuilder sb,
            @NonNull final MermaidNameShortener nameProvider,
            @NonNull final MermaidStyleManager styleManager);

    /**
     * Sorts by name.
     */
    default int compareTo(@NonNull final ModelVertex that) {
        return this.getName().compareTo(that.getName());
    }

    /**
     * Set the depth of this vertex in the wiring diagram. Depth increases by 1 for every group that this vertex is
     * nested within.
     *
     * @param depth the depth of this vertex in the wiring diagram
     */
    void setDepth(int depth);
}
