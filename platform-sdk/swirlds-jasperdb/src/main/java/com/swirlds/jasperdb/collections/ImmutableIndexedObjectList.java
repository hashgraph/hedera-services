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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An immutable list of indexed objects, containing at most one object at any given index.
 *
 * The {@link ImmutableIndexedObjectList#withAddedObject(IndexedObject)} and
 * {@link ImmutableIndexedObjectList#withDeletedObjects(Set)}} methods return shallow copies
 * of the list that result from applying the requested addition or deletion(s), leaving the
 * receiving list unchanged.
 *
 * @param <T>
 * 		the type of IndexedObject in the list
 */
@SuppressWarnings("unused")
public interface ImmutableIndexedObjectList<T extends IndexedObject> {
    /**
     * Creates a new ImmutableIndexedObjectList with the union of the existing objects
     * and the given new object, minus any existing object at the new object's index.
     *
     * Returns this list if given a null object.
     *
     * @param newT
     * 		a new indexed object
     * @return an immutable copy of this list plus newT, minus any existing element at newT's index
     */
    ImmutableIndexedObjectList<T> withAddedObject(T newT);

    /**
     * Creates a new ImmutableIndexedObjectList with the existing objects, minus any
     * at indexes in a list of objects (indexes) to delete.
     *
     * @param objectsToDelete
     * 		a non-null list of objects to delete
     * @return an immutable copy of this list minus any existing elements at indices from the deletion list
     */
    ImmutableIndexedObjectList<T> withDeletedObjects(Set<T> objectsToDelete);

    /**
     * Gets the last object in this list.
     *
     * @return the object in this list with the greatest index, or null if the list is empty
     */
    T getLast();

    /**
     * Gets the object at given index.
     *
     * @return the unique object at the given index, null if this list has no such object
     * @throws IndexOutOfBoundsException
     * 		if the index is negative
     */
    T get(final int objectIndex);

    /**
     * Gets a stream containing all non-null objects in this list, sorted in ascending order
     * of their self-reported index. (Note that an object's index need not be its position in
     * the returned stream; for example, the index of the first object in the stream
     * could be 7 and not 0.)
     *
     * @return a stream of this list's objects, sorted by self-reported index
     */
    Stream<T> stream();

    /**
     * Useful debugging helper that prints just the indices of the objects in this list.
     *
     * @return a pretty-printed list of the self-reported indices in the list
     */
    default String prettyPrintedIndices() {
        return Arrays.toString(stream().mapToInt(IndexedObject::getIndex).toArray());
    }

    /**
     * Get the number of items in this list
     */
    int size();
}
