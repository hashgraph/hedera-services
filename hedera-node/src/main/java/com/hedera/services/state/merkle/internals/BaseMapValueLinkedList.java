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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class BaseMapValueLinkedList<K, S extends MapValueListNode<K, S>, V> implements MapValueLinkedList<K, S, V> {
	private int size = 0;
	private K head = null;
	private K tail = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean addLast(
			@NotNull final K key,
			@NotNull final S node,
			@NotNull final int maxSize,
			@NotNull final BiConsumer<K, S> adder,
			@NotNull final Function<K, S> getter
	) {
		if (size == maxSize) {
			return false;
		}
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
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove(
			@NotNull final S node,
			@NotNull final Consumer<K> remover,
			@NotNull final Function<K, S> getter
	) {
		final var tbdKey = node.getKey();
		requireNonNull(tbdKey, "Only in-map nodes with non-null keys can be removed");
		if (tbdKey.equals(head)) {
			final var nextKey = node.nextKey();
			if (nextKey != null) {
				final var nextNode = getter.apply(nextKey);
				nextNode.setPrevKey(null);
				head = nextKey;
			} else {
				head = null;
				tail = null;
			}
		} else if (tbdKey.equals(tail)) {
			final var prevKey = node.prevKey();
			final var prevNode = getter.apply(prevKey);
			prevNode.setNextKey(null);
			tail = prevKey;
		} else {
			final var prevKey = node.prevKey();
			final var nextKey = node.nextKey();
			final var prevNode = getter.apply(prevKey);
			final var nextNode = getter.apply(nextKey);
			prevNode.setNextKey(nextKey);
			nextNode.setPrevKey(prevKey);
		}
		size--;
		remover.accept(tbdKey);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<V> listValues(@NotNull final Function<K, S> getter, @NotNull final Function<S, V> valueOf) {
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
