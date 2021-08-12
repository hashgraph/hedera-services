package com.hedera.services.state.merkle.v3.collections;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * An abstract immutable index objects list. This is a simple list that stores objects that know their own index. Any
 * change to the data can be done with the withXXX methods that return a new immutable copy with that change.
 *
 * @param <T> the type of the IndexedObject we are collecting
 */
public abstract class ImmutableIndexedObjectList<T extends IndexedObject> {

    /**
     * Create a new ImmutableIndexedObjectList from existing set of objects. No-op constructor just to force API.
     */
    public ImmutableIndexedObjectList(T[] objects) {}

    /**
     * Create a new ImmutableIndexedObjectList containing the all existing objects with newT added it
     * its index. If there was already an object at that index then it will be replaced.
     */
    public abstract ImmutableIndexedObjectList<T> withAddedObject(T newT);


    /**
     * Create a new ImmutableIndexedObjectList with all existing objects that
     * are not in objectsToDelete.
     *
     * @param objectsToDelete non-null list of objects to delete
     * @return null if oldImmutableIndexedObjectList is null otherwise a new ImmutableIndexedObjectList with 
     *          just remaining objects
     */
    public abstract ImmutableIndexedObjectList<T> withDeletingObjects(List<T> objectsToDelete);

    /**
     * Get the last object index
     */
    public abstract int getLastObjectIndex();

    /**
     * Get the last object
     */
    public abstract T getLast();

    /**
     * Get the object at given objectIndex.
     */
    public abstract T get(int objectIndex);

    /**
     * Get a stream containing all non-null objects. The indexes in the list will not be object index but will be 
     * sorted in order.
     */
    public abstract Stream<T> stream();

    /**
     * Useful toString for debugging that prints array of indexes stored
     */
    @Override
    public String toString() {
        return Arrays.toString(stream().mapToInt(IndexedObject::getIndex).toArray());
    }
}
