package com.hedera.services.state.jasperdb.collections;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ImmutableIndexedObjectListUsingMapTest extends ImmutableIndexedObjectListUsingArrayTest {

    @Override
    public ImmutableIndexedObjectList<TestIndexedObject> factoryForReaderToTest(TestIndexedObject[] objects) {
        return new ImmutableIndexedObjectListUsingMap<>(objects);
    }

}
