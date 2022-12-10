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
package com.hedera.node.app.state.merkle.memory;

import com.hedera.node.app.spi.state.WritableStateBase;

/**
 * Tests for the {@link InMemoryWritableState}. This test class doesn't cover every permutation of
 * the full public API because tests for {@link WritableStateBase} cover everything that doesn't
 * eventually get committed to the underlying data store. So what we really need to test here are
 * unique API and implementations of {@link InMemoryWritableState}.
 */
class InMemoryStateTest {
    //    private static final String STATE_KEY = "TEST_STATE_KEY";
    //    private static final long CLASS_ID = 1234L;
    //    private static final String A_KEY = "A";
    //    private static final String B_KEY = "B";
    //    private static final String C_KEY = "C";
    //    private static final String D_KEY = "D";
    //    private static final String E_KEY = "E";
    //    private static final String A_VALUE = "Apple";
    //    private static final String B_VALUE = "Banana";
    //    private static final String C_VALUE = "Cherry";
    //    private static final String D_VALUE = "Date";
    //    private static final String E_VALUE = "Eggplant";
    //    private MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> merkleMap;
    //
    //    @BeforeEach
    //    protected void setUp() {
    //        merkleMap = new MerkleMap<>();
    //    }
    //
    //    private InMemoryState<String, String> createState(
    //            MerkleMap<InMemoryKey<String>, InMemoryValue<String, String>> map) {
    //        return new InMemoryState<>(
    //                STATE_KEY,
    //                map,
    //                CLASS_ID,
    //                MerkleTestBase::parseString,
    //                MerkleTestBase::parseString,
    //                MerkleTestBase::writeString,
    //                MerkleTestBase::writeString);
    //    }
    //
    //    private boolean merkleMapContainsKey(String key) {
    //        return merkleMap.containsKey(new InMemoryKey<>(key));
    //    }
    //
    //    private String readValueFromMerkleMap(String key) {
    //        final var val = merkleMap.get(new InMemoryKey<>(key));
    //        return val == null ? null : val.getValue();
    //    }
    //
    //    @Nested
    //    @DisplayName("Constructor Tests")
    //    final class ConstructorTest {
    //        @Test
    //        @DisplayName("Constructor requires a non-null state key")
    //        void nullStateKeyThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new InMemoryState<>(
    //                                    null,
    //                                    merkleMap,
    //                                    CLASS_ID,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeString,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null merkle map")
    //        void nullMapThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new InMemoryState<>(
    //                                    "KEY",
    //                                    null,
    //                                    CLASS_ID,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeString,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null key serializer")
    //        void nullKeySerializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new InMemoryState<>(
    //                                    "KEY",
    //                                    merkleMap,
    //                                    CLASS_ID,
    //                                    null,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeString,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null value serializer")
    //        void nullValueSerializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new InMemoryState<>(
    //                                    "KEY",
    //                                    merkleMap,
    //                                    CLASS_ID,
    //                                    MerkleTestBase::parseString,
    //                                    null,
    //                                    MerkleTestBase::writeString,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null key deserializer")
    //        void nullKeyDeserializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new InMemoryState<>(
    //                                    "KEY",
    //                                    merkleMap,
    //                                    CLASS_ID,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::parseString,
    //                                    null,
    //                                    MerkleTestBase::writeString));
    //        }
    //
    //        @Test
    //        @DisplayName("Constructor requires a non-null value deserializer")
    //        void nullValueDeserializerThrows() {
    //            //noinspection ConstantConditions
    //            assertThrows(
    //                    NullPointerException.class,
    //                    () ->
    //                            new InMemoryState<>(
    //                                    "KEY",
    //                                    merkleMap,
    //                                    CLASS_ID,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::parseString,
    //                                    MerkleTestBase::writeString,
    //                                    null));
    //        }
    //
    //        // TODO Test that setting classId finds its way to created
    //        @Test
    //        @DisplayName("ClassID is set on each created InMemoryValue")
    //        void classId() {
    //            final var state = createState(merkleMap);
    //            state.put(A_KEY, A_VALUE);
    //            state.commit();
    //            final var leaf = merkleMap.get(new InMemoryKey<>(A_KEY));
    //            assertEquals(CLASS_ID, leaf.getClassId());
    //        }
    //    }
    //
    //    /** Various tests for mutations to the store */
    //    @Nested
    //    @DisplayName("Mutations")
    //    final class MutationsTest {
    //
    //        /** Before each test, pre-configure the merkle map to have these built-in values. */
    //        @BeforeEach
    //        void setUp() {
    //            final var state = createState(merkleMap);
    //            state.put(A_KEY, A_VALUE);
    //            state.put(B_KEY, B_VALUE);
    //            state.put(C_KEY, C_VALUE);
    //            state.put(D_KEY, D_VALUE);
    //            state.commit();
    //
    //            // Now make a copy, so we are working on a new fast copy and not the original.
    //            // Probably doesn't make a difference, but it seems like a good thing to simulate.
    //            merkleMap = merkleMap.copy();
    //
    //            // And to be safe, let's make sure the elements are all there
    //            assertEquals(A_VALUE, readValueFromMerkleMap(A_KEY));
    //            assertEquals(B_VALUE, readValueFromMerkleMap(B_KEY));
    //            assertEquals(C_VALUE, readValueFromMerkleMap(C_KEY));
    //            assertEquals(D_VALUE, readValueFromMerkleMap(D_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, put a new value into it, and then commit it. The new value MUST
    // not
    //         * be present in the merkle tree until after commit.
    //         */
    //        @Test
    //        @DisplayName("Put a new entry and commit it")
    //        void putAndCommit() {
    //            // Make sure this key is NOT in the merkle map
    //            assertFalse(merkleMapContainsKey(E_KEY));
    //
    //            // Create a state and put the value
    //            final var state = createState(merkleMap);
    //            state.put(E_KEY, E_VALUE);
    //
    //            // Verify it is STILL not in the merkle map
    //            assertFalse(merkleMapContainsKey(E_KEY));
    //
    //            // Commit it and verify that it IS now in the merkle map
    //            state.commit();
    //            assertEquals(E_VALUE, readValueFromMerkleMap(E_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, put a new value into it, and then commit it. The new value MUST
    // not
    //         * be present in the merkle tree until after commit.
    //         */
    //        @Test
    //        @DisplayName("Put a new entry and roll it back")
    //        void putAndReset() {
    //            // Make sure this key is NOT in the merkle map
    //            assertFalse(merkleMapContainsKey(E_KEY));
    //
    //            // Create a state and put the value
    //            final var state = createState(merkleMap);
    //            state.put(E_KEY, E_VALUE);
    //
    //            // Verify it is STILL not in the merkle map
    //            assertFalse(merkleMapContainsKey(E_KEY));
    //
    //            // Reset the state and verify it is STILL not in the map
    //            state.reset();
    //            assertFalse(merkleMapContainsKey(E_KEY));
    //
    //            // Now commit it, and verify it is STILL not in the map because the
    //            // modification was lost after reset
    //            state.commit();
    //            assertFalse(merkleMapContainsKey(E_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, remove an existing value from it, and then commit it. The
    // removed
    //         * value MUST be present in the merkle tree until after commit.
    //         */
    //        @Test
    //        @DisplayName("Remove an entry and commit it")
    //        void removeAndCommit() {
    //            // Make sure this key IS in the merkle map
    //            assertTrue(merkleMapContainsKey(A_KEY));
    //
    //            // Create a state and remove the value
    //            final var state = createState(merkleMap);
    //            state.remove(A_KEY);
    //
    //            // Verify it is STILL in the merkle map
    //            assertTrue(merkleMapContainsKey(A_KEY));
    //
    //            // Commit it and verify that it is now NOT in the merkle map
    //            state.commit();
    //            assertFalse(merkleMapContainsKey(A_KEY));
    //        }
    //
    //        /**
    //         * Create a new state, remove an existing value from it, and then roll back. The
    // removed
    //         * value MUST never be removed from the merkle tree.
    //         */
    //        @Test
    //        @DisplayName("Remove an entry and roll back the change")
    //        void removeAndRollback() {
    //            // Make sure this key IS in the merkle map
    //            assertTrue(merkleMapContainsKey(B_KEY));
    //
    //            // Create a state and remove the value
    //            final var state = createState(merkleMap);
    //            state.remove(B_KEY);
    //
    //            // Verify it is STILL in the merkle map
    //            assertTrue(merkleMapContainsKey(B_KEY));
    //
    //            // Roll back and confirm it is STILL in the map
    //            state.reset();
    //            assertTrue(merkleMapContainsKey(B_KEY));
    //
    //            // Commit it and verify that it is STILL in the map since the change was rolled
    // back
    //            state.commit();
    //            assertTrue(merkleMapContainsKey(B_KEY));
    //        }
    //
    //        /**
    //         * A variety of modifications over many fast-copies, including rolled-back
    // modifications,
    //         * with verification that the merkle map has all the right values at each stage in the
    //         * process.
    //         */
    //        @Test
    //        @DisplayName("The Smörgåsbord of modifications, rollbacks, commits, and fast copies")
    //        void smorgasbord() {
    //            // Let's read with get and getForModify, remove something, put a modification, and
    // put
    //            // something new.
    //            var state = createState(merkleMap);
    //            assertEquals(A_VALUE, state.get(A_KEY).orElse(null));
    //            assertEquals(B_VALUE, state.getForModify(B_KEY).orElse(null));
    //            state.put(C_KEY, "Cat");
    //            state.remove(D_KEY);
    //            state.put(E_KEY, E_VALUE);
    //            state.commit();
    //
    //            // The merkle state should now be:
    //            assertEquals(A_VALUE, readValueFromMerkleMap(A_KEY));
    //            assertEquals(B_VALUE, readValueFromMerkleMap(B_KEY));
    //            assertEquals("Cat", readValueFromMerkleMap(C_KEY));
    //            assertFalse(merkleMapContainsKey(D_KEY));
    //            assertEquals(E_VALUE, readValueFromMerkleMap(E_KEY));
    //
    //            // Now let's make a fast copy and create a new state and make some more
    // modifications
    //            // and reads.
    //            // And then let's throw them all away and make sure the merkle map hasn't changed.
    //            merkleMap = merkleMap.copy();
    //            state = createState(merkleMap);
    //            assertEquals(A_VALUE, state.get(A_KEY).orElse(null));
    //            state.remove(B_KEY);
    //            assertEquals("Cat", state.getForModify(C_KEY).orElse(null));
    //            state.put(D_KEY, "Dog");
    //            state.put(E_KEY, "Elephant");
    //            state.reset();
    //
    //            // The merkle state should still be:
    //            assertEquals(A_VALUE, readValueFromMerkleMap(A_KEY));
    //            assertEquals(B_VALUE, readValueFromMerkleMap(B_KEY));
    //            assertEquals("Cat", readValueFromMerkleMap(C_KEY));
    //            assertFalse(merkleMapContainsKey(D_KEY));
    //            assertEquals(E_VALUE, readValueFromMerkleMap(E_KEY));
    //
    //            // Now reuse the same state, make some modifications, and commit them.
    //            state.put(A_KEY, "Aardvark");
    //            state.remove(B_KEY);
    //            state.remove(C_KEY);
    //            state.put(D_KEY, D_VALUE);
    //            state.put(E_KEY, "Elephant");
    //            state.commit();
    //
    //            // The merkle state should now be:
    //            assertEquals("Aardvark", readValueFromMerkleMap(A_KEY));
    //            assertFalse(merkleMapContainsKey(B_KEY));
    //            assertFalse(merkleMapContainsKey(C_KEY));
    //            assertEquals(D_VALUE, readValueFromMerkleMap(D_KEY));
    //            assertEquals("Elephant", readValueFromMerkleMap(E_KEY));
    //        }
    //    }
    //
    //    /** Various tests for serialization and deserialization */
    //    @Nested
    //    @DisplayName("Serdes")
    //    final class SerdesTest extends MerkleSerdesTestBase {
    //        private void initState() {
    //            final var state = createState(merkleMap);
    //            state.put(A_KEY, A_VALUE);
    //            state.put(B_KEY, B_VALUE);
    //            state.put(C_KEY, C_VALUE);
    //            state.put(D_KEY, D_VALUE);
    //            state.commit();
    //        }
    //
    //        /** Tests for serialization and deserialization */
    //        @Test
    //        @DisplayName("Serialization and deserialization")
    //        void serializesAndDeserializes(@TempDir Path tempDir)
    //                throws IOException, ConstructableRegistryException {
    //            initState();
    //            final var state = writeTree(merkleMap, tempDir);
    //
    //            // Register this so during deserialization we can get instance of
    //            // InMemoryValue created
    //            ConstructableRegistry.getInstance()
    //                    .registerConstructable(
    //                            new ClassConstructorPair(
    //                                    InMemoryValue.class,
    //                                    () ->
    //                                            new InMemoryValue<>(
    //                                                    CLASS_ID,
    //                                                    MerkleTestBase::parseString,
    //                                                    MerkleTestBase::parseString,
    //                                                    MerkleTestBase::writeString,
    //                                                    MerkleTestBase::writeString)));
    //
    //            merkleMap = parseTree(state, tempDir);
    //            assertEquals(A_VALUE, readValueFromMerkleMap(A_KEY));
    //            assertEquals(B_VALUE, readValueFromMerkleMap(B_KEY));
    //            assertEquals(C_VALUE, readValueFromMerkleMap(C_KEY));
    //            assertEquals(D_VALUE, readValueFromMerkleMap(D_KEY));
    //        }
    //
    //        /** Tests for a bad serializer that fails to write the key to the stream */
    //        @Test
    //        @DisplayName("Bad keyWriter fails to write the key")
    //        void badKeyWriter(@TempDir Path tempDir)
    //                throws IOException, ConstructableRegistryException {
    //            initState();
    //            final var state = writeTree(merkleMap, tempDir);
    //
    //            // Register this so during deserialization we can get instance of
    //            // InMemoryValue created
    //            ConstructableRegistry.getInstance()
    //                    .registerConstructable(
    //                            new ClassConstructorPair(
    //                                    InMemoryValue.class,
    //                                    () ->
    //                                            new InMemoryValue<>(
    //                                                    CLASS_ID,
    //                                                    (in, ver) ->
    //                                                            null, // Bad key parser returns
    // null
    //                                                    MerkleTestBase::parseString,
    //                                                    MerkleTestBase::writeString,
    //                                                    MerkleTestBase::writeString)));
    //
    //            assertThrows(IllegalStateException.class, () -> parseTree(state, tempDir));
    //        }
    //    }
}
