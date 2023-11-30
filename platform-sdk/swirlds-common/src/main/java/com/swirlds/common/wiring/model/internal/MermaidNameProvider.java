package com.swirlds.common.wiring.model.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates shortening names for vertices in a mermaid wiring flowchart. Reduces the resulting flowchart size.
 */
public class MermaidNameProvider {

    private final Map<String, String> nameMap = new HashMap<>();

    /**
     * Get the name that should be used for a vertex with the given base name.
     *
     * @param baseName the base name of the vertex
     * @return the name that should be used for a vertex with the given base name
     */
    @NonNull
    public String getShortenedName(@NonNull final String baseName) {
        return nameMap.computeIfAbsent(baseName, this::generateShortenedName);
    }

    /**
     * Generate a name for a vertex with the given base name.
     *
     * @param baseName the base name of the vertex
     * @return the name that should be used for a vertex with the given base name
     */
    private String generateShortenedName(@NonNull final String baseName) {
        return "x" + nameMap.size();
    }
}
