/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.model.internal.analysis;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates shortened names in a mermaid wiring flowchart. Reduces the resulting flowchart size.
 */
public class MermaidNameShortener {

    private final Map<String, String> vertexNameMap = new HashMap<>();

    /**
     * Get the name that should be used for a vertex with the given base name.
     *
     * @param baseName the base name of the vertex
     * @return the name that should be used for a vertex with the given base name
     */
    @NonNull
    public String getShortVertexName(@NonNull final String baseName) {
        return vertexNameMap.computeIfAbsent(baseName, this::generateShortVertexName);
    }

    /**
     * Generate a name for a vertex with the given base name.
     *
     * @param baseName the base name of the vertex
     * @return the name that should be used for a vertex with the given base name
     */
    @NonNull
    private String generateShortVertexName(@NonNull final String baseName) {
        return "v" + vertexNameMap.size();
    }
}
