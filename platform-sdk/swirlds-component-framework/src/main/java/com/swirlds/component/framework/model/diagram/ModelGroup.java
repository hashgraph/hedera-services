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

package com.swirlds.component.framework.model.diagram;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Describes a group of components that should be visualized together in a wiring diagram. Specified via strings since
 * this configuration is presumably read from the command line.
 *
 * @param name     the name of the group
 * @param elements the set of subcomponents in the group
 * @param collapse true if the group should be collapsed into a single box
 */
public record ModelGroup(@NonNull String name, @NonNull Set<String> elements, boolean collapse)
        implements Comparable<ModelGroup> {

    /**
     * Sorts groups by name.
     */
    @Override
    public int compareTo(@NonNull final ModelGroup that) {
        return name.compareTo(that.name);
    }
}
