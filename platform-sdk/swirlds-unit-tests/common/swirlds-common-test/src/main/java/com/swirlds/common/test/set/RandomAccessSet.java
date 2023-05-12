/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import java.util.Collection;
import java.util.Random;
import java.util.Set;

/**
 * A set that supports efficient access of random elements.
 *
 * @param <T>
 */
public interface RandomAccessSet<T> extends Set<T> {

    /**
     * Get a random element. Element is chosen with even probability of selecting any element in the set.
     * O(1) time.
     *
     * @param random
     * 		a source of randomness
     * @return a randomly chosen element
     * @throws java.util.NoSuchElementException
     * 		if the set is empty
     */
    T get(Random random);

    /**
     * Get an element at a given index. Element returned is guaranteed to be deterministic. Mutating the set
     * in any way may cause a different element to be returned for the same index. O(1) time.
     *
     * @param index
     * 		the index of the element to return
     * @return the element that corresponds to the given index
     */
    T get(int index);

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean containsAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean addAll(final Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }
}
