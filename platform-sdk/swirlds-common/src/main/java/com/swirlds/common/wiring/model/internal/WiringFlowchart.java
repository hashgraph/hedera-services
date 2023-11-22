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

import com.swirlds.common.wiring.model.ModelGroup;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A utility for drawing mermaid style flowcharts of wiring models.
 */
public final class WiringFlowchart {

    private WiringFlowchart() {}

    private static final String INDENTATION = "    ";
    private static final String SCHEDULER_COLOR = "362";
    private static final String DIRECT_SCHEDULER_COLOR = "666";
    private static final String GROUP_COLOR = "555";

    /**
     * Draw an edge.
     *
     * @param sb                 a string builder where the mermaid file is being assembled
     * @param edge               the edge to draw
     * @param collapsedVertexMap a map from vertices that are in collapsed groups to the group name that they should be
     *                           replaced with
     */
    private static void drawEdge(
            @NonNull final StringBuilder sb,
            @NonNull final ModelEdge edge,
            @NonNull final Map<ModelVertex, String> collapsedVertexMap) {

        final String source;
        if (collapsedVertexMap.containsKey(edge.source())) {
            source = collapsedVertexMap.get(edge.source());
        } else {
            source = edge.source().getName();
        }

        final String destination;
        if (collapsedVertexMap.containsKey(edge.destination())) {
            destination = collapsedVertexMap.get(edge.destination());

        } else {
            destination = edge.destination().getName();
        }

        if (source.equals(destination)) {
            // Don't draw arrows from a component back to itself.
            return;
        }

        sb.append(INDENTATION).append(source);

        if (edge.insertionIsBlocking()) {
            if (edge.label().isEmpty()) {
                sb.append(" --> ");
            } else {
                sb.append(" -- \"").append(edge.label()).append("\" --> ");
            }
        } else {
            if (edge.label().isEmpty()) {
                sb.append(" -.-> ");
            } else {
                sb.append(" -. \"").append(edge.label()).append("\" .-> ");
            }
        }
        sb.append(destination).append("\n");
    }

    /**
     * Modify the shape of the vertex on the graph (e.g. should this vertex be drawn with a box, a circle, etc.).
     *
     * @param sb     a string builder where the mermaid file is being assembled
     * @param vertex the vertex to modify
     */
    private static void modifyVertexShape(@NonNull final StringBuilder sb, @NonNull final ModelVertex vertex) {
        if (vertex.getType() == TaskSchedulerType.CONCURRENT) {
            sb.append("[[").append(vertex.getName()).append("]]");
        } else if (vertex.getType() == TaskSchedulerType.DIRECT) {
            sb.append("((").append(vertex.getName()).append("))");
        } else if (vertex.getType() == TaskSchedulerType.DIRECT_STATELESS) {
            sb.append("(((").append(vertex.getName()).append(")))");
        }
    }

    /**
     * Based on the type of vertex, determine the appropriate color.
     *
     * @param vertex the vertex to get the color for
     * @return the color
     */
    private static String getVertexColor(@NonNull final ModelVertex vertex) {
        final TaskSchedulerType type = vertex.getType();

        return switch (type) {
            case SEQUENTIAL:
            case SEQUENTIAL_THREAD:
            case CONCURRENT:
                yield SCHEDULER_COLOR;
            case DIRECT:
            case DIRECT_STATELESS:
                yield DIRECT_SCHEDULER_COLOR;
        };
    }

    /**
     * Draw a vertex.
     *
     * @param sb                 a string builder where the mermaid file is being assembled
     * @param vertex             the vertex to draw
     * @param collapsedVertexMap a map from vertices that are in collapsed groups to the group name that they should be
     *                           replaced with
     * @param indentLevel        the level of indentation
     */
    private static void drawVertex(
            @NonNull final StringBuilder sb,
            @NonNull final ModelVertex vertex,
            @NonNull final Map<ModelVertex, String> collapsedVertexMap,
            final int indentLevel) {

        if (!collapsedVertexMap.containsKey(vertex)) {
            sb.append(INDENTATION.repeat(indentLevel)).append(vertex.getName());
            modifyVertexShape(sb, vertex);
            sb.append("\n");

            sb.append(INDENTATION.repeat(indentLevel))
                    .append("style ")
                    .append(vertex.getName())
                    .append(" fill:#")
                    .append(getVertexColor(vertex))
                    .append(",stroke:#000,stroke-width:2px,color:#fff\n");
        }
    }

    private static void drawGroup(
            @NonNull final StringBuilder sb,
            @NonNull final ModelGroup group,
            @NonNull final Set<ModelVertex> vertices,
            @NonNull final Map<ModelVertex, String> collapsedVertexMap) {

        sb.append(INDENTATION).append("subgraph ").append(group.name()).append("\n");

        final String color;
        if (group.collapse()) {
            color = SCHEDULER_COLOR;
        } else {
            color = GROUP_COLOR;
        }

        sb.append(INDENTATION.repeat(2))
                .append("style ")
                .append(group.name())
                .append(" fill:#")
                .append(color)
                .append(",stroke:#000,stroke-width:2px,color:#fff\n");

        vertices.stream().sorted().forEachOrdered(vertex -> drawVertex(sb, vertex, collapsedVertexMap, 2));
        sb.append(INDENTATION).append("end\n");
    }

    /**
     * Get the actual list of vertices for each group (as opposed to just the names of the vertices in the groups).
     *
     * @return the map from group name to the vertices in that group
     */
    @NonNull
    private static Map<String, Set<ModelVertex>> buildGroupMap(
            @NonNull final Map<String, ModelVertex> vertices, @NonNull final Set<ModelGroup> groups) {

        final Map<String, Set<ModelVertex>> groupMap = new HashMap<>();

        for (final ModelGroup group : groups) {
            groupMap.put(group.name(), new HashSet<>());
            for (final String vertexName : group.elements()) {
                groupMap.get(group.name()).add(vertices.get(vertexName));
            }
        }

        return groupMap;
    }

    /**
     * Get the list of vertices that are not in any group.
     *
     * @param vertices a map from vertex names to vertices
     * @param groupMap a map of group names to the vertices in those groups
     * @return the list of vertices that are not in any group
     */
    private static List<ModelVertex> getUngroupedVertices(
            @NonNull final Map<String, ModelVertex> vertices,
            @NonNull Map<String /* the name of the group */, Set<ModelVertex>> groupMap) {

        final Set<ModelVertex> uniqueVertices = new HashSet<>(vertices.values());

        for (final Set<ModelVertex> group : groupMap.values()) {
            for (final ModelVertex vertex : group) {
                final boolean removed = uniqueVertices.remove(vertex);
                if (!removed) {
                    throw new IllegalStateException("Vertex " + vertex.getName() + " is in multiple groups.");
                }
            }
        }

        return new ArrayList<>(uniqueVertices);
    }

    /**
     * For all vertices that are in collapsed groups, we want to draw edges to the collapsed group instead of to the
     * individual vertices in the group. This method builds a map from the collapsed vertices to the group name that
     * they should be replaced with.
     *
     * @param groups   the groups
     * @param vertices a map from vertex names to vertices
     * @return a map from collapsed vertices to the group name that they should be replaced with
     */
    @NonNull
    private static Map<ModelVertex, String> getCollapsedVertexMap(
            @NonNull final Set<ModelGroup> groups, @NonNull final Map<String, ModelVertex> vertices) {

        final HashMap<ModelVertex, String> collapsedVertexMap = new HashMap<>();

        for (final ModelGroup group : groups) {
            if (!group.collapse()) {
                continue;
            }

            for (final String vertexName : group.elements()) {
                collapsedVertexMap.put(vertices.get(vertexName), group.name());
            }
        }

        return collapsedVertexMap;
    }

    /**
     * Generate a mermaid flowchart of the wiring model.
     *
     * @param vertices the vertices in the wiring model
     * @param edges    the edges in the wiring model
     * @param groups   the groups in the wiring model
     * @return a mermaid flowchart of the wiring model, in string form
     */
    @NonNull
    public static String generateWiringDiagram(
            @NonNull final Map<String, ModelVertex> vertices,
            @NonNull final Set<ModelEdge> edges,
            @NonNull final Set<ModelGroup> groups) {

        final StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        final Map<String, Set<ModelVertex>> groupMap = buildGroupMap(vertices, groups);
        final List<ModelVertex> ungroupedVertices = getUngroupedVertices(vertices, groupMap);
        final Map<ModelVertex, String> collapsedVertexMap = getCollapsedVertexMap(groups, vertices);

        groups.stream()
                .sorted()
                .forEachOrdered(group -> drawGroup(sb, group, groupMap.get(group.name()), collapsedVertexMap));
        ungroupedVertices.stream().sorted().forEachOrdered(vertex -> drawVertex(sb, vertex, collapsedVertexMap, 1));
        edges.stream().sorted().forEachOrdered(edge -> drawEdge(sb, edge, collapsedVertexMap));

        return sb.toString();
    }
}
