package com.hedera.services.state.jasperdb.collections;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LongListOffHeapTest extends LongListHeapTest {

    @Override
    protected LongList createLongList() {
        return new LongListOffHeap();
    }
}
