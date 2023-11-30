package com.swirlds.common.wiring.model.internal;

import static com.swirlds.common.wiring.model.internal.ModelVertexMetaType.SCHEDULER;
import static com.swirlds.common.wiring.model.internal.ModelVertexMetaType.SUBSTITUTION;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.DIRECT;

import com.swirlds.common.wiring.model.ModelEdgeSubstitution;
import com.swirlds.common.wiring.model.ModelGroup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A readable wiring flowchart.
 */
public class WiringFlowchart {

    public static final String SCHEDULER_COLOR = "ff9";
    public static final String DIRECT_SCHEDULER_COLOR = "ccc";
    public static final String TEXT_COLOR = "000";
    public static final String GROUP_COLOR = "9cf";
    public static final String SUBSTITUTION_COLOR = "f88";

    /**
     * A map from vertex name to vertex.
     */
    private final Map<String, ModelVertex> vertexMap;

    // TODO javadoc
    public WiringFlowchart(
            @NonNull final Map<String, ModelVertex> modelVertexMap,
            @NonNull final List<ModelEdgeSubstitution> substitutions,
            @NonNull final List<ModelGroup> groups) {

        Objects.requireNonNull(modelVertexMap);
        // TODO

        vertexMap = copyVertexMap(modelVertexMap);
        substituteEdges(substitutions);
        handleGroups(groups);
    }

    /**
     * Do a deep copy of the vertex map. Allows the local copy to be modified without affecting the original.
     *
     * @return a deep copy of the vertex map
     */
    @NonNull
    private Map<String, ModelVertex> copyVertexMap(@NonNull final Map<String, ModelVertex> original) {
        final Map<String, ModelVertex> copy = new HashMap<>();

        // First, copy the vertices without copying the edges.
        // We should only encounter StandardVertex instances here.
        for (final ModelVertex vertex : original.values()) {
            if (!(vertex instanceof StandardVertex)) {
                throw new IllegalStateException("Encountered a vertex that is not a StandardVertex");
            }
            final StandardVertex vertexCopy = new StandardVertex(
                    vertex.getName(),
                    vertex.getType(),
                    SCHEDULER,
                    vertex.isInsertionIsBlocking());

            copy.put(vertex.getName(), vertexCopy);
        }

        // Next, copy the edges.
        for (final ModelVertex vertex : original.values()) {
            for (final ModelEdge edge : vertex.getOutgoingEdges()) {

                final ModelVertex source = copy.get(edge.getSource().getName());
                final ModelVertex destination = copy.get(edge.getDestination().getName());

                final ModelEdge edgeCopy = new ModelEdge(
                        source,
                        destination,
                        edge.getLabel(),
                        edge.isInsertionIsBlocking());

                source.connectToEdge(edgeCopy);
            }
        }

        return copy;
    }

    /**
     * Find all edges that need to be substituted and perform the substitution.
     */
    private void substituteEdges(@NonNull final List<ModelEdgeSubstitution> substitutions) {
        for (final ModelEdgeSubstitution substitution : substitutions) {
            substituteEdge(substitution);
        }
    }

    /**
     * Perform a single edge substitution.
     */
    private void substituteEdge(@NonNull final ModelEdgeSubstitution substitution) {
        // First, create a new vertex that will represent the destination of the substitution.
        final StandardVertex substitutedVertex = new StandardVertex(
                substitution.substitution(),
                DIRECT,
                SUBSTITUTION,
                true);
        vertexMap.put(substitution.substitution(), substitutedVertex);

        // Next, cause all substituted edges to point to this new vertex.
        for (final ModelVertex vertex : vertexMap.values()) {
            if (!substitution.source().equals(vertex.getName())) {
                // Only replace edges with the given source.
                continue;
            }

            final HashSet<ModelEdge> uniqueEdges = new HashSet<>();

            for (final ModelEdge edge : vertex.getOutgoingEdges()) {
                if (!substitution.edge().equals(edge.getLabel())) {
                    // Only replace destinations for edges with the given label.
                    vertex.getOutgoingEdges().add(edge);
                    continue;
                }

                edge.getDestination().addSubstitutedInput(substitution.substitution());
                edge.setDestination(substitutedVertex);
                uniqueEdges.add(edge);
            }

            vertex.getOutgoingEdges().clear();
            vertex.getOutgoingEdges().addAll(uniqueEdges);
        }
    }

    /**
     * Handle groups in the order provided.
     */
    private void handleGroups(@NonNull final List<ModelGroup> groups) {
        for (final ModelGroup group : groups) {
            createGroup(group);
            if (group.collapse()) {
                // TODO
            }
        }
    }

    /**
     * Collect all vertices that are contained within the given group and create a new vertex that represents the
     * group.
     *
     * @param group the group to create a vertex for
     * @return the new vertex
     */
    private GroupVertex createGroup(@NonNull final ModelGroup group) { // TODO is return value needed?
        // Collect all vertices that are contained within the group.
        final List<ModelVertex> subVertices = new ArrayList<>();

        for (final String vertexName : group.elements()) {
            final ModelVertex subVertex = vertexMap.get(vertexName);
            if (subVertex == null) {
                throw new IllegalStateException(
                        "Vertex " + vertexName + " is not in the vertex map. Can not insert into group "
                                + group.name() + ".");
            }

            subVertices.add(subVertex);
        }

        // Remove those vertices from the vertex map.
        for (final ModelVertex subVertex : subVertices) {
            vertexMap.remove(subVertex.getName());
        }

        // Create a new vertex that represents the group.
        final GroupVertex groupVertex = new GroupVertex(group.name(), subVertices);
        vertexMap.put(group.name(), groupVertex);

        return groupVertex;
    }

    /**
     * Get all edges in the flowchart.
     *
     * @return all edges in the flowchart, sorted
     */
    private List<ModelEdge> collectEdges() {
        final List<ModelEdge> edges = new ArrayList<>();
        final LinkedList<ModelVertex> stack = new LinkedList<>();

        for (final ModelVertex vertex : vertexMap.values()) {
            stack.addLast(vertex);
        }

        while (!stack.isEmpty()) {
            final ModelVertex vertex = stack.removeLast();
            edges.addAll(vertex.getOutgoingEdges());
            if (vertex instanceof final GroupVertex groupVertex) {
                for (final ModelVertex subVertex : groupVertex.getSubVertices()) {
                    stack.addLast(subVertex);
                }
            }
        }

        Collections.sort(edges);
        return edges;
    }

    /**
     * Render the flowchart to a string.
     *
     * @return the rendered flowchart
     */
    @NonNull
    public String render() {
        final StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        final List<ModelVertex> sortedVertices = vertexMap.values().stream().sorted().toList();
        for (final ModelVertex vertex : sortedVertices) {
            vertex.render(sb);
        }

        for (final ModelEdge edge : collectEdges()) {
            edge.render(sb);
        }

        return sb.toString();
    }
}
