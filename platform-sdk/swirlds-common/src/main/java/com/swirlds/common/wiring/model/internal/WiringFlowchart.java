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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A readable wiring flowchart.
 */
public class WiringFlowchart {

    /**
     * A map from vertex name to vertex.
     */
    private final Map<String, ModelVertex> vertexMap;

    public WiringFlowchart(
            @NonNull final Map<String, ModelVertex> modelVertexMap,
            @NonNull final List<ModelEdgeSubstitution> substitutions,
            @NonNull final List<ModelGroup> groups) {

        Objects.requireNonNull(modelVertexMap);
        // TODO

        vertexMap = copyVertexMap(modelVertexMap);
        substituteEdges(substitutions);
        collapseGroups();

    }

    /**
     * Do a deep copy of the vertex map. Allows the local copy to be modified without affecting the original.
     *
     * @return a deep copy of the vertex map
     */
    @NonNull
    private Map<String, ModelVertex> copyVertexMap(@NonNull final Map<String, ModelVertex> modelVertexMap) {
        final Map<String, ModelVertex> copy = new HashMap<>();

        // First, copy the vertices without copying the edges.
        for (final ModelVertex vertex : modelVertexMap.values()) {
            final ModelVertex vertexCopy = new ModelVertex(
                    vertex.getName(),
                    vertex.getType(),
                    SCHEDULER,
                    vertex.isInsertionIsBlocking());

            copy.put(vertex.getName(), vertexCopy);
        }

        // Next, copy the edges.
        for (final ModelVertex vertex : modelVertexMap.values()) {
            for (final ModelEdge edge : vertex) {

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
        final ModelVertex substitutedVertex = new ModelVertex(
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
     * For each group that requires collapsing, collapse the group.
     */
    private void collapseGroups() {
        // TODO
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

        final List<ModelEdge> sortedEdges = new ArrayList<>();
        for (final ModelVertex vertex : sortedVertices) {
            for (final ModelEdge edge : vertex) {
                sortedEdges.add(edge);
            }
        }
        Collections.sort(sortedEdges);

        for (final ModelVertex vertex : sortedVertices) {
            vertex.render(sb);
        }

        for (final ModelEdge edge : sortedEdges) {
            edge.render(sb);
        }

        return sb.toString();
    }

}
