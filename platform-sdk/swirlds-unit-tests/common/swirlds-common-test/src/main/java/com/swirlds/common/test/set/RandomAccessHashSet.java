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

package com.swirlds.common.test.set;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * This class implements a set that has O(1) time to get an element at a given index within the set.
 */
public class RandomAccessHashSet<T> implements RandomAccessSet<T> {

    private final Map<T, Integer> indices;
    private final List<T> list;

    public RandomAccessHashSet() {
        indices = new HashMap<>();
        list = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return list.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(final Object o) {
        if (o == null) {
            return false;
        }
        return indices.containsKey((T) o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<T> iterator() {
        return list.iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T1> T1[] toArray(final T1[] a) {
        return list.toArray(a);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(final T t) {
        if (indices.containsKey(t)) {
            return false;
        }
        final int newIndex = list.size();
        list.add(t);
        indices.put(t, newIndex);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get(final int index) {
        return list.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get(final Random random) {
        if (size() == 0) {
            throw new NoSuchElementException("can not get random element from empty set");
        }
        return get(random.nextInt(size()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(final Object o) {
        if (o == null || !indices.containsKey((T) o)) {
            return false;
        }

        final int indexToRemove = indices.get((T) o);
        final int lastIndex = list.size() - 1;

        if (indexToRemove != lastIndex) {
            // If we are not removing the last element then we need gap filling
            final T gapFillingElement = list.get(lastIndex);
            list.set(indexToRemove, gapFillingElement);
            indices.put(gapFillingElement, indexToRemove);
        }

        list.remove(lastIndex);
        indices.remove((T) o);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        list.clear();
        indices.clear();
    }
}
