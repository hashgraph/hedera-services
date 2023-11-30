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
        handleGroups(groups);
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
     * Handle groups in the order provided.
     */
    private void handleGroups(@NonNull final List<ModelGroup> groups) {
        for (final ModelGroup group : groups) {
            if (group.collapse()) {
                collapseGroup(group);
            } else {
                labelGroup(group);
            }
        }
    }

    /**
     * Collapse a group into a single vertex.
     *
     * @param group the group to collapse
     */
    private void collapseGroup(@NonNull final ModelGroup group) {

    }

    /**
     * Apply a group label to all vertices in the group. Used for groups that are not collapsed.
     *
     * @param group the group to label
     */
    private void labelGroup(@NonNull final ModelGroup group) {
        for (final String vertexName : group.elements()) {
            if (group.elements().contains(vertexName)) {
                vertexMap.get(vertexName).addToGroup(group.name());
            }
        }
    }

    /**
     * Groups are represented as subgraphs in the flowchart. This method the subgraph labels, as needed.
     *
     * @param sb                the string builder to append to
     * @param previousHierarchy the hierarchy of the previous vertex
     * @param currentHierarchy  the hierarchy of the current vertex
     */
    private void renderSubgraph(
            @NonNull final StringBuilder sb,
            @NonNull final List<String> previousHierarchy,
            @NonNull final List<String> currentHierarchy) {


        final List<String> groupsToEnd = new ArrayList<>();
        boolean foundDifference = false;
        for (int i = 0; i < previousHierarchy.size(); i++) {
            if (foundDifference) {
                groupsToEnd.add(previousHierarchy.get(i));
            } else if (i >= currentHierarchy.size() || !previousHierarchy.get(i).equals(currentHierarchy.get(i))) {
                foundDifference = true;
                groupsToEnd.add(previousHierarchy.get(i));
            }
        }

        final List<String> groupsToStart = new ArrayList<>();
        foundDifference = false;
        for (int i = 0; i < currentHierarchy.size(); i++) {
            if (foundDifference) {
                groupsToStart.add(currentHierarchy.get(i));
            } else if (i >= previousHierarchy.size() || !currentHierarchy.get(i).equals(previousHierarchy.get(i))) {
                foundDifference = true;
                groupsToStart.add(currentHierarchy.get(i));
            }
        }

        // TODO fix indentation
        for (final String group : groupsToEnd) {
            sb.append("end\n");// %% ").append(group).append("\n");
        }

        for (final String group : groupsToStart) {
            sb.append("subgraph ").append(group).append("\n");
        }
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

        List<String> currentHierarchy = List.of();
        for (final ModelVertex vertex : sortedVertices) {
            renderSubgraph(sb, currentHierarchy, vertex.getGroupHierarchy());
            vertex.render(sb);
            currentHierarchy = vertex.getGroupHierarchy();
        }

        renderSubgraph(sb, currentHierarchy, List.of());

        for (final ModelEdge edge : sortedEdges) {
            edge.render(sb);
        }

        return sb.toString();
    }

}
