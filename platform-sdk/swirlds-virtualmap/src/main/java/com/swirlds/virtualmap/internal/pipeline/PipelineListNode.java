/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.pipeline;

/**
 * A single link in a {@link PipelineList}.
 *
 * @param <T>
 * 		the type of the data contained
 */
class PipelineListNode<T> {

    private final T value;

    private PipelineListNode<T> previous;
    private PipelineListNode<T> next;

    PipelineListNode(final T value) {
        this.value = value;
    }

    /**
     * Get the value contained by this node.
     *
     * @return the value
     */
    T getValue() {
        return value;
    }

    /**
     * Get the previous node, or null if there is no previous node.
     *
     * @return the previous node
     */
    synchronized PipelineListNode<T> getPrevious() {
        return previous;
    }

    /**
     * Get the next node, or null if there is no next node.
     *
     * @return the next node
     */
    synchronized PipelineListNode<T> getNext() {
        return next;
    }

    /**
     * Remove a node from the list. Intentionally package private.
     */
    synchronized void remove() {
        if (previous != null) {
            previous.next = next;
        }
        if (next != null) {
            next.previous = previous;
        }
    }

    /**
     * Add the next node in the linked list. Intentionally package private.
     *
     * @param nextNode
     * 		the node to add
     */
    synchronized void addNext(final PipelineListNode<T> nextNode) {
        if (next != null) {
            throw new IllegalStateException("this list does not currently support insertions in the middle");
        }
        if (nextNode.previous != null) {
            throw new IllegalStateException("element to be inserted already has a previous value");
        }
        next = nextNode;
        nextNode.previous = this;
    }

    @Override
    public String toString() {
        return "(" + value + ")";
    }
}
