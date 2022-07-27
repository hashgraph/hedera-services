/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

import com.swirlds.common.FastCopyable;
import javax.annotation.Nullable;

/**
 * Facilitates the mutation of one or more keys from an implicit map whose values compose a family
 * of doubly-linked lists. The interface could be made more concise by making {@code K} and {@code
 * V} bounded types, but after some consideration this approach seemed to better suit our use cases.
 *
 * @param <K> the type of key in the linked-values map
 * @param <V> type of value in the linked-values map
 */
public interface MapValueListMutation<K, V extends FastCopyable> {
    /**
     * Gets the value for the specified key.
     *
     * @param key the key of interest
     * @return the map's value
     */
    @Nullable
    V get(K key);

    /**
     * Gets the mutable value for the specified key.
     *
     * @param key the key of interest
     * @return the map's mutable value
     */
    @Nullable
    V getForModify(K key);

    /**
     * Adds the given mapping.
     *
     * @param key the key of interest
     * @param value the corresponding value
     */
    void put(K key, V value);

    /**
     * Removes the value for the specified key.
     *
     * @param key the key of interest
     */
    void remove(K key);

    /**
     * Updates the given node as the root of its containing linked list.
     *
     * @param node the new root
     */
    void markAsHead(V node);

    /**
     * Updates the given node as the tail of its containing linked list.
     *
     * @param node the new tail
     */
    void markAsTail(V node);

    /**
     * Updates the given node's previous pointer.
     *
     * @param node the changed node
     * @param prev the previous node
     */
    void updatePrev(V node, K prev);

    /**
     * Updates the given node's next pointer.
     *
     * @param node the changed node
     * @param next the next node
     */
    void updateNext(V node, K next);

    /**
     * Returns the (map key of) the next node in the linked list containing the given node.
     *
     * @param node the current node in the list
     * @return the key of the next node
     */
    @Nullable
    K next(V node);

    /**
     * Returns the (map key of) the prev node in the linked list containing the given node.
     *
     * @param node the current node in the list
     * @return the key of the previous node
     */
    @Nullable
    K prev(V node);
}
