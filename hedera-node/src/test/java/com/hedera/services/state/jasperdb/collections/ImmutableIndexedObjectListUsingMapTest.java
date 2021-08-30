package com.hedera.services.state.jasperdb.collections;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ImmutableIndexedObjectListUsingMapTest extends ImmutableIndexedObjectListUsingArrayTest {

    @Override
    public ImmutableIndexedObjectList<TestIndexedObject> factoryForReaderToTest(TestIndexedObject[] objects) {
        return new ImmutableIndexedObjectListUsingMap<>(objects);
    }

}
