/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.model.internal.analysis;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the styles in a mermaid flowchart.
 */
public class MermaidStyleManager {

    private final Map<String /* full style string */, String /* style name */> styleToStyleName = new HashMap<>();
    private final Map<String /* style name */, String /* full style string */> styleNameToStyle = new HashMap<>();
    private final Map<String /* style name */, List<String> /* classes with style */> styleNameToClasses =
            new HashMap<>();

    /**
     * Register a style string. Will be attached to a vertex with the given base name at a later time.
     *
     * @param name  the name of the vertex with the style
     * @param style the style string
     */
    public void registerStyle(@NonNull final String name, @NonNull final String style) {
        final String styleName = styleToStyleName.computeIfAbsent(style, this::generateShortStyleName);
        styleNameToStyle.put(styleName, style);
        styleNameToClasses.computeIfAbsent(styleName, x -> new ArrayList<>()).add(name);
    }

    /**
     * Generate a name for a style.
     *
     * @param style the style string
     * @return the name that should be used for a style
     */
    @NonNull
    private String generateShortStyleName(@NonNull final String style) {
        return "s" + styleToStyleName.size();
    }

    /**
     * Render the styles to the given string builder.
     *
     * @param sb the string builder
     */
    public void render(@NonNull final StringBuilder sb) {
        styleNameToStyle.keySet().stream().sorted().forEachOrdered(styleName -> {
            sb.append("classDef ")
                    .append(styleName)
                    .append(" ")
                    .append(styleNameToStyle.get(styleName))
                    .append("\n");
            sb.append("class ");
            final List<String> classNames = styleNameToClasses.get(styleName);
            Collections.sort(classNames);
            for (int i = 0; i < classNames.size(); i++) {
                sb.append(classNames.get(i));
                if (i < classNames.size() - 1) {
                    sb.append(",");
                }
            }
            sb.append(" ").append(styleName).append("\n");
        });
    }
}
