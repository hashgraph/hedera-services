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
public class ImmutableIndexedObjectListUsingMapTest extends ImmutableIndexedObjectListUsingArrayTest {

    @Override
    public ImmutableIndexedObjectList<TestIndexedObject> factoryForReaderToTest(TestIndexedObject[] objects) {
        return new ImmutableIndexedObjectListUsingMap<>(objects);
    }

}
