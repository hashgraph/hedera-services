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
import java.util.LinkedList;
import java.util.Set;

/**
 * A utility for finding cyclical backpressure in a wiring model.
 */
public final class CycleFinder {

    private CycleFinder() {}

    /**
     * Check for cyclical backpressure in a wiring model.
     *
     * @param vertices the vertices in the wiring model
     * @return true if there is cyclical backpressure
     */
    public static boolean checkForCyclicalBackpressure(@NonNull final Set<ModelVertex> vertices) {
        for (final ModelVertex vertex : vertices) {
            if (checkForCycleStartingFromVertex(vertex, vertices)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check for a cycle starting from a vertex.
     *
     * @param start    the vertex to start from
     * @param vertices the vertices in the wiring model
     * @return true if there is a cycle
     */
    private static boolean checkForCycleStartingFromVertex(
            @NonNull final ModelVertex start, @NonNull final Set<ModelVertex> vertices) {

        // Perform a depth first traversal of the graph starting from the given vertex.
        // Ignore any edge that doesn't apply back pressure.

        final LinkedList<ModelVertex> stack = new LinkedList<>();

        // TODO

        return false;
    }
}
