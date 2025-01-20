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

/**
 * Describes an edge substitution. A substituted edge is not drawn on the diagram, and is instead noted using a label.
 * Useful for situations where a component is connected with a large number of other components (thus making the diagram
 * hard to read).
 *
 * @param source       the name of the scheduler that produces the output wire corresponding to the edge we are
 *                     attempting to substitute (NOT the group name, if grouped)
 * @param edge         the label on the edge(s) to be substituted
 * @param substitution the substitute label
 */
public record ModelEdgeSubstitution(@NonNull String source, @NonNull String edge, @NonNull String substitution) {}
