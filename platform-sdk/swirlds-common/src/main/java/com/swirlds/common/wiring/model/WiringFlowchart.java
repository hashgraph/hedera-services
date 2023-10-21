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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Set;

/**
 * A utility for drawing mermaid style flowcharts of wiring models.
 */
public final class WiringFlowchart {

    private WiringFlowchart() {}

    private static final String INDENTATION = "    ";
    private static final String COMMENT = "%%";

    /**
     * Draw an edge.
     *
     * @param sb   a string builder where the mermaid file is being assembled
     * @param edge the edge to draw
     */
    private static void drawEdge(@NonNull final StringBuilder sb, @NonNull final ModelEdge edge) {

        sb.append(INDENTATION).append(edge.source().getName());

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

        sb.append(edge.destination().getName()).append("\n");
    }

    /**
     * Draw a vertex.
     *
     * @param sb     a string builder where the mermaid file is being assembled
     * @param vertex the vertex to draw
     */
    private static void drawVertex(@NonNull final StringBuilder sb, @NonNull final ModelVertex vertex) {
        sb.append(INDENTATION).append(vertex.getName()).append("\n");
        sb.append(INDENTATION)
                .append("style ")
                .append(vertex.getName())
                .append(" fill:#362,stroke:#000,stroke-width:2px,color:#fff\n");

        vertex.getOutgoingEdges().stream().sorted().forEachOrdered(edge -> drawEdge(sb, edge));

        sb.append("\n");
    }

    @NonNull
    public static String generateWiringDiagram(
            @NonNull final Map<String, ModelVertex> vertices, @NonNull final Set<ModelEdge> edges) {

        final StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        vertices.values().stream().sorted().forEachOrdered(vertex -> drawVertex(sb, vertex));

        return sb.toString();
    }
}
