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

package com.swirlds.jasperdb.collections;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An implementation of ImmutableIndexedObjectList that stores its objects in an on-heap array. The position of each
 * object in the array is its self-reported index minus a fixed offset.
 *
 * This means lookups by self-reported index are extremely fast, but the array may waste some space if self-reported
 * indexes are not sequential.
 *
 * @param <T>
 * 		the type of the IndexedObject in the list
 */
public class ImmutableIndexedObjectListUsingArray<T extends IndexedObject> implements ImmutableIndexedObjectList<T> {
    /**
     * Offset to subtract from self-reported index to get an object's position in the data array (if present).
     */
    private final int firstIndexOffset;

    /**
     * The array of data objects, with each object positioned at its self-reported index minus the firstIndexOffset.
     */
    private final T[] dataArray;

    /**
     * Creates a new ImmutableIndexedObjectList from an existing array of objects.
     */
    public ImmutableIndexedObjectListUsingArray(final T[] objects) {
        this(Arrays.asList(objects));
    }

    /**
     * Creates a new ImmutableIndexedObjectList from an existing list of objects.
     */
    public ImmutableIndexedObjectListUsingArray(final List<T> objects) {
        Objects.requireNonNull(objects);

        final List<T> nonNullObjects = new ArrayList<>();
        for (final T object : objects) {
            if (object != null) {
                nonNullObjects.add(object);
            }
        }

        if (nonNullObjects.isEmpty()) {
            firstIndexOffset = 0;
            dataArray = null;
        } else {
            // Ensure data is sorted by self-reported index
            nonNullObjects.sort(Comparator.comparingInt(IndexedObject::getIndex));
            // Determine the first and last indexes
            final T firstObject = nonNullObjects.get(0);
            firstIndexOffset = firstObject.getIndex();
            final int lastIndex = nonNullObjects.get(nonNullObjects.size() - 1).getIndex();
            final int range = lastIndex - firstIndexOffset + 1;

            // Create a sufficiently large data array and place the nonNullObjects in the correct positions
            //noinspection unchecked
            dataArray = (T[]) Array.newInstance(firstObject.getClass(), range);
            for (final T object : nonNullObjects) {
                dataArray[object.getIndex() - firstIndexOffset] = object;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ImmutableIndexedObjectListUsingArray<T> withAddedObject(final T newObject) {
        // Ignore null objects
        if (newObject == null) {
            return this;
        }

        // Create a temp array list with just non-null objects that belong to the new list
        final List<T> newDataArray = new ArrayList<>(Arrays.asList(dataArray));
        newDataArray.removeIf(next -> next == null || next.getIndex() == newObject.getIndex());
        newDataArray.add(newObject);

        return new ImmutableIndexedObjectListUsingArray<>(newDataArray);
    }

    /**
     * {@inheritDoc}
     */
    public ImmutableIndexedObjectListUsingArray<T> withDeletedObjects(final Set<T> objectsToDelete) {
        // Ignore null objects, share an immutable empty list
        if (objectsToDelete == null || objectsToDelete.isEmpty() || isEmpty()) {
            return this;
        }

        // Create a temp array list with just non-null objects that belong to the new list
        final List<T> newDataArray = new ArrayList<>();
        for (final T datum : dataArray) {
            if (datum == null) {
                continue;
            }

            if (objectsToDelete.contains(datum)) {
                continue;
            }

            newDataArray.add(datum);
        }

        return new ImmutableIndexedObjectListUsingArray<>(newDataArray);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T getLast() {
        return isEmpty() ? null : dataArray[dataArray.length - 1];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get(final int objectIndex) {
        if (objectIndex < 0) {
            throw new IndexOutOfBoundsException("Cannot use negative index " + objectIndex);
        }
        if (isEmpty()) {
            return null;
        }
        final int offsetIndex = objectIndex - firstIndexOffset;
        return (offsetIndex < 0 || offsetIndex >= dataArray.length) ? null : dataArray[offsetIndex];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<T> stream() {
        return Arrays.stream(dataArray).filter(Objects::nonNull);
    }

    @Override
    public String toString() {
        return prettyPrintedIndices();
    }

    private boolean isEmpty() {
        /* We null out the data array when constructing from an empty list */
        return dataArray == null;
    }

    /**
     * Get the number of items in this list
     */
    @Override
    public int size() {
        return dataArray.length;
    }
}
