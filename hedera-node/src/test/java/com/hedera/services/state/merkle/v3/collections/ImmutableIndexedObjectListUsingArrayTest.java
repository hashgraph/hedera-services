package com.hedera.services.state.merkle.v3.collections;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ImmutableIndexedObjectListUsingArrayTest {


    public static ImmutableIndexedObjectList<TestIndexedObject> list;

    public ImmutableIndexedObjectList<TestIndexedObject> factoryForReaderToTest(TestIndexedObject[] objects) {
        return new ImmutableIndexedObjectListUsingArray<>(objects);
    }

    @Test
    @Order(1)
    public void createData0_100() {
        list = factoryForReaderToTest(
                IntStream.range(0,100)
                        .mapToObj(TestIndexedObject::new)
                        .toArray(TestIndexedObject[]::new));
        checkRange(0,99);
    }

    @Test
    @Order(2)
    public void add100() {
        list = list.withAddedObject(new TestIndexedObject(100));
        checkRange(0,100);
    }

    @Test
    @Order(3)
    public void removeSome() {
//        list = list.withDeletingObjects(List.of(new TestIndexedObject(10),new TestIndexedObject(20),new TestIndexedObject(30)));
        list = list.withDeletingObjects(List.of(list.get(10),list.get(20),list.get(30)));
        System.out.println("list = " + list);
        assertNull(list.get(10));
        assertNull(list.get(20));
        assertNull(list.get(30));
        IntStream.range(0,9).forEach(i -> assertEquals(i,list.get(i).getIndex(), "Checking by get, expected ["+i+"] but got ["+list.get(i).getIndex()+"]"));
        IntStream.range(11,19).forEach(i -> assertEquals(i,list.get(i).getIndex(), "Checking by get, expected ["+i+"] but got ["+list.get(i).getIndex()+"]"));
        IntStream.range(21,29).forEach(i -> assertEquals(i,list.get(i).getIndex(), "Checking by get, expected ["+i+"] but got ["+list.get(i).getIndex()+"]"));
        IntStream.range(31,100).forEach(i -> assertEquals(i,list.get(i).getIndex(), "Checking by get, expected ["+i+"] but got ["+list.get(i).getIndex()+"]"));
    }

    public static void checkRange(int min, int max) {
        max ++; // add one to make range exclusive from inclusive
        TestIndexedObject[] objects = list.stream().toArray(TestIndexedObject[]::new);
        for (int i = min; i < max; i++) {
            assertEquals(i,objects[i].getIndex(), "Checking by stream, expected ["+i+"] but got ["+objects[i].getIndex()+"]");
        }
        IntStream.range(0,100).forEach(i -> assertEquals(i,list.get(i).getIndex(), "Checking by get, expected ["+i+"] but got ["+list.get(i).getIndex()+"]"));
        System.out.println("list = " + list);
    }

    /** Testing implementation of IndexedObject */
    public static class TestIndexedObject implements IndexedObject {
        public final int index;

        public TestIndexedObject(int index) { this.index = index; }

        public int getIndex() { return index; }
    }
}
