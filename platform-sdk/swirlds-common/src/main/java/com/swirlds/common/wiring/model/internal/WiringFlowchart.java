package com.swirlds.common.wiring.model.internal;

import com.swirlds.common.wiring.model.ModelEdgeSubstitution;
import com.swirlds.common.wiring.model.ModelGroup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
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
        substituteEdges();
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
                    vertex.isInsertionIsBlocking());

            copy.put(vertex.getName(), vertexCopy);
        }

        // Next, copy the edges.
        for (final ModelVertex vertex : modelVertexMap.values()) {
            for (final ModelEdge edge : vertex) {

                final ModelVertex source = copy.get(edge.source().getName());
                final ModelVertex destination = copy.get(edge.destination().getName());

                final ModelEdge edgeCopy = new ModelEdge(
                        source,
                        destination,
                        edge.label(),
                        edge.insertionIsBlocking());

                source.connectToEdge(edgeCopy);
            }
        }

        return copy;
    }

    /**
     * Find all edges that need to be substituted and perform the substitution.
     */
    private void substituteEdges() {
        // TODO
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

        // Render vertices
        vertexMap.values().stream().sorted().forEach(vertex -> {
            vertex.render(sb, 1);
        });

        // Render edges
        vertexMap.values().stream().sorted().forEach(vertex -> {
            // TODO force this to be in sorted order
            for (final ModelEdge edge : vertex) {
                edge.render(sb);
            }
        });

        return sb.toString();
    }

}
