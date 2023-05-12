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

package com.swirlds.merkle.map.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.dummy.Key;
import com.swirlds.common.test.io.InputOutputStream;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Key Tests")
class KeyTests {

    private static final Random RANDOM = new Random();

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();

        registry.registerConstructables("com.swirlds.merkle.map");

        // FUTURE WORK this is a hack
        // It is required for when this test is run via an intellij configuration
        registry.registerConstructable(new ClassConstructorPair(Key.class, Key::new));
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MMAP)
    @DisplayName("Equals Compare To Null")
    void equalsCompareToNull() {
        final Key key = new Key(new long[] {1L, 2L, 3L});
        final Key nullKey = null;
        assertNotEquals(nullKey, key, "key should not equal null");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MMAP)
    @DisplayName("Serialize And Deserialize Test")
    void serializeAndDeserializeTest() throws IOException {
        final long shardId = RANDOM.nextLong();
        final long realmId = RANDOM.nextLong();
        final long accountId = RANDOM.nextLong();

        final Key key = new Key(shardId, realmId, accountId);

        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(key, true);
            io.startReading();

            final Key deserializedKey = io.getInput().readSerializable();
            assertEquals(key, deserializedKey, "deserialized key should match");
        }
    }
}
