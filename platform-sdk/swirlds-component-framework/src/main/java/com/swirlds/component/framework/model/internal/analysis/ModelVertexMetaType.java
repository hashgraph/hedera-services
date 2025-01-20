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

/**
 * The type of a vertex in a wiring flowchart. Although the original graph will be constructed of SCHEDULER vertices
 * alone, when generating the flowchart, verticies will be added, removed and combined. New verticies that do not
 * directly correspond to a scheduler will be of type SUBSTITUTION or GROUP.
 */
public enum ModelVertexMetaType {
    /**
     * A vertex that corresponds to a scheduler.
     */
    SCHEDULER,
    /**
     * A vertex that is used as a stand-in for a substituted edge.
     */
    SUBSTITUTION,
    /**
     * A vertex that is used as a stand-in for a group of vertices.
     */
    GROUP
}
