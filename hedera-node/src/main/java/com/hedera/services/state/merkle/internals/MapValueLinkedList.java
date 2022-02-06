package com.hedera.services.state.merkle.internals;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.exceptions.InvalidTransactionException;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents a linked list of {@link MapValueListNode}s <b>with distinct keys</b>.
 *
 * @param <K>
 * 		the type of the key in this list (and associated map)
 * @param <S>
 * 		the value-aware node type
 * @param <V>
 * 		the type of value in a node
 */
public interface MapValueLinkedList<K, S extends MapValueListNode<K, S>, V> {
	/**
	 * Adds a new node to this linked list, using the provided function objects to update the map that contains
	 * the linked list (e.g., a {@link com.swirlds.virtualmap.VirtualMap} or {@link com.swirlds.merkle.map.MerkleMap}).
	 *
	 * @param key
	 * 		the key of the new node
	 * @param node
	 * 		the new node to append to the list
	 * @param maxSize
	 * 		the maximum size of the list
	 * @param adder
	 * 		a callback to add a new mapping
	 * @param getter
	 * 		a callback to get a node from the map
	 * @throws InvalidTransactionException
	 * 		if list maximum size would be exceeded
	 */
	void addLast(K key, S node, int maxSize, BiConsumer<K, S> adder, Function<K, S> getter);

	/**
	 * Removes an existing node from this list, using the provided function objects to update the map that
	 * contains the nodes of this linked list.
	 *
	 * @param node
	 * 		the node to remove
	 * @param check
	 * 		an existence check for map keys
	 * @param remover
	 * 		a callback remove an existing mapping
	 * @throws IllegalArgumentException
	 * 		if the node does not exist
	 */
	void remove(S node, Predicate<K> check, Consumer<S> remover, Function<K, S> getter);

	/**
	 * Provides a view of all the values in this list.
	 *
	 * @param getter
	 * 		a callback to get a node from the map
	 * @param valueOf
	 * 		a callback to get a node's value
	 * @return the values in the list
	 * @throws IllegalStateException
	 * 		if a cycle is found in the list
	 */
	List<V> listValues(Function<K, S> getter, Function<S, V> valueOf);
}
