/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class that is used to uniquely identify a Swirlds Node
 *
 * @param id ID number unique within the network
 */
public record NodeId(long id) implements Comparable<NodeId> {

    /** the map of all NodeId objects created, indexed by ID number */
    private static final ConcurrentHashMap<Long, NodeId> nodeIds = new ConcurrentHashMap<>();

    /** an invalid NodeId used in testing and boundary conditions. */
    public static final NodeId INVALID_NODE_ID = NodeId.create(-1L);

    /** the first NodeId object created */
    public static final NodeId FIRST_NODE_ID = NodeId.create(0L);

    /**
     * Constructs a NodeId object
     *
     * @param id the ID number
     */
    public NodeId {
        if (id < -1L) {
            throw new IllegalArgumentException("id must be non-negative");
        }
    }

    /**
     * Constructs a main network NodeId object
     *
     * @param id the ID number
     * @return the object created
     */
    public static NodeId create(long id) {
        return nodeIds.computeIfAbsent(id, NodeId::new);
    }

    /**
     * Checks if this NodeId is equal to the given ID value
     *
     * @param id the ID value to compare
     * @return true if this ID value is equal to the supplied value, false otherwise
     * @deprecated use {@link #equals(Object)} instead.
     */
    @Deprecated(since = "0.39.0", forRemoval = true)
    public boolean equals(long id) {
        return this.id == id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(@NonNull final NodeId other) {
        Objects.requireNonNull(other, "NodeId cannot be null");
        return Long.compare(this.id, other.id);
    }

    /**
     * get numeric part of ID and cast to an Integer
     *
     * @return the numeric part of this ID, cast to an integer
     * @deprecated use {@link #id()} instead.
     */
    @Deprecated(since = "0.39.0", forRemoval = true)
    public int getIdAsInt() {
        return (int) id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return Long.toString(id);
    }
}
