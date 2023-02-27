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

package com.swirlds.common.utility;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * This class can be used to wrap an iterator that is modifiable to make it unmodifiable.
 *
 * @param <T>
 * 		the type of the object that the iterator walks over
 */
public class UnmodifiableIterator<T> implements Iterator<T> {

    private final Iterator<T> baseIterator;

    public UnmodifiableIterator(final Iterator<T> baseIterator) {
        throwArgNull(baseIterator, "baseIterator");
        this.baseIterator = baseIterator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return baseIterator.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T next() {
        return baseIterator.next();
    }

    /**
     * Not supported.
     *
     * @throws UnsupportedOperationException
     * 		if called
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forEachRemaining(final Consumer<? super T> action) {
        baseIterator.forEachRemaining(action);
    }
}
