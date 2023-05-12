/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util.iterator;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * An array iterator that skips certain indices.
 *
 * @param <T>
 * 		the type being iterated
 */
public class SkippingIterator<T> implements Iterator<T> {

    private int cursor;
    private final T[] array;
    private final Set<Integer> skipIndices;
    private int lastReturnableIndex;

    /**
     * Creates a new instance. Mutations to {@code skipIndices} do not affect this instance.
     *
     * @param array
     * 		the array to iterate over
     * @param skipIndices
     * 		the zero based indices to skip over
     */
    public SkippingIterator(final T[] array, final Set<Integer> skipIndices) {
        throwArgNull(array, "array must not be null");

        this.array = array;
        this.skipIndices = skipIndices == null ? Collections.emptySet() : Set.copyOf(skipIndices);

        lastReturnableIndex = -1;
        for (int i = array.length - 1; i >= 0; i--) {
            if (!this.skipIndices.contains(i)) {
                lastReturnableIndex = i;
                break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return cursor < array.length && cursor <= lastReturnableIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T next() {
        while (skipIndices.contains(cursor)) {
            cursor++;
        }
        final int i = cursor;
        if (i > lastReturnableIndex) {
            throw new NoSuchElementException();
        }
        cursor = i + 1;
        return array[i];
    }
}
