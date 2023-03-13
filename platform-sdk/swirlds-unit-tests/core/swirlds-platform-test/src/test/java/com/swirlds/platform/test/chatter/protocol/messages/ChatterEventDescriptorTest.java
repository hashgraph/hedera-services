/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.protocol.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.io.SerializationUtils;
import com.swirlds.platform.chatter.protocol.messages.ChatterEventDescriptor;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ChatterEventDescriptorTest {
    @Test
    void testSerialization() throws IOException, ConstructableRegistryException {
        final ChatterEventDescriptor descriptor = new ChatterEventDescriptor(RandomUtils.randomHash(), 1, 123);
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(ChatterEventDescriptor.class, ChatterEventDescriptor::new));
        final ChatterEventDescriptor copy = SerializationUtils.serializeDeserialize(descriptor);
        assertEquals(descriptor, copy, "deserialized version should be the same");

        assertThrows(Exception.class, () -> new ChatterEventDescriptor(null, 0, 0), "we should not permit a null hash");
    }

    @Test
    void testEquals() {
        final ChatterEventDescriptor d1 = new ChatterEventDescriptor(RandomUtils.randomHash(), 1, 123);
        final ChatterEventDescriptor d2 = new ChatterEventDescriptor(RandomUtils.randomHash(), 2, 234);
        assertTrue(d1.equals(d1), "should be equal to itself");
        assertFalse(d1.equals(null), "should not be equal to null");
        assertFalse(d1.equals(new Object()), "should not be equal to a different class");
        assertFalse(d1.equals(d2), "should not be equal to a different instance");
    }
}
