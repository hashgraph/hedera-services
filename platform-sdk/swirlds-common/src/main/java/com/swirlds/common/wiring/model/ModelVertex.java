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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A vertex in a wiring model.
 */
public class ModelVertex implements Iterable<ModelEdge> {

    private final String name;
    private final boolean insertionIsBlocking;

    private final List<ModelEdge> outgoingEdges = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param name                the name of the vertex
     * @param insertionIsBlocking true if the insertion of this vertex may block until capacity is available
     */
    public ModelVertex(@NonNull String name, boolean insertionIsBlocking) {
        this.name = name;
        this.insertionIsBlocking = insertionIsBlocking;
    }

    /**
     * Get the name of the vertex.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get whether the insertion of this vertex may block until capacity is available.
     *
     * @return true if the insertion of this vertex may block until capacity is available
     */
    public boolean isInsertionIsBlocking() {
        return insertionIsBlocking;
    }

    /**
     * Add an outgoing edge to this vertex.
     *
     * @param vertex the edge to connect to
     */
    public void connectToEdge(@NonNull final ModelEdge vertex) {
        outgoingEdges.add(Objects.requireNonNull(vertex));
    }

    /**
     * Get an iterator that walks over the outgoing edges of this vertex.
     *
     * @return an iterator that walks over the outgoing edges of this vertex
     */
    @Override
    @NonNull
    public Iterator<ModelEdge> iterator() {
        return outgoingEdges.iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ModelVertex) && name.equals(((ModelVertex) obj).name);
    }

    @Override
    public String toString() {
        if (insertionIsBlocking) {
            return "[" + name + "]";
        } else {
            return "(" + name + ")";
        }
    }
}
