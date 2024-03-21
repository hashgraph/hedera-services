/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.state;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * A base implementation of {@link WritableQueueState} that buffers all modifications and reads.
 * @param <E> The type of element in the queue.
 */
public abstract class WritableQueueStateBase<E> implements WritableQueueState<E> {
    /** The state key */
    private final String stateKey;
    /** Each element that has been read. At the moment these are not exposed, but could be. */
    private final List<E> readElements = new ArrayList<>();
    /** Each element that has been added to the queue, but not yet committed */
    private final List<E> addedElements = new ArrayList<>();
    /**
     * The current index into {@link #addedElements} that we have read from.
     *
     * <p>When a client reads from the queue (either with peek, poll, remove, or iterator) we will first read from
     * the backing data source, but after we have read everything from there, we also need to read from the
     * {@link #addedElements} list. This index keeps track of where we have read from it.
     */
    private int currentAddedElementIndex = 0;
    /** An iterator from the backing datasource for reading data */
    private Iterator<E> dsIterator = null;
    /** The cached most recent peeked element */
    private E peekedElement = null;

    /** Create a new instance */
    protected WritableQueueStateBase(@NonNull final String stateKey) {
        this.stateKey = requireNonNull(stateKey);
    }

    /**
     * Gets whether this queue has been modified, either by reading elements off the queue, or by adding elements to
     * the queue.
     *
     * @return If the queue has been modified.
     */
    public boolean isModified() {
        return !readElements.isEmpty() || !addedElements.isEmpty();
    }

    /**
     * Flushes all changes into the underlying data store. This method should <strong>ONLY</strong>
     * be called by the code that created the {@link WritableKVStateBase} instance or owns it. Don't
     * cast and commit unless you own the instance!
     */
    public final void commit() {
        for (int i = 0; i < readElements.size(); i++) {
            removeFromDataSource();
        }

        for (final var addedElement : addedElements) {
            addToDataSource(addedElement);
        }

        reset();
    }

    /**
     * Clears the set of peeked values and added values.
     */
    public final void reset() {
        readElements.clear();
        addedElements.clear();
        peekedElement = null;
        dsIterator = null;
    }

    @NonNull
    @Override
    public String getStateKey() {
        return stateKey;
    }

    @Nullable
    @Override
    // Suppressing the warning about the nested ternary operations
    @SuppressWarnings("java:S3358")
    public E peek() {
        if (peekedElement != null) return peekedElement;
        if (dsIterator == null) dsIterator = iterateOnDataSource();
        peekedElement = dsIterator.hasNext()
                ? dsIterator.next()
                : currentAddedElementIndex < addedElements.size()
                        ? addedElements.get(currentAddedElementIndex++)
                        : null;
        return peekedElement;
    }

    @Override
    public void add(@NonNull final E element) {
        addedElements.add(element);
    }

    @Nullable
    @Override
    public E removeIf(@NonNull final Predicate<E> predicate) {
        final var element = peek();

        if (element == null) {
            return null;
        }

        if (predicate.test(element)) {
            readElements.add(element);
            peekedElement = null;
            return element;
        }

        return null;
    }

    @NonNull
    @Override
    public Iterator<E> iterator() {
        final var iterator = iterateOnDataSource();
        final var addedElementsIterator = addedElements.iterator();
        final var numAddedElements = addedElements.size();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext() || addedElementsIterator.hasNext();
            }

            @Override
            public E next() {
                if (numAddedElements != addedElements.size()) {
                    throw new ConcurrentModificationException();
                }
                return iterator.hasNext() ? iterator.next() : addedElementsIterator.next();
            }
        };
    }

    /**
     * Adds the given element to the end of the data source.
     * @param element The element to add
     */
    protected abstract void addToDataSource(@NonNull final E element);

    /**
     * Removes the current item at the head of the data source queue.
     */
    protected abstract void removeFromDataSource();

    /**
     * Creates and returns an iterator over all data in the data source.
     * @return An iterator
     */
    @NonNull
    protected abstract Iterator<E> iterateOnDataSource();
}
