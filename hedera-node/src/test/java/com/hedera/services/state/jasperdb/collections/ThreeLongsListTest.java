package com.hedera.services.state.jasperdb.collections;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ThreeLongsListTest {
    public static final ThreeLongsList longList = new ThreeLongsList();

    @Test
    @Order(1)
    public void createData() throws Exception {
        // create 10 million sample data
        for (int i = 0; i < 100_000; i++) {
            longList.add(i, i*2L,i*3L);
        }
    }

    @Test
    @Order(2)
    public void checkGet() {
        assertEquals(100_000, longList.size());
        // check all data
        for (int i = 0; i < 100_000; i++) {
            long[] readValue = longList.get(i);
            assertEquals(3,readValue.length);
            assertEquals(i,readValue[0]);
            assertEquals(i*2L,readValue[1]);
            assertEquals(i*3L,readValue[2]);
        }
    }

    int count = 0;

    @Test
    @Order(3)
    public void checkForEach() {
        // check all data
        count = 0;
        longList.forEach((l1,l2,l3) -> {
            assertEquals(count,l1);
            assertEquals(count*2L,l2);
            assertEquals(count*3L,l3);
            count ++;
        });
    }

}
