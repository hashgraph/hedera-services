/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle;

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.merkle.MerkleNode;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import utils.TestUtils;

class ServicesStateNodeTest {

    @Test
    @DisplayName("Null string values are not allowed")
    void nullValueThrowsConstructor() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> new ServiceStateNode(null));
    }

    @Test
    @DisplayName("Long string values are not allowed")
    void longValueThrowsConstructor() {
        final var randomString = new String(TestUtils.randomBytes(129));
        assertThrows(IllegalArgumentException.class, () -> new ServiceStateNode(randomString));
    }

    @ParameterizedTest(name = "With string `{0}`")
    @ValueSource(strings = {"", " ", "\n", "\uD83D\uDE07", "Test", "Multiple Words"})
    @DisplayName(
            "Non-null string values are allowed if their length in bytes is less than the maximum")
    void nonNullStringValuesAreGood(String value) {
        final var node = new ServiceStateNode(value);
        assertEquals(value, node.getServiceName());
    }

    @Test
    @DisplayName("You cannot use a null value for the state key")
    void nullStateKeyThrows() {
        final var node = new ServiceStateNode("testService");

        //noinspection ConstantConditions
        assertThrows(
                NullPointerException.class, () -> node.put(null, Mockito.mock(MerkleNode.class)));
    }

    @Test
    @DisplayName("The state key cannot have too many bytes")
    void stateKeyIsExcessivelyLong() {
        final var node = new ServiceStateNode("testService");
        final var stateKey = new String(TestUtils.randomBytes(129));
        assertThrows(
                IllegalArgumentException.class,
                () -> node.put(stateKey, Mockito.mock(MerkleNode.class)));
    }

    @ParameterizedTest(name = "With string `{0}`")
    @ValueSource(strings = {"", "\uD83D\uDE08", "\n", "\r", "Fine except for punctuation!?"})
    @DisplayName(
            "The state key can only have ascii characters and must have at least one character")
    void stateKeyWithNonAsciiThrows(final String stateKey) {
        final var node = new ServiceStateNode("testService");
        assertThrows(
                IllegalArgumentException.class,
                () -> node.put(stateKey, Mockito.mock(MerkleNode.class)));
    }

    @ParameterizedTest(name = "With string `{0}`")
    @ValueSource(strings = {" ", "This is A Test", "Numbers are fine 123"})
    @DisplayName("State keys can have spaces and upper and lower case characters")
    void stateKeys(final String stateKey) {
        final var node = new ServiceStateNode("testService");
        final var merkle = Mockito.mock(MerkleNode.class);
        node.put(stateKey, merkle);
        assertSame(merkle, node.find(stateKey));
    }

    @Test
    @DisplayName("You cannot find anything if nothing was set!")
    void findReturnsNullIfItIsEmpty() {
        final var node = new ServiceStateNode("testService");
        assertNull(node.find("Not Present"));
    }

    @Test
    @DisplayName("If `find` cannot find the state key, null is returned")
    void findReturnsNullIfItIsNotThere() {
        final var node = new ServiceStateNode("testService");
        final var merkle = Mockito.mock(MerkleNode.class);
        node.put("The Key", merkle);
        assertNull(node.find("Not Present"));
    }

    @Test
    @DisplayName("It is possible to find a merkle node that has been set on the ServiceStateNode")
    void find() {
        final var node = new ServiceStateNode("testService");
        final var merkle1 = Mockito.mock(MerkleNode.class);
        final var merkle2 = Mockito.mock(MerkleNode.class);
        final var merkle3 = Mockito.mock(MerkleNode.class);
        final var merkle4 = Mockito.mock(MerkleNode.class);
        node.put("Key 1", merkle1);
        node.put("Key 2", merkle2);
        node.put("Key 3", merkle3);
        node.put("Key 4", merkle4);
        assertSame(merkle3, node.find("Key 3"));
        assertSame(merkle2, node.find("Key 2"));
        assertSame(merkle4, node.find("Key 4"));
        assertSame(merkle1, node.find("Key 1"));
    }

    @Test
    @DisplayName("Calling `remove` on an empty ServiceStateNode is harmless")
    void removeOnEmptyIsNoop() {
        final var node = new ServiceStateNode("testService");
        node.remove("Not Present");
    }

    @Test
    @DisplayName("Calling `remove` removes the right key")
    void remove() {
        // Put a bunch of stuff into the node
        final var node = new ServiceStateNode("testService");
        final var map = new HashMap<String, MerkleNode>();
        for (int i = 0; i < 10; i++) {
            final var stateKey = "Key " + i;
            final var merkle = Mockito.mock(MerkleNode.class);
            map.put(stateKey, merkle);
            node.put(stateKey, merkle);
        }

        // Randomize the order in which they should be removed
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.shuffle(keys, TestUtils.random());

        // Remove the keys
        Set<String> removedKeys = new HashSet<>();
        for (final var key : keys) {
            removedKeys.add(key);
            map.remove(key);
            node.remove(key);

            for (final var entry : map.entrySet()) {
                assertSame(entry.getValue(), node.find(entry.getKey()));
            }

            for (final var removedKey : removedKeys) {
                assertNull(node.find(removedKey));
            }
        }
    }

    @Test
    @DisplayName("Cannot copy a node twice")
    void copiedNodeBecomesImmutable() {
        final var node = new ServiceStateNode("testService");
        node.copy();
        assertThrows(MutabilityException.class, node::copy);
    }

    @Test
    @DisplayName("Copies are the same")
    void copies() {
        final var node = new ServiceStateNode("testService");
        final var m1 = new StringLeaf("m1");
        final var m2 = new StringLeaf("m2");
        final var m3 = new StringLeaf("m3");
        final var m4 = new StringLeaf("m4");
        node.put("k1", m1);
        node.put("k2", m2);
        node.put("k3", m3);
        node.put("k4", m4);

        final var copy = node.copy();
        assertEquals(m1, copy.find("k1"));
        assertEquals(m2, copy.find("k2"));
        assertEquals(m3, copy.find("k3"));
        assertEquals(m4, copy.find("k4"));
    }

    @Test
    @DisplayName(
            "Copies are the same even when the original had some series of removals and additions")
    void copiesWithRemovals() {
        final var node = new ServiceStateNode("testService");
        final var m1 = new StringLeaf("m1");
        final var m2 = new StringLeaf("m2");
        final var m3 = new StringLeaf("m3");
        final var m4 = new StringLeaf("m4");
        node.put("k1", m1);
        node.put("k2", m2);
        node.put("k3", m3);
        node.put("k4", m4);
        node.remove("k2");

        final var copy = node.copy();
        assertEquals(m1, copy.find("k1"));
        assertNull(copy.find("k2"));
        assertEquals(m3, copy.find("k3"));
        assertEquals(m4, copy.find("k4"));
    }

    @Test
    @DisplayName("A bug in the platform may mean there is no service name")
    void noServiceNameShouldNotHappen() {
        // NOTE: Remove this test once we can remove the default constructor
        //noinspection deprecation
        final var node = new ServiceStateNode();
        assertThrows(IllegalStateException.class, node::getServiceName);
    }
}
