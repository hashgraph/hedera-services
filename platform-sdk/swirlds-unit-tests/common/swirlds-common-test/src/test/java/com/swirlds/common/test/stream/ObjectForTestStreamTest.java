/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.test.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.test.io.SerializationUtils;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ObjectForTestStreamTest {
    private static final int PAYLOAD_SIZE = 4;
    private static final Instant TIMESTAMP = Instant.now();
    private static ObjectForTestStream object = new ObjectForTestStream(PAYLOAD_SIZE, TIMESTAMP);

    @Test
    void getTest() {
        assertEquals(TIMESTAMP, object.getTimestamp(), "timestamp should match expected");
    }

    @Test
    void toStringTest() {
        final String expectedString =
                String.format("ObjectForTestStream[payload size: %d, time: %s]", PAYLOAD_SIZE, TIMESTAMP);
        assertEquals(expectedString, object.toString(), "string should match expected");
    }

    @Test
    void serializeDeserializeTest() throws Exception {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.common.test.stream");
        ObjectForTestStream deserialized = SerializationUtils.serializeDeserialize(object);
        assertEquals(object, deserialized, "deserialized object should equal to original object");
    }
}
