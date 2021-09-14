package com.hedera.services.state.jasperdb.collections;

public class HashListHeapArrayTest extends HashListHeapTest {

    @Override
    protected HashList createHashList() {
        return new HashListHeapArrays();
    }
}
