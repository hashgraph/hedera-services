package com.hedera.services.state.jasperdb.collections;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OffHeapLongListTest {
    public static final OffHeapLongList longList = new OffHeapLongList();

    @Test
    @Order(1)
    public void createData() throws Exception {
        // create 10 million sample data
        for (int i = 0; i < 10_000_000; i++) {
            longList.put(i, i);
        }
    }

    @Test
    @Order(2)
    public void check() {
        // check all data
        checkRange(0,10_000_000,false);
    }

    @Test
    @Order(3)
    public void testOffEndExpand() throws Exception {
        longList.put(13_000_123, 13_000_123);
        assertEquals(13_000_123,longList.get(13_000_123, 0),"Failed to save and get 13_000_123");
    }

    @Test
    @Order(4)
    public void testPutIfEqual() throws Exception {
        longList.put(13_000_123, 13_000_123);
        longList.putIfEqual(13_000_123, 0,42);
        assertNotEquals(42,longList.get(13_000_123, 0),"putIfEqual put when it should have not");
        longList.putIfEqual(13_000_123, 1,42);
        assertNotEquals(42,longList.get(13_000_123, 0),"putIfEqual put when it should have not");
        longList.putIfEqual(13_000_123, 13_000_122,42);
        assertNotEquals(42,longList.get(13_000_123, 0),"putIfEqual put when it should have not");
        longList.putIfEqual(13_000_123, 13_000_123,42);
        assertEquals(42,longList.get(13_000_123, 0),"putIfEqual did not put when it should have");
    }

    @Test
    @Order(5)
    public void testClear() throws Exception {
        // check the first 10 million are correct
        checkRange(0,10_000_000,false);
        printIndexes(999_999,1_000_000,1_000_001,1_000_999,1_001_000,1_001_001);
        // now clear the 1000 at offset 1 million, should be in a single memory chunk
        longList.clear(1_000_000,1000);
        // now check the data
        printIndexes(999_999,1_000_000,1_000_001,1_000_999,1_001_000,1_001_001);
        checkRange(0,1_000_000,false);
        checkRange(1_000_000,1_001_000,true);
        checkRange(1_001_000,10_000_000,false);
    }

    private void printIndexes(long ... indexes) {
        for(long index: indexes) {
            System.out.printf("%,d = %,d \n", index, longList.get(index,-1));
        }
    }

    private void checkRange(int start, int endExclusive, boolean checkZero) {
        for (int i = start; i < endExclusive; i++) {
            long readValue = longList.get(i, 0);
            if (checkZero) {
                assertEquals(0, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be ZERO");
            } else {
                assertEquals(i, readValue, "Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
            }
        }
    }
}
