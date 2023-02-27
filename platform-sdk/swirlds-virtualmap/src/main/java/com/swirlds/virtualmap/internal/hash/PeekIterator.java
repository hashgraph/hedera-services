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

package com.swirlds.virtualmap.internal.hash;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * An {@link Iterator} that supports "peeking" at the next item without actually removing it.
 * This implementation is a simple decorator around the underlying iterator. It does not implement
 * the optional {@link #remove()} method.
 * <p>
 * This seems like a fairly common data structure and should consider moving to a more permanent location.
 * See issue 4121.
 *
 * @param <T>
 *     The type.
 */
final class PeekIterator<T> implements Iterator<T> {
    private final Iterator<T> itr;
    private T next;

    /**
     * Create a new PeekIterator. The supplied iterator must not be null.
     *
     * @param itr
     * 		The decorated iterator. Cannot be null
     */
    PeekIterator(Iterator<T> itr) {
        this.itr = Objects.requireNonNull(itr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return next != null || itr.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T next() {
        if (next != null) {
            final T ret = next;
            next = null;
            return ret;
        } else {
            return itr.next();
        }
    }

    /**
     * Peeks at the next item in the iterator. This method has the same semantics as {@link #next()}
     * except that it <strong>does not</strong> remove the item from the iterator. That is,
     * a call to {@code peek()} followed by a call to {@link #next()} will return the same element.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if the iteration has no more elements
     */
    T peek() {
        if (next == null) {
            next = itr.next();
        }
        return next;
    }
}
