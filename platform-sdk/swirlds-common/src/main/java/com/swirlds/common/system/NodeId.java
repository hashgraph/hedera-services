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

/**
 * A class that is used to uniquely identify a Swirlds Node
 */
public class NodeId {
    /** ID number unique within the network, unique set for main network and mirror network */
    private final long id;

    /**
     * Constructs a NodeId object
     *
     * @param id the ID number
     */
    public NodeId(long id) {
        this.id = id;
    }

    /**
     * Constructs a main network NodeId object
     *
     * @param id
     * 		the ID number
     * @return the object created
     */
    public static NodeId create(long id) {
        return new NodeId(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NodeId)) {
            throw new IllegalArgumentException("obj must be a NodeId object");
        }
        return equals((NodeId) obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    /**
     * Checks if IDs are equal
     *
     * @param nodeId
     * 		the NodeId to compare
     * @return true if equal, false if not
     */
    public boolean equals(NodeId nodeId) {
        return this.id == nodeId.id;
    }

    /**
     * Checks if this NodeId is main network and if the ID value is equal
     *
     * @param id
     * 		the ID value to compare
     * @return true if this is a main network ID and its ID value is equal to the supplied value, false if either of
     * 		these conditions are not true
     */
    public boolean equalsMain(long id) {
        return this.id == id;
    }

    /**
     * Check if numeric part of this ID
     *
     * @return the numeric part of this ID
     */
    public long getId() {
        return id;
    }

    /**
     * get numeric part of ID and cast to an Integer
     *
     * @return the numeric part of this ID, cast to an integer
     */
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
