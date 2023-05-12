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
public class NodeId implements EventCreationRule {
    /** used to distinguish between a main node and a mirror node */
    private boolean isMirror;
    /** ID number unique within the network, unique set for main network and mirror network */
    private long id;

    /**
     * Constructs a NodeId object
     *
     * @param isMirror
     * 		is it a mirror node or main node
     * @param id
     * 		the ID number
     */
    public NodeId(boolean isMirror, long id) {
        this.isMirror = isMirror;
        this.id = id;
    }

    /**
     * Constructs a main network NodeId object
     *
     * @param id
     * 		the ID number
     * @return the object created
     */
    public static NodeId createMain(long id) {
        return new NodeId(false, id);
    }

    /**
     * Constructs a mirror network NodeId object
     *
     * @param id
     * 		the ID number
     * @return the object created
     */
    static NodeId createMirror(long id) {
        return new NodeId(true, id);
    }

    /**
     * Checks if two IDs belong to the same network
     *
     * @param nodeId
     * 		the NodeId to compare to
     * @return true if networks are the same, false if not
     */
    public boolean sameNetwork(NodeId nodeId) {
        return this.isMirror() == nodeId.isMirror();
    }

    private boolean equals(boolean isMirror, long id) {
        return this.isMirror() == isMirror && id == this.getId();
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

    @Override
    public int hashCode() {
        return Boolean.hashCode(isMirror) + Long.hashCode(id);
    }

    /**
     * Checks if IDs are equal
     *
     * @param nodeId
     * 		the NodeId to compare
     * @return true if equal, false if not
     */
    public boolean equals(NodeId nodeId) {
        return equals(nodeId.isMirror(), nodeId.getId());
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
        return equals(false, id);
    }

    /**
     * Checks if this NodeId is mirror network and if the ID value is equal
     *
     * @param id
     * 		the ID value to compare
     * @return true if this is a mirror network ID and its ID value is equal to the supplied value, false if either of
     * 		these conditions are not true
     */
    public boolean equalsMirror(long id) {
        return equals(true, id);
    }

    /**
     * Check if ID is part of mirror network
     *
     * @return true if this ID is part of the mirror network, false if not
     */
    public boolean isMirror() {
        return isMirror;
    }

    /**
     * Check if ID is part of main network
     *
     * @return true if this ID is part of the main network, false if not
     */
    public boolean isMain() {
        return !isMirror;
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
        return (isMirror ? "m" : "") + id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventCreationRuleResponse shouldCreateEvent() {
        // Only main nodes should create events
        if (isMain()) {
            return EventCreationRuleResponse.PASS;
        } else {
            return EventCreationRuleResponse.DONT_CREATE;
        }
    }
}
