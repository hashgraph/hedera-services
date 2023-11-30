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

import static com.swirlds.common.wiring.model.internal.LegacyWiringFlowchart.DIRECT_SCHEDULER_COLOR;
import static com.swirlds.common.wiring.model.internal.LegacyWiringFlowchart.GROUP_COLOR;
import static com.swirlds.common.wiring.model.internal.LegacyWiringFlowchart.INDENTATION;
import static com.swirlds.common.wiring.model.internal.LegacyWiringFlowchart.SCHEDULER_COLOR;
import static com.swirlds.common.wiring.model.internal.LegacyWiringFlowchart.SUBSTITUTION_COLOR;
import static com.swirlds.common.wiring.model.internal.LegacyWiringFlowchart.TEXT_COLOR;

import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A vertex in a wiring model.
 */
public class ModelVertex implements Iterable<ModelEdge>, Comparable<ModelVertex> {

    /**
     * The name of the vertex.
     */
    private final String name;

    /**
     * When tasks are inserted into this vertex, is this component capable of applying back pressure?
     */
    private final boolean insertionIsBlocking;

    /**
     * The task scheduler type that corresponds to this vertex.
     */
    private final TaskSchedulerType type;

    /**
     * The meta-type of this vertex. Used by the wiring diagram, ignored by other use cases.
     */
    private final ModelVertexMetaType metaType;

    /**
     * The outgoing edges of this vertex.
     */
    private final List<ModelEdge> outgoingEdges = new ArrayList<>();

    /**
     * Used to track inputs that have been substituted during diagram generation.
     */
    private final Set<String> substitutedInputs = new HashSet<>();

    /**
     * The groups containing this vertex from highest to lowest level.
     */
    private final List<String> groupHierarchy = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param name                the name of the vertex
     * @param type                the type of task scheduler that corresponds to this vertex
     * @param metaType            the meta-type of this vertex, used to generate a wiring diagram
     * @param insertionIsBlocking true if the insertion of this vertex may block until capacity is available
     */
    public ModelVertex(
            @NonNull final String name,
            @NonNull final TaskSchedulerType type,
            @NonNull final ModelVertexMetaType metaType,
            final boolean insertionIsBlocking) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.metaType = Objects.requireNonNull(metaType);
        this.insertionIsBlocking = insertionIsBlocking;
    }

    /**
     * Get the name of the vertex.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the type of task scheduler that corresponds to this vertex, or null if this vertex does not correspond to a
     * task scheduler.
     *
     * @return the type of task scheduler that corresponds to this vertex, or null if this vertex does not correspond to
     * a task scheduler
     */
    @NonNull
    public TaskSchedulerType getType() {
        return type;
    }

    /**
     * Get the meta-type of this vertex. Used to generate the wiring diagram, ignored by other use cases.
     *
     * @return the meta-type of this vertex
     */
    @NonNull
    public ModelVertexMetaType getMetaType() {
        return metaType;
    }

    /**
     * Get whether the insertion of this vertex may block until capacity is available.
     *
     * @return true if the insertion of this vertex may block until capacity is available
     */
    public boolean isInsertionIsBlocking() {
        return insertionIsBlocking;
    }

    /**
     * Add an outgoing edge to this vertex.
     *
     * @param edge the edge to connect to
     */
    public void connectToEdge(@NonNull final ModelEdge edge) { // TODO is this redundant?
        outgoingEdges.add(Objects.requireNonNull(edge));
    }

    /**
     * Get an iterator that walks over the outgoing edges of this vertex.
     *
     * @return an iterator that walks over the outgoing edges of this vertex
     */
    @Override
    @NonNull
    public Iterator<ModelEdge> iterator() {
        return outgoingEdges.iterator();
    }

    /**
     * Get the outgoing edges of this vertex.
     *
     * @return the outgoing edges of this vertex
     */
    @NonNull
    public List<ModelEdge> getOutgoingEdges() {
        return outgoingEdges;
    }

    /**
     * Add an input that has been substituted during diagram generation.
     *
     * @param input the input that has been substituted
     */
    public void addSubstitutedInput(@NonNull final String input) {
        substitutedInputs.add(Objects.requireNonNull(input));
    }

    /**
     * Get the inputs that have been substituted during diagram generation.
     */
    public void addToGroup(@NonNull final String group) {
        // Groups are defined from lowest to highest level, but we want to render them from highest to lowest level.
        groupHierarchy.add(0, Objects.requireNonNull(group));
    }

    /**
     * Get the group hierarchy of this vertex.
     *
     * @return the group hierarchy of this vertex
     */
    @NonNull
    public List<String> getGroupHierarchy() {
        return groupHierarchy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof final ModelVertex that) {
            return name.equals(that.name);
        }
        return false;
    }

    /**
     * Makes the vertex nicer to look at in a debugger.
     */
    @Override
    public String toString() {
        if (insertionIsBlocking) {
            return "[" + name + "]";
        } else {
            return "(" + name + ")";
        }
    }

    /**
     * Sorts vertices by alphabetical order.
     */
    @Override
    public int compareTo(@NonNull final ModelVertex that) {
        // First sort by group hierarchy, then by name.
        if (!this.groupHierarchy.equals(that.groupHierarchy)) {
            return this.groupHierarchy.toString().compareTo(that.groupHierarchy.toString());
        }

        return name.compareTo(that.name);
    }

    /**
     * Render this vertex in mermaid format. Used when generating a wiring diagram.
     *
     * @param sb the string builder to render to
     */
    public void render(@NonNull final StringBuilder sb) {

        final int indentationLevel = 1 + groupHierarchy.size();

        sb.append(INDENTATION.repeat(indentationLevel)).append(name);

        switch (metaType) {
            case SUBSTITUTION -> sb.append("((");
            case GROUP -> sb.append("[");
            case SCHEDULER -> {
                switch (type) {
                    case CONCURRENT -> sb.append("[[");
                    case DIRECT -> sb.append("[/");
                    case DIRECT_STATELESS -> sb.append("{{");
                    default -> sb.append("[");
                }
            }
        }

        sb.append("\"").append(name);

        if (!substitutedInputs.isEmpty()) {
            sb.append("<br />");
            substitutedInputs.stream().sorted().forEachOrdered(sb::append);
        }

        sb.append("\"");

        switch (metaType) {
            case SUBSTITUTION -> sb.append("))");
            case GROUP -> sb.append("]");
            case SCHEDULER -> {
                switch (type) {
                    case CONCURRENT -> sb.append("]]");
                    case DIRECT -> sb.append("/]");
                    case DIRECT_STATELESS -> sb.append("}}");
                    default -> sb.append("]");
                }
            }
        }

        sb.append("\n");

        // TODO future cody:
        //  - generate the graph
        //  - figure out why things aren't connected the expected way
        final String color = switch (metaType) {
            case SUBSTITUTION -> SUBSTITUTION_COLOR;
            case GROUP -> GROUP_COLOR;
            case SCHEDULER -> switch (type) {
                case DIRECT -> DIRECT_SCHEDULER_COLOR;
                case DIRECT_STATELESS -> DIRECT_SCHEDULER_COLOR;
                default -> SCHEDULER_COLOR;
            };
        };

        sb.append(INDENTATION.repeat(indentationLevel))
                .append("style ")
                .append(name)
                .append(" fill:#")
                .append(color)
                .append(",stroke:#")
                .append(TEXT_COLOR)
                .append(",stroke-width:2px\n");
    }
}
