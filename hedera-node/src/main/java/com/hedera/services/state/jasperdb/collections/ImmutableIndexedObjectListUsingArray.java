package com.hedera.services.state.jasperdb.collections;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of ImmutableIndexedObjectList using a java heap array. The array starts at the first valid entry
 * and is sized to cover all indexes. The array index is the object's index so lookups are fast at the cost of having
 * empty array entries for any missing indexes.
 *
 * @param <T> the type of the IndexedObject we are collecting
 */
public class ImmutableIndexedObjectListUsingArray<T extends IndexedObject> extends ImmutableIndexedObjectList<T> {

    /** Index for the first object, this is basically the offset for the first object in dataMap */
    private final int firstIndexOffset;
    /** Array of data objects, index in array is offset by firstObjectIndex */
    private final T[] dataArray;

    /**
     * Create a new ImmutableIndexedObjectList from existing array of objects.
     */
    public ImmutableIndexedObjectListUsingArray(T[] objects) {
        this(Arrays.asList(objects));
    }

    /**
     * Create a new ImmutableIndexedObjectList from existing list of objects.
     */
    public ImmutableIndexedObjectListUsingArray(List<T> objects) {
        super(null);
        if (objects == null || objects.isEmpty()) {
            firstIndexOffset = 0;
            dataArray = null;
        } else {
            // sort the incoming data just in case it was not sorted
            objects.sort(Comparator.comparingInt(IndexedObject::getIndex));
            // now get first and last indexes
            firstIndexOffset = objects.get(0).getIndex();
            final int lastIndex = objects.get(objects.size()-1).getIndex();
            final int range = lastIndex - firstIndexOffset + 1;
            // create new array
            //noinspection unchecked
            dataArray = (T[]) Array.newInstance(objects.get(0).getClass(), range);
            for (var object : objects) {
                if (object != null) dataArray[object.getIndex()-firstIndexOffset] = object;
            }
        }
    }

    /**
     * Create a new ImmutableIndexedObjectList containing the all existing objects with newT added it
     * its index. If there was already an object at that index then it will be replaced.
     */
    @Override
    public ImmutableIndexedObjectListUsingArray<T> withAddedObject(T newObject) {
        // if we are not adding anything then we are the same
        if (newObject == null) return this;
        // create new temp array list for changes
        final ArrayList<T> newDataArray = new ArrayList<>(Arrays.asList(dataArray));
        // remove any existing object with same index
        newDataArray.removeIf(next -> next == null || next.getIndex() == newObject.getIndex());
        // add new object
        newDataArray.add(newObject);
        // create new ImmutableIndexedObjectListUsingArray
        return new ImmutableIndexedObjectListUsingArray<>(newDataArray);
    }


    /**
     * Create a new ImmutableIndexedObjectList with all existing objects that
     * are not in objectsToDelete.
     *
     * @param objectsToDelete non-null list of objects to delete, the object are compared by equals()
     * @return null if oldImmutableIndexedObjectList is null otherwise a new ImmutableIndexedObjectList with 
     *          just remaining objects
     */
    public ImmutableIndexedObjectListUsingArray<T> withDeletingObjects(List<T> objectsToDelete) {
        // if we are not removing  anything then we are the same
        if (objectsToDelete == null || objectsToDelete.isEmpty()) return this;
        // create new temp array list for changes
        final List<T> newDataArray = Arrays.stream(dataArray)
                .filter(Objects::nonNull)
                .filter( file -> ! objectsToDelete.contains(file))
                .collect(Collectors.toList());
        // create new ImmutableIndexedObjectListUsingArray
        return new ImmutableIndexedObjectListUsingArray<>(newDataArray);
    }

    /**
     * Get the last object index
     */
    @Override
    public int getLastObjectIndex() {
        return firstIndexOffset;
    }

    /**
     * Get the last object
     */
    @Override
    public T getLast() {
        return (dataArray == null || dataArray.length == 0) ? null : dataArray[dataArray.length-1];
    }

    /**
     * Get the object at given objectIndex.
     */
    @Override
    public T get(int objectIndex) {
        final int offsetIndex = objectIndex - firstIndexOffset;
        return (dataArray == null || dataArray.length <= offsetIndex) ? null : dataArray[offsetIndex];
    }

    /**
     * Get a stream containing all non-null objects. The indexes in the list will not be object index but will be 
     * sorted in order.
     */
    @Override
    public Stream<T> stream() {
        return Arrays.stream(dataArray).filter(Objects::nonNull);
    }
}
