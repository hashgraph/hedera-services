package com.hedera.services.state.merkle.v3.offheap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OffHeapLongListTest {

    @Test
    public void createDataAndCheck() {
        try {
            OffHeapLongList longList = new OffHeapLongList();
            // create 10 million sample data
            for (int i = 0; i < 10_000_000; i++) {
                longList.put(i, i);
            }
            // check all data
            for (int i = 0; i < 3_000_000; i++) {
                long readValue = longList.get(i, 0);
                assertEquals(i,readValue,"Longs don't match for " + i + " got [" + readValue + "] should be [" + i + "]");
            }
            // test off end
            longList.put(13_000_123, 13_000_123);
            assertEquals(13_000_123,longList.get(13_000_123, 0),"Failed to save and get 13_000_123");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
