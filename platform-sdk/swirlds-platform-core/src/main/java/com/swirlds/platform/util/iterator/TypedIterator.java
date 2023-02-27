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

import java.util.Iterator;

/**
 * A wrapper for an iterator that allows the exposed type to be a super type of the iterator provided.
 * <p>
 * Support for {@link #remove()} is the same as the iterator provided.
 *
 * @param <T>
 * 		the type to expose to callers
 */
public class TypedIterator<T> implements Iterator<T> {

    private final Iterator<? extends T> itr;

    /**
     * Creates a new instance.
     *
     * @param itr
     * 		the iterator to wrap
     */
    public TypedIterator(final Iterator<? extends T> itr) {
        throwArgNull(itr, "itr must not be null");
        this.itr = itr;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return itr.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T next() {
        return itr.next();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove() {
        itr.remove();
    }
}
