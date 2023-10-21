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

import static com.swirlds.common.utility.NonCryptographicHashing.hash32;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A directed edge between to vertices.
 *
 * @param source              the source vertex
 * @param destination         the destination vertex
 * @param label               the label of the edge, if a label is not needed for an edge then holds the value ""
 * @param insertionIsBlocking true if the insertion of this edge may block until capacity is available
 */
public record ModelEdge(
        @NonNull ModelVertex source,
        @NonNull ModelVertex destination,
        @NonNull String label,
        boolean insertionIsBlocking) {

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ModelEdge that) {
            return this.source.equals(that.source)
                    && this.destination.equals(that.destination)
                    && this.label.equals(that.label);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash32(source.hashCode(), destination.hashCode(), label.hashCode());
    }

    @Override
    public String toString() {
        return source + " --" + label + "-->" + (insertionIsBlocking ? "" : ">") + " " + destination;
    }
}
