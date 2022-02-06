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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class BaseMapValueLinkedList<K, S extends MapValueListNode<K, S>, V> implements MapValueLinkedList<K, S, V> {
	private int size = 0;
	private K head = null;
	private K tail = null;

	@Override
	public void addLast(
			final K key,
			final S node,
			final int maxSize,
			final BiConsumer<K, S> adder,
			final Function<K, S> getter
	) {
		if (head == null) {
			head = key;
			tail = key;
		} else {
			final var lastNode = getter.apply(tail);
			lastNode.setNextKey(key);
			node.setPrevKey(tail);
			tail = key;
		}
		size++;
		adder.accept(key, node);
	}

	@Override
	public void remove(
			final S node,
			final Predicate<K> check,
			final Consumer<S> remover,
			final Function<K, S> getter
	) {

	}

	@Override
	public List<V> listValues(final Function<K, S> getter, final Function<S, V> valueOf) {
		if (head == null) {
			return Collections.emptyList();
		}
		final List<V> all = new ArrayList<>();
		K key = head;
		for (int i = 0; i < size; i++) {
			final var node = getter.apply(key);
			all.add(valueOf.apply(node));
			key = node.nextKey();
		}
		return all;
	}
}
