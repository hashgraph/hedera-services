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

/**
 * A class that is used to uniquely identify a Swirlds Node.
 *
 * @param id ID number unique within the network
 */
public record NodeId(long id) implements Comparable<NodeId> {

    /** The first allowed Node ID. */
    public static final long LOWEST_NODE_NUMBER = 0L;

    /** the first NodeId record */
    public static final NodeId FIRST_NODE_ID = new NodeId(LOWEST_NODE_NUMBER);

    /**
     * Constructs a NodeId object
     * <p>
     * Use {@link #create(long)} to create a new NodeId object. This method will return an existing NodeId object if one
     * already exists with the same ID number.
     *
     * @param id the ID number
     */
    public NodeId {
        if (id < LOWEST_NODE_NUMBER) {
            throw new IllegalArgumentException("id must be non-negative");
        }
    }

    /**
     * Constructs a NodeId object
     *
     * @param id the ID number
     * @return the object created
     * @throws IllegalArgumentException if the ID number is negative
     */
    @NonNull
    public static NodeId create(long id) {
        return new NodeId(id);
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
    @NonNull
    @Override
    public String toString() {
        return Long.toString(id);
    }
}
