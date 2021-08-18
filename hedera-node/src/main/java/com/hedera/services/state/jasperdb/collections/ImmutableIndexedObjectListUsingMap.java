package com.hedera.services.state.jasperdb.collections;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of ImmutableIndexedObjectList using a java util TreeMap. Not the most efficient but should be
 * correct.
 *
 * @param <T> the type of the IndexedObject we are collecting
 */
public class ImmutableIndexedObjectListUsingMap<T extends IndexedObject> extends ImmutableIndexedObjectList<T> {

    /** Array of data objects, index in array is offset by firstObjectIndex */
    private final SortedMap<Integer, T> dataMap;

    /**
     * Create a new ImmutableIndexedObjectList from existing set of objects.
     */
    public ImmutableIndexedObjectListUsingMap(T[] objects) {
        super(null);
        SortedMap<Integer, T> map = new TreeMap<>();
        for(var object: objects) {
            if (object != null) map.put(object.getIndex(),object);
        }
        dataMap = Collections.unmodifiableSortedMap(map);
    }

    /**
     * Create a new ImmutableIndexedObjectList from existing list of objects.
     */
    public ImmutableIndexedObjectListUsingMap(List<T> objects) {
        super(null);
        SortedMap<Integer, T> map = new TreeMap<>();
        for(var object: objects) {
            if (object != null) map.put(object.getIndex(),object);
        }
        dataMap = Collections.unmodifiableSortedMap(map);
    }

    /**
     * Construct a new ImmutableIndexedObjectList, this is private so use one of the static factory methods.
     *
     * @param dataMap map of objects
     */
    private ImmutableIndexedObjectListUsingMap(SortedMap<Integer, T> dataMap) {
        super(null);
        this.dataMap = dataMap;
    }

    /**
     * Create a new ImmutableIndexedObjectList containing the all existing objects with newT added it
     * its index. If there was already an object at that index then it will be replaced.
     */
    @Override
    public ImmutableIndexedObjectListUsingMap<T> withAddedObject(T newT) {
        SortedMap<Integer, T> map = new TreeMap<>(dataMap);
        map.put(newT.getIndex(),newT);
        return new ImmutableIndexedObjectListUsingMap<>(Collections.unmodifiableSortedMap(map));
    }


    /**
     * Create a new ImmutableIndexedObjectList with all existing objects that
     * are not in objectsToDelete.
     *
     * @param objectsToDelete non-null list of objects to delete
     * @return null if oldImmutableIndexedObjectList is null otherwise a new ImmutableIndexedObjectList with 
     *          just remaining objects
     */
    public ImmutableIndexedObjectListUsingMap<T> withDeletingObjects(List<T> objectsToDelete) {
        if (dataMap.isEmpty()) return this; // we are already empty
        SortedMap<Integer, T> map = new TreeMap<>(dataMap);
        for(var object: objectsToDelete) if (object != null) map.remove(object.getIndex());
        System.err.println("ImmutableIndexedObjectListUsingMap.withDeletingObjects \n"+
                "objectsToDelete="+ Arrays.toString(objectsToDelete.toArray())+"\n"+
                "after=("+ map.values().stream().map(Objects::toString).collect(Collectors.joining(","))+")");
        new Exception().printStackTrace();
        return new ImmutableIndexedObjectListUsingMap<>(Collections.unmodifiableSortedMap(map));
    }

    /**
     * Get the last object index
     */
    @Override
    public int getLastObjectIndex() {
        return dataMap.firstKey();
    }

    /**
     * Get the last object
     */
    @Override
    public T getLast() {
        return dataMap.get(dataMap.lastKey());
    }

    /**
     * Get the object at given objectIndex.
     */
    @Override
    public T get(int objectIndex) {
        return dataMap.get(objectIndex);
    }

    /**
     * Get a stream containing all non-null objects. The indexes in the list will not be object index but will be 
     * sorted in order.
     */
    @Override
    public Stream<T> stream() {
        return dataMap.values().stream();
    }
}
